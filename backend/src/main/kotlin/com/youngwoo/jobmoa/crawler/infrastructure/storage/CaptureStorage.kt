package com.youngwoo.jobmoa.crawler.infrastructure.storage

import com.youngwoo.jobmoa.crawler.domain.model.PostingUrl
import com.youngwoo.jobmoa.shared.CrawlerProperties
import com.youngwoo.jobmoa.shared.ResourceNotFound
import org.springframework.core.io.FileSystemResource
import org.springframework.stereotype.Component
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.security.MessageDigest

@Component
class CaptureStorage(properties: CrawlerProperties) {
    private val directory = properties.storageDirectory.toAbsolutePath().normalize().resolve("captures")
    fun save(bytes: ByteArray): String {
        Files.createDirectories(directory)
        val name = MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) } + ".png"
        val destination = directory.resolve(name)
        if (Files.exists(destination)) return name
        val temp = Files.createTempFile(directory, ".capture-", ".tmp")
        try {
            Files.write(temp, bytes)
            Files.move(temp, destination, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING)
        } finally { Files.deleteIfExists(temp) }
        return name
    }
    fun read(name: String): FileSystemResource {
        if (!Regex("[a-f0-9]{64}\\.png").matches(name)) throw ResourceNotFound("캡처를 찾을 수 없습니다.")
        val file = directory.resolve(name)
        if (!Files.isRegularFile(file)) throw ResourceNotFound("캡처를 찾을 수 없습니다.")
        return FileSystemResource(file)
    }
}
