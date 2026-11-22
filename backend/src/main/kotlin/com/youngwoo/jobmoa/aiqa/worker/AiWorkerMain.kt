package com.youngwoo.jobmoa.aiqa.worker

import com.youngwoo.jobmoa.career.assistant.domain.model.CareerAssistantTaskType
import org.slf4j.LoggerFactory
import tools.jackson.databind.JsonNode
import tools.jackson.databind.json.JsonMapper
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.nio.charset.StandardCharsets
import java.time.Duration
import java.time.Instant
import java.util.UUID
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledFuture
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference

fun main() {
    AiWorker().run()
}

private class AiWorker {
    private val log = LoggerFactory.getLogger(javaClass)
    private val mapper = JsonMapper()
    private val baseUrl = (System.getenv("AI_WORKER_API_URL") ?: "http://127.0.0.1:18080").trimEnd('/')
    private val workerId = System.getenv("AI_WORKER_ID")?.takeIf(String::isNotBlank)
        ?: "jobmoa-${System.getenv("HOSTNAME") ?: "mac"}-${UUID.randomUUID().toString().take(8)}"
    private val codex = CodexCli(findCodex(), mapper)
    private val client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build()
    private val active = AtomicReference<Lease?>(null)
    private val reporter = AtomicReference<ProgressReporter?>(null)
    private val scheduler = Executors.newSingleThreadScheduledExecutor { Thread(it, "jobmoa-ai-heartbeat").apply { isDaemon = true } }
    private val deltaScheduler = Executors.newSingleThreadScheduledExecutor { Thread(it, "jobmoa-ai-progress").apply { isDaemon = true } }

    fun run() {
        Runtime.getRuntime().addShutdownHook(Thread({
            codex.stopActive()
            scheduler.shutdownNow()
            deltaScheduler.shutdownNow()
        }, "jobmoa-ai-shutdown"))
        codex.verifyLogin()
        sendHeartbeat()
        scheduler.scheduleAtFixedRate({
            runCatching { sendHeartbeat() }.onFailure { log.warn("AI 워커 연결 갱신 실패: {}", it.message) }
            runCatching { active.get()?.let { lease -> renew(lease) } }.onFailure { log.warn("AI 작업 임대 갱신 실패: {}", it.message) }
            runCatching { reporter.get()?.flush() }.onFailure { log.warn("AI 답변 진행 상태 저장 실패: {}", it.message) }
        }, HEARTBEAT_SECONDS, HEARTBEAT_SECONDS, TimeUnit.SECONDS)
        log.info("JobMoa AI 워커 시작: model={}, endpoint={}", CodexCli.MODEL, baseUrl)

        var nextQueueIndex = 0
        while (!Thread.currentThread().isInterrupted) {
            try {
                val queues = WorkerQueue.entries
                var selected: Pair<WorkerQueue, JsonNode>? = null
                for (offset in queues.indices) {
                    val queue = queues[(nextQueueIndex + offset) % queues.size]
                    val task = claim(queue)
                    if (!task.isNull && !task.isMissingNode) {
                        selected = queue to task
                        break
                    }
                }
                if (selected == null) {
                    nextQueueIndex = (nextQueueIndex + 1) % queues.size
                    Thread.sleep(POLL_MILLIS)
                    continue
                }
                nextQueueIndex = (selected.first.ordinal + 1) % queues.size
                process(selected.second, selected.first)
            } catch (interrupted: InterruptedException) {
                Thread.currentThread().interrupt()
            } catch (error: Exception) {
                log.warn("AI 작업 대기 중 오류: {}", safeError(error.message.orEmpty()))
                Thread.sleep(RETRY_MILLIS)
            }
        }
        scheduler.shutdownNow()
        deltaScheduler.shutdownNow()
    }

    private fun claim(queue: WorkerQueue) = request("POST", when (queue) {
        WorkerQueue.CAREER_MATERIAL -> "/api/career-materials/worker/tasks/claim"
        WorkerQueue.CAREER_ASSISTANT -> "/api/career-assistant/worker/tasks/claim"
        WorkerQueue.AI_CHAT -> "/api/ai/worker/tasks/claim"
    }, "{}")

