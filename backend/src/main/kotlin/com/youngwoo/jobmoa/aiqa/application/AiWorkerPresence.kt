package com.youngwoo.jobmoa.aiqa.application

import com.youngwoo.jobmoa.aiqa.domain.model.AiWorkerStatus
import com.youngwoo.jobmoa.shared.ServiceUnavailable
import org.springframework.stereotype.Component
import java.time.Duration
import java.time.Instant
import java.util.concurrent.atomic.AtomicReference

@Component
class AiWorkerPresence {
    private val latest = AtomicReference<AiWorkerStatus?>(null)
    private val expiration = Duration.ofSeconds(45)

    fun heartbeat(workerId: String, model: String) {
        require(workerId.isNotBlank() && workerId.length <= 100) { "잘못된 AI 워커 ID입니다." }
        require(model == MODEL) { "JobMoa AI 모델은 $MODEL 로 고정되어 있습니다." }
        latest.set(AiWorkerStatus(true, workerId, MODEL, true, Instant.now()))
    }

    fun status(): AiWorkerStatus {
        val current = latest.get()
        val connected = current != null && current.lastSeenAt?.plus(expiration)?.isAfter(Instant.now()) == true
        return if (connected) current else AiWorkerStatus(false, model = MODEL, webSearch = true, lastSeenAt = current?.lastSeenAt)
    }

    fun requireConnected() {
        if (!status().connected) throw ServiceUnavailable("AI 워커가 연결되지 않았습니다. 로컬에서 ./ai-worker.sh start 로 시작하세요.")
    }

    companion object { const val MODEL = "gpt-6-luna" }
}
