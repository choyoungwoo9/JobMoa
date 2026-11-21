package com.youngwoo.jobmoa.aiqa.application

import com.youngwoo.jobmoa.aiqa.domain.model.AiTaskEvent
import com.youngwoo.jobmoa.aiqa.domain.repository.AiChatRepository
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter
import java.io.IOException
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CopyOnWriteArrayList

@Component
class AiTaskEventHub(private val repository: AiChatRepository) {
    private data class Channel(val lock: Any = Any(), val subscribers: CopyOnWriteArrayList<SseEmitter> = CopyOnWriteArrayList())
    private val channels = ConcurrentHashMap<String, Channel>()

    fun subscribe(taskId: String, afterId: Long, terminal: Boolean): SseEmitter {
        val channel = channels.computeIfAbsent(taskId) { Channel() }
        val emitter = SseEmitter(0L)
        val remove = { channel.subscribers.remove(emitter); Unit }
        emitter.onCompletion(remove)
        emitter.onTimeout { remove(); emitter.complete() }
        emitter.onError { remove() }
        synchronized(channel.lock) {
            channel.subscribers.add(emitter)
            repository.taskEventsAfter(taskId, afterId).forEach { event ->
                if (!send(channel, emitter, event)) return@forEach
            }
            if (terminal) {
                channel.subscribers.remove(emitter)
                emitter.complete()
            }
        }
        if (terminal) channels.remove(taskId, channel)
        return emitter
    }

    fun publish(event: AiTaskEvent) {
        val channel = channels[event.taskId] ?: return
        synchronized(channel.lock) {
            channel.subscribers.toList().forEach { send(channel, it, event) }
            if (event.type in setOf("complete", "failed")) {
                channel.subscribers.toList().forEach { it.complete() }
                channel.subscribers.clear()
            }
        }
        if (event.type in setOf("complete", "failed")) channels.remove(event.taskId, channel)
    }

    @Scheduled(fixedDelay = 15000)
    fun keepAlive() {
        channels.forEach { (taskId, channel) ->
            synchronized(channel.lock) {
                channel.subscribers.toList().forEach { emitter ->
                    try {
                        emitter.send(SseEmitter.event().comment("keepalive"))
                    } catch (_: IOException) {
                        channel.subscribers.remove(emitter)
                    }
                }
            }
        }
    }

    private fun send(channel: Channel, emitter: SseEmitter, event: AiTaskEvent): Boolean {
        try {
            emitter.send(SseEmitter.event().id(event.id.toString()).name(event.type).data(event.payloadJson))
            return true
        } catch (_: IOException) {
            // SSE 구독자는 작업 진행과 독립적이다. 끊긴 연결만 제거하고
            // 이미 DB에 커밋된 진행 이벤트 전파를 실패 처리하지 않는다.
            channel.subscribers.remove(emitter)
            return false
        }
    }
}