    private fun process(task: JsonNode, queue: WorkerQueue) {
        val taskId = task["taskId"].asString()
        val leaseToken = task["leaseToken"].asString()
        val lease = Lease(taskId, leaseToken, queue)
        val progress = if (queue == WorkerQueue.AI_CHAT) ProgressReporter(taskId, leaseToken) else null
        active.set(lease)
        reporter.set(progress)
        val flushJob: ScheduledFuture<*>? = progress?.let { reporter ->
            deltaScheduler.scheduleAtFixedRate(
                { runCatching { reporter.flush() }.onFailure { log.warn("AI 답변 중간 저장 실패: {}", it.message) } },
                DELTA_FLUSH_MILLIS, DELTA_FLUSH_MILLIS, TimeUnit.MILLISECONDS,
            )
        }
        try {
            when (queue) {
                WorkerQueue.CAREER_MATERIAL -> {
                    val result = codex.analyzeCareer(task)
                    request("POST", "/api/career-materials/worker/tasks/$taskId/complete", mapper.writeValueAsString(mapOf(
                        "leaseToken" to leaseToken,
                        "facts" to result.facts.map { fact -> mapOf(
                            "kind" to fact.kind.name,
                            "title" to fact.title,
                            "summary" to fact.summary,
                            "evidence" to fact.evidence.map { evidence -> mapOf("fragmentId" to evidence.fragmentId, "excerpt" to evidence.excerpt) },
                        ) },
                    )))
                    log.info("경력 자료 분석 완료: {} (facts={})", taskId, result.facts.size)
                }
                WorkerQueue.CAREER_ASSISTANT -> {
                    val taskType = CareerAssistantTaskType.valueOf(task["taskType"].asString())
                    val input = task["request"]
                    val result = when (taskType) {
                        CareerAssistantTaskType.RECOMMENDATIONS -> codex.recommendCareer(input)
                        CareerAssistantTaskType.POSTING_DRAFT -> codex.buildPostingDraft(input)
                    }
                    request("POST", "/api/career-assistant/worker/tasks/$taskId/complete", mapper.writeValueAsString(mapOf(
                        "leaseToken" to leaseToken,
                        "result" to mapper.readTree(mapper.writeValueAsString(result)),
                    )))
                    log.info("경력 맞춤 작업 완료: {} ({})", taskId, taskType)
                }
                WorkerQueue.AI_CHAT -> {
                val aiProgress = requireNotNull(progress)
                aiProgress.status("공고 자료를 확인하고 있어요")
                aiProgress.status("Codex가 답변을 작성하고 있어요")
                val result = codex.answer(task, aiProgress::search, aiProgress::delta)
                aiProgress.flush()
                val body = mapOf(
                    "leaseToken" to leaseToken,
                    "answer" to result.answer,
                    "postingCitationIds" to result.postingSources.map { it.first },
                    "webSources" to result.webSources.map { mapOf("title" to it.title, "url" to it.url, "reason" to it.reason) },
                    "searchPerformed" to result.searchPerformed,
                    "workspaceUsed" to result.workspaceUsed,
                )
                request("POST", "/api/ai/worker/tasks/$taskId/complete", mapper.writeValueAsString(body))
                log.info("AI 작업 완료: {} (webSearch={})", taskId, result.searchPerformed)
                }
            }
        } catch (error: Exception) {
            progress?.flushQuietly()
            val message = safeError(error.message ?: "Codex CLI가 작업을 완료하지 못했습니다.")
            runCatching {
                val endpoint = when (queue) {
                    WorkerQueue.CAREER_MATERIAL -> "/api/career-materials/worker/tasks/$taskId/fail"
                    WorkerQueue.CAREER_ASSISTANT -> "/api/career-assistant/worker/tasks/$taskId/fail"
                    WorkerQueue.AI_CHAT -> "/api/ai/worker/tasks/$taskId/fail"
                }
                request("POST", endpoint, mapper.writeValueAsString(mapOf(
                    "leaseToken" to leaseToken,
                    "message" to message,
                )))
            }.onFailure { log.warn("AI 작업 실패 기록 저장 불가: {}", it.message) }
            log.warn("AI 작업 실패: {} · {}", taskId, message)
        } finally {
            flushJob?.cancel(false)
            reporter.set(null)
            active.set(null)
        }
    }

    private fun sendHeartbeat() {
        request("POST", "/api/ai/worker/heartbeat", mapper.writeValueAsString(mapOf(
            "workerId" to workerId,
            "model" to CodexCli.MODEL,
        )))
    }

