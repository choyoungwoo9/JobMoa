package com.youngwoo.jobmoa.workspace.application

import com.youngwoo.jobmoa.workspace.domain.*
import com.youngwoo.jobmoa.shared.CrawlerProperties
import com.youngwoo.jobmoa.shared.ResourceNotFound
import org.springframework.stereotype.Service
import org.springframework.transaction.PlatformTransactionManager
import org.springframework.transaction.support.TransactionTemplate
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.web.multipart.MultipartFile
import tools.jackson.databind.json.JsonMapper
import java.nio.file.*
import java.security.MessageDigest
import java.time.Instant
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.zip.*

@Service
class WorkspaceService(private val repository: WorkspaceSnapshotRepository, private val access: WorkspaceAccess,
    properties: CrawlerProperties, private val mapper: JsonMapper, transactionManager: PlatformTransactionManager) {
    private val tx = TransactionTemplate(transactionManager)
    private val captures = properties.storageDirectory.resolve("captures").toAbsolutePath().normalize()
    private val temporary = properties.storageDirectory.resolve("workspace-tmp").toAbsolutePath().normalize()
    private val fileName = Regex("[a-f0-9]{64}\\.png")
    private val maxArchive = 2L * 1024 * 1024 * 1024
    private val maxExpanded = 4L * 1024 * 1024 * 1024
    private data class Preview(val directory: Path, val snapshot: WorkspaceSnapshot, val expires: Instant)
    private val previews = ConcurrentHashMap<String, Preview>()
    init { Files.createDirectories(captures); Files.createDirectories(temporary) }
    fun overview(): Map<String, Any> = tx.execute {
        val counts = repository.counts()
        val files = Files.list(captures).use { stream -> stream.filter { safeFile(it) }.toList() }
        counts + mapOf("captureFiles" to files.size, "captureBytes" to files.sumOf { Files.size(it) }, "backupVersion" to 1)
    }
    private fun safeFile(path: Path) = fileName.matches(path.fileName.toString()) && Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS)
    private fun checkedCapture(name: String): Path {
        require(fileName.matches(name)) { "잘못된 캡처 파일 이름입니다." }
        val path = captures.resolve(name)
        require(safeFile(path)) { "캡처 파일을 찾을 수 없습니다: $name" }
        return path
    }
    private fun hash(path: Path): String {
        val digest = MessageDigest.getInstance("SHA-256")
        Files.newInputStream(path).use { input -> val buffer = ByteArray(65536); while (true) { val n = input.read(buffer); if (n < 0) break; digest.update(buffer, 0, n) } }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }
    fun backup(): Path {
        val target = Files.createTempFile(temporary, "jobmoa-backup-", ".zip")
        try {
            tx.executeWithoutResult {
                access.maintenance()
                val names = repository.captureNames().sorted()
                val snapshot = WorkspaceSnapshot(exportedAt = Instant.now().toString(), tables = repository.exportTables(), captures = names)
                repository.validate(snapshot)
                ZipOutputStream(Files.newOutputStream(target)).use { zip ->
                    zip.putNextEntry(ZipEntry("workspace.json")); zip.write(mapper.writeValueAsBytes(snapshot)); zip.closeEntry()
                    names.forEach { name ->
                        val source = checkedCapture(name)
                        require(hash(source) + ".png" == name) { "캡처 파일의 무결성을 확인할 수 없습니다: $name" }
                        zip.putNextEntry(ZipEntry("captures/$name")); Files.copy(source, zip); zip.closeEntry()
                    }
                }
                require(Files.size(target) <= maxArchive) { "백업 파일은 2 GiB까지 지원합니다." }
            }
            return target
        } catch (e: Exception) { Files.deleteIfExists(target); throw e }
    }
    @Synchronized fun preview(file: MultipartFile): Map<String, Any> {
        cleanup()
        require(!file.isEmpty && file.size <= maxArchive) { "2 GiB 이하의 백업 ZIP을 선택하세요." }
        require(previews.size < 2) { "미리보기는 동시에 2개까지 가능합니다. 기존 미리보기를 취소하세요." }
        val id = UUID.randomUUID().toString()
        val directory = Files.createDirectory(temporary.resolve(id))
        try {
            var total = 0L
            val seen = mutableSetOf<String>()
            ZipInputStream(file.inputStream.buffered()).use { zip ->
                var entry = zip.nextEntry
                while (entry != null) {
                    val name = entry.name
                    require(!entry.isDirectory && (name == "workspace.json" || name.startsWith("captures/") && fileName.matches(name.removePrefix("captures/")))) { "허용하지 않는 ZIP 경로입니다: $name" }
                    require(seen.add(name) && seen.size <= 100_001) { "중복 파일 또는 너무 많은 파일이 포함되어 있습니다." }
                    val target = directory.resolve(name)
                    Files.createDirectories(target.parent)
                    var bytes = 0L
                    Files.newOutputStream(target, StandardOpenOption.CREATE_NEW).use { out ->
                        val buffer = ByteArray(65536)
                        while (true) {
                            val n = zip.read(buffer); if (n < 0) break
                            bytes += n; total += n
                            require(total <= maxExpanded && bytes <= 128L * 1024 * 1024) { "압축 해제 데이터가 허용 크기를 초과합니다." }
                            out.write(buffer, 0, n)
                        }
                    }
                    entry = zip.nextEntry
                }
            }
            require("workspace.json" in seen) { "JobMoa 백업 데이터가 없습니다." }
            val snapshot = parseSnapshot(directory.resolve("workspace.json"))
            tx.executeWithoutResult { repository.validate(snapshot) }
            require(seen == (snapshot.captures.map { "captures/$it" } + "workspace.json").toSet()) { "백업의 캡처 파일이 누락되었거나 목록과 다릅니다." }
            snapshot.captures.forEach { name -> require(fileName.matches(name) && hash(directory.resolve("captures/$name")) + ".png" == name) { "캡처 파일이 손상되었습니다: $name" } }
            val expires = Instant.now().plusSeconds(1800)
            previews[id] = Preview(directory, snapshot, expires)
            return mapOf("token" to id, "exportedAt" to snapshot.exportedAt, "expiresAt" to expires, "counts" to snapshotCounts(snapshot), "captureBytes" to snapshot.captures.sumOf { Files.size(directory.resolve("captures/$it")) })
        } catch (e: Exception) {
            removeDirectory(directory)
            if (e is IllegalArgumentException) throw e
            org.slf4j.LoggerFactory.getLogger(javaClass).warn("백업 미리보기 검증 실패", e)
            throw IllegalArgumentException("백업 파일을 읽을 수 없습니다. 형식과 압축 파일 손상을 확인하세요.", e)
        }
    }
    private fun parseSnapshot(path: Path): WorkspaceSnapshot {
        val root = mapper.readTree(Files.readString(path))
        require(root.isObject && root["tables"]?.isObject == true && root["captures"]?.isArray == true) { "백업 데이터 구조가 올바르지 않습니다." }
        fun strings(node: tools.jackson.databind.JsonNode): List<String> {
            require(node.isArray && node.all { it.isString }) { "백업의 문자열 목록이 잘못되었습니다." }
            return node.values().map { it.asString() }
        }
        val tables = root["tables"].properties().associate { (name, value) ->
            require(value.isObject && value["columns"]?.isArray == true && value["rows"]?.isArray == true) { "백업 테이블 구조가 잘못되었습니다." }
            val rows = value["rows"].values().map { row ->
                require(row.isArray) { "백업 행 형식이 잘못되었습니다." }
                row.values().map { cell -> when {
                    cell.isNull -> null
                    cell.isString -> cell.asString()
                    cell.isBoolean -> cell.asBoolean()
                    cell.isIntegralNumber -> cell.longValue()
                    cell.isNumber -> cell.decimalValue()
                    else -> throw IllegalArgumentException("백업 값 형식이 잘못되었습니다.")
                } }
            }
            name to SnapshotTable(strings(value["columns"]), rows)
        }
        return WorkspaceSnapshot(format = root["format"].asString(), version = root["version"].asInt(), schema = root["schema"].asInt(),
            exportedAt = root["exportedAt"].asString().also { Instant.parse(it) }, tables = tables, captures = strings(root["captures"]))
    }
    private fun snapshotCounts(s: WorkspaceSnapshot) = mapOf("postings" to s.tables.getValue("posting").rows.size,
        "versions" to s.tables.getValue("posting_version").rows.size, "runs" to s.tables.getValue("collection_run").rows.size,
        "management" to s.tables.getValue("posting_interest").rows.size, "presets" to s.tables.getValue("collection_preset").rows.size, "captureFiles" to s.captures.size)
    @Synchronized fun discard(token: String) { previews.remove(token)?.let { removeDirectory(it.directory) } }
    @Synchronized fun restore(token: String, confirmation: String): Map<String, Any> {
        require(confirmation == "복원") { "확인 문구 '복원'을 입력하세요." }
        val preview = previews[token]?.takeIf { it.expires.isAfter(Instant.now()) } ?: throw ResourceNotFound("미리보기가 만료되었습니다. 백업 파일을 다시 선택하세요.")
        try {
            tx.executeWithoutResult {
                access.maintenance()
                // 먼저 검증한 파일을 배치한다. DB 롤백 시 남는 파일은 미사용 파일일 뿐, 기존 참조를 손상시키지 않는다.
                preview.snapshot.captures.forEach { name ->
                    val source = preview.directory.resolve("captures/$name")
                    val target = captures.resolve(name)
                    if (Files.exists(target, LinkOption.NOFOLLOW_LINKS)) {
                        require(safeFile(target) && hash(target) + ".png" == name) { "기존 캡처 파일이 손상되었습니다: $name" }
                    } else Files.copy(source, target)
                }
                repository.replace(preview.snapshot)
            }
        } catch (e: org.springframework.dao.DataAccessException) { throw IllegalArgumentException("백업 데이터의 참조 또는 값이 올바르지 않습니다. 현재 데이터는 유지됩니다.", e) }
        discard(token)
        return mapOf("restored" to true, "cleanup" to cleanupAfterCommit())
    }
    fun reset(scope: String, confirmation: String): Map<String, Any> {
        require(confirmation == "초기화") { "확인 문구 '초기화'를 입력하세요." }
        tx.executeWithoutResult { access.maintenance(); repository.reset(scope) }
        return mapOf("reset" to scope, "cleanup" to if (scope == "postings") cleanupAfterCommit() else 0)
    }
    private fun cleanupAfterCommit(): Int = try { garbageCollect() } catch (e: Exception) {
        org.slf4j.LoggerFactory.getLogger(javaClass).warn("데이터 반영 후 미사용 캡처 정리는 보류했습니다.", e); -1
    }
    fun garbageCollect(): Int = tx.execute {
        access.maintenance()
        val referenced = repository.captureNames()
        Files.list(captures).use { stream -> stream.filter { safeFile(it) && it.fileName.toString() !in referenced }.toList().count { Files.deleteIfExists(it) } }
    }
    @Scheduled(fixedDelay = 60000) @Synchronized fun cleanup() {
        val now = Instant.now()
        previews.entries.filter { it.value.expires.isBefore(now) }.forEach { discard(it.key) }
        // 프로세스 재시작으로 메모리에서 잃은 임시 파일만 정리한다. 활성 미리보기/다운로드는 보존한다.
        Files.list(temporary).use { stream -> stream.filter { !previews.containsKey(it.fileName.toString()) && Files.getLastModifiedTime(it).toInstant().isBefore(now.minusSeconds(3600)) }.toList().forEach { removeDirectory(it) } }
    }
    private fun removeDirectory(path: Path) { if (Files.exists(path)) Files.walk(path).use { stream -> stream.sorted(Comparator.reverseOrder()).forEach { Files.deleteIfExists(it) } } }
}