    private fun renew(lease: Lease) {
        val endpoint = when (lease.queue) {
            WorkerQueue.CAREER_MATERIAL -> "/api/career-materials/worker/tasks/${lease.taskId}/renew"
            WorkerQueue.CAREER_ASSISTANT -> "/api/career-assistant/worker/tasks/${lease.taskId}/renew"
            WorkerQueue.AI_CHAT -> "/api/ai/worker/tasks/${lease.taskId}/renew"
        }
        request("POST", endpoint, mapper.writeValueAsString(mapOf("leaseToken" to lease.token)))
    }

    private fun request(method: String, path: String, body: String): JsonNode {
        val request = HttpRequest.newBuilder(URI.create(baseUrl + path))
            .timeout(Duration.ofSeconds(30))
            .header("Content-Type", "application/json")
            .method(method, HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8))
            .build()
        val response = client.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8))
        if (response.statusCode() !in 200..299) {
            val details = runCatching { mapper.readTree(response.body())["message"]?.asString() }.getOrNull()
            throw IllegalStateException(details ?: "JobMoa API 응답 오류: ${response.statusCode()}")
        }
        return if (response.body().isBlank()) mapper.readTree("null") else mapper.readTree(response.body())
    }

    private fun findCodex(): String {
        val configured = System.getenv("CODEX_BIN")?.takeIf(String::isNotBlank)
        if (configured != null) return configured
        val path = System.getenv("PATH").orEmpty().split(System.getProperty("path.separator"))
        path.firstNotNullOfOrNull { directory ->
            val candidate = java.nio.file.Path.of(directory, "codex")
            candidate.takeIf { java.nio.file.Files.isExecutable(it) }?.toString()
        }?.let { return it }
        val common = listOf(
            "/Applications/ChatGPT.app/Contents/Resources/codex-cli/CodexCLI.app/Contents/MacOS/codex",
            "/Applications/Codex.app/Contents/Resources/codex",
        ).firstOrNull { java.nio.file.Files.isExecutable(java.nio.file.Path.of(it)) }
        return common ?: throw IllegalStateException("Codex CLI를 찾을 수 없습니다. CODEX_BIN에 실행 파일 경로를 지정하세요.")
    }

    private fun safeError(message: String) = message
        .replace(Regex("(?i)bearer\\s+[A-Za-z0-9._~-]+"), "Bearer [숨김]")
        .replace(Regex("sk-[A-Za-z0-9_-]{12,}"), "sk-[숨김]")
        .trim().take(1800)

    private inner class ProgressReporter(private val taskId: String, private val token: String) {
        private val pending = StringBuilder()
        private val lock = Any()
        private var lastFlushNanos = System.nanoTime()

        fun status(message: String) = emit("status", mapOf("message" to message))
        fun search(message: String) = emit("search", mapOf("message" to message))
        fun delta(text: String) {
            synchronized(lock) {
                pending.append(text)
                val now = System.nanoTime()
                if (pending.length >= DELTA_BATCH_CHARS || now - lastFlushNanos >= DELTA_FLUSH_NANOS) flushLocked(now)
            }
        }
        fun flush() = synchronized(lock) { flushLocked(System.nanoTime()) }
        fun flushQuietly() { runCatching { flush() } }

        private fun emit(type: String, payload: Map<String, String>) {
            request("POST", "/api/ai/worker/tasks/$taskId/events", mapper.writeValueAsString(mapOf(
                "leaseToken" to token,
                "type" to type,
                "payload" to payload,
            )))
        }

        private fun flushLocked(now: Long) {
            if (pending.isEmpty()) return
            val text = pending.toString()
            pending.setLength(0)
            lastFlushNanos = now
            emit("delta", mapOf("text" to text))
        }
    }

    private enum class WorkerQueue { CAREER_MATERIAL, CAREER_ASSISTANT, AI_CHAT }
    private data class Lease(val taskId: String, val token: String, val queue: WorkerQueue)

    companion object {
        private const val HEARTBEAT_SECONDS = 10L
        private const val POLL_MILLIS = 1200L
        private const val RETRY_MILLIS = 3000L
        private const val DELTA_BATCH_CHARS = 100
        private const val DELTA_FLUSH_NANOS = 120_000_000L
        private const val DELTA_FLUSH_MILLIS = 120L
    }
}
