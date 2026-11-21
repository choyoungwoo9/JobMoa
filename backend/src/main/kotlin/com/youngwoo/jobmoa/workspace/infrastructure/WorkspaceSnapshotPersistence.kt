package com.youngwoo.jobmoa.workspace.infrastructure

import com.youngwoo.jobmoa.workspace.domain.*
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.stereotype.Repository
import java.sql.Types
import java.time.Instant
import java.util.UUID

@Repository
class WorkspaceSnapshotPersistence(private val jdbc: JdbcTemplate) : WorkspaceSnapshotRepository {
    // 부모 → 자식 순서. 삭제는 역순으로 하며 FK를 끄거나 TRUNCATE하지 않는다.
    private val tables = listOf("collection_run", "collection_run_source", "crawl_task", "posting", "posting_version",
        "posting_category", "posting_observation", "posting_interest", "collection_preset", "collection_change",
        "career_material", "career_material_fragment", "career_fact", "career_fact_evidence", "posting_application_requirement", "career_assistant_task")
    private data class Column(val name: String, val type: Int)
    private fun metadata(table: String): List<Column> = jdbc.execute(org.springframework.jdbc.core.ConnectionCallback { connection ->
        connection.prepareStatement("SELECT * FROM `$table` LIMIT 0").use { statement -> statement.executeQuery().use { rs ->
            (1..rs.metaData.columnCount).map { Column(rs.metaData.getColumnName(it), rs.metaData.getColumnType(it)) }
        } }
    })
    override fun revision() = jdbc.queryForObject("SELECT revision FROM workspace_revision WHERE id='workspace'", String::class.java)!!
    override fun bumpRevision() { jdbc.update("UPDATE workspace_revision SET revision=? WHERE id='workspace'", UUID.randomUUID().toString()) }
    override fun counts(): Map<String, Long> = mapOf(
        "postings" to count("posting"), "versions" to count("posting_version"), "runs" to count("collection_run"),
        "interests" to (jdbc.queryForObject("SELECT COUNT(*) FROM posting_interest WHERE starred=1", Long::class.java) ?: 0),
        "management" to count("posting_interest"), "presets" to count("collection_preset"),
        "careerMaterials" to count("career_material"), "careerFacts" to count("career_fact"),
        "careerTasks" to count("career_assistant_task"),
        "applicationRequirements" to count("posting_application_requirement"),
        "read" to (jdbc.queryForObject("SELECT COUNT(*) FROM posting WHERE is_read=1", Long::class.java) ?: 0))
    private fun count(table: String) = jdbc.queryForObject("SELECT COUNT(*) FROM `$table`", Long::class.java) ?: 0
    override fun captureNames(): Set<String> = jdbc.queryForList("SELECT capture_path FROM posting_version UNION SELECT capture_path FROM crawl_task WHERE capture_path IS NOT NULL", String::class.java).filterNotNull().toSet()
    override fun careerMaterialNames(): Set<String> = jdbc.queryForList("SELECT CONCAT(storage_key, '.bin') FROM career_material", String::class.java).filterNotNull().toSet()
    override fun exportTables(): Map<String, SnapshotTable> = tables.associateWith { table ->
        val columns = metadata(table)
        val rows = jdbc.query("SELECT * FROM `$table`") { rs, _ -> columns.mapIndexed { index, column ->
            when (column.type) {
                Types.BIT, Types.BOOLEAN -> rs.getBoolean(index + 1).let { if (rs.wasNull()) null else it }
                Types.TIMESTAMP, Types.TIMESTAMP_WITH_TIMEZONE -> rs.getTimestamp(index + 1)?.toInstant()?.toString()
                Types.DATE -> rs.getDate(index + 1)?.toLocalDate()?.toString()
                else -> rs.getObject(index + 1)
            }
        } }
        SnapshotTable(columns.map { it.name }, rows)
    }
    override fun validate(snapshot: WorkspaceSnapshot) {
        require(snapshot.format == "jobmoa" && snapshot.version in setOf(1, 2) && snapshot.schema in setOf(10, 11, 12, 13)) { "이 버전에서 지원하는 JobMoa 백업 파일이 아닙니다." }
        val expectedTables = when (snapshot.schema) {
            10 -> tables.filterNot { it.startsWith("career_") || it == "posting_application_requirement" }.toSet()
            11 -> tables.filterNot { it == "posting_application_requirement" || it == "career_assistant_task" }.toSet()
            12 -> tables.filterNot { it == "career_assistant_task" }.toSet()
            else -> tables.toSet()
        }
        require(snapshot.tables.keys == expectedTables) { "백업에 필요한 데이터가 없거나 알 수 없는 테이블이 포함되어 있습니다." }
        require(snapshot.tables.values.sumOf { it.rows.size.toLong() } <= 1_000_000) { "백업 데이터가 너무 큽니다." }
        snapshot.tables.keys.forEach { table ->
            val data = snapshot.tables.getValue(table)
            require(data.columns == metadata(table).map { it.name }) { "$table 백업 스키마가 일치하지 않습니다." }
            require(data.rows.all { row -> row.size == data.columns.size && row.all { it == null || it is String || it is Number || it is Boolean } }) { "$table 데이터 형식이 잘못되었습니다." }
        }
        fun values(table: String, column: String) = snapshot.tables.getValue(table).let { t -> t.rows.map { it[t.columns.indexOf(column)] } }
        require(values("collection_run", "finished_at").none { it == null }) { "수집 중인 백업은 복원할 수 없습니다." }
        require(values("crawl_task", "status").all { it in setOf("COMPLETED", "FAILED") }) { "처리 중인 작업을 포함한 백업은 복원할 수 없습니다." }
        fun keys(table: String, columns: List<String>): Set<List<String>> {
            val data = snapshot.tables.getValue(table)
            val positions = columns.map { data.columns.indexOf(it) }
            val keys = data.rows.map { row -> positions.map { index -> requireNotNull(row[index]) { "$table 기본 키가 없습니다." }.toString() } }
            require(keys.size == keys.toSet().size) { "$table 기본 키가 중복됩니다." }
            return keys.toSet()
        }
        val primaryKeys = snapshot.tables.keys.associateWith { table -> keys(table, when (table) {
            "posting_category" -> listOf("posting_id", "category_id")
            "posting_interest" -> listOf("posting_id")
            "collection_change" -> listOf("task_id")
            else -> listOf("id")
        }) }
        fun references(table: String, column: String, parent: String) {
            require(values(table, column).filterNotNull().all { listOf(it.toString()) in primaryKeys.getValue(parent) }) { "$table.$column 참조 데이터가 없습니다." }
        }
        references("collection_run_source", "run_id", "collection_run")
        references("crawl_task", "run_id", "collection_run")
        references("posting_version", "posting_id", "posting")
        references("posting_version", "task_id", "crawl_task")
        references("posting_category", "posting_id", "posting")
        references("posting_interest", "posting_id", "posting")
        references("posting_observation", "posting_id", "posting")
        references("posting_observation", "task_id", "crawl_task")
        references("collection_change", "posting_id", "posting")
        references("collection_change", "task_id", "crawl_task")
        references("collection_change", "run_id", "collection_run")
        val versionPostingIds = values("posting_version", "posting_id").map { it.toString() }.toSet()
        require(values("posting", "id").all { it.toString() in versionPostingIds }) { "본문 버전이 없는 공고가 있습니다." }
        require(values("posting_interest", "priority").all { it in setOf("HIGH", "NORMAL", "LOW", "NONE") } && values("posting_interest", "application_status").all { it in setOf("BEFORE_APPLY", "APPLIED") }) { "개인 관리 정보의 상태가 올바르지 않습니다." }
        require(values("collection_run", "status").all { it in setOf("COMPLETED", "PARTIAL_FAILED", "FAILED") } && values("collection_run_source", "status").all { it in setOf("COMPLETED", "FAILED") }) { "완료되지 않은 실행 또는 목록 작업이 있습니다." }
        require(values("crawl_task", "stage").all { it in setOf("BODY", "CAPTURE", "SAVE") } && values("crawl_task", "outcome").all { it == null || it in setOf("NEW", "CHANGED", "IDENTICAL", "CLOSED", "EXCLUDED") }) { "작업 단계가 올바르지 않습니다." }
        val captureReferences = (values("posting_version", "capture_path") + values("crawl_task", "capture_path")).filterNotNull().map { it.toString() }.toSet()
        require(captureReferences == snapshot.captures.toSet() && snapshot.captures.distinct().size == snapshot.captures.size) { "백업의 캡처 목록이 데이터와 일치하지 않습니다." }
        if (snapshot.schema >= 11) {
            references("career_material_fragment", "material_id", "career_material")
            references("career_fact", "material_id", "career_material")
            references("career_fact_evidence", "fact_id", "career_fact")
            references("career_fact_evidence", "fragment_id", "career_material_fragment")
            require(values("career_material", "status").all { it in setOf("WAITING_AI", "ANALYZING", "ANALYZED", "FAILED", "UNREADABLE") }) { "경력 자료 상태가 올바르지 않습니다." }
            val factStatuses = if (snapshot.schema >= 12) setOf("UNVERIFIED", "VERIFIED", "REJECTED") else setOf("UNVERIFIED")
            require(values("career_fact", "verification_status").all { it in factStatuses }) { "지원하지 않는 경력 정보 확인 상태입니다." }
            val materialFiles = values("career_material", "storage_key").map { "${it}.bin" }.toSet()
            require(materialFiles == snapshot.careerFiles.toSet() && snapshot.careerFiles.distinct().size == snapshot.careerFiles.size) { "백업의 경력 원본 파일 목록이 데이터와 일치하지 않습니다." }
            require(values("career_material", "storage_key").none { it == null } && values("career_material", "content_hash").all { it.toString().matches(Regex("[a-f0-9]{64}")) }) { "경력 자료 원본 참조가 올바르지 않습니다." }
        } else {
            require(snapshot.careerFiles.isEmpty()) { "이전 백업 형식에는 경력 원본 파일을 포함할 수 없습니다." }
        }
        if (snapshot.schema >= 12) {
            references("posting_application_requirement", "posting_id", "posting")
            require(values("posting_application_requirement", "source").all { it in setOf("POSTING_BODY", "USER_PASTED", "PUBLIC_PAGE") }) { "지원서 정보 출처가 올바르지 않습니다." }
            require(values("posting_application_requirement", "status").all { it in setOf("WAITING_PUBLIC", "FETCHING_PUBLIC", "WAITING_AI", "ANALYZING", "REVIEW", "CONFIRMED", "FAILED") }) { "지원서 정보 상태가 올바르지 않습니다." }
        }
        if (snapshot.schema >= 13) {
            references("career_assistant_task", "parent_task_id", "career_assistant_task")
            references("career_assistant_task", "posting_id", "posting")
            require(values("career_assistant_task", "task_type").all { it in setOf("RECOMMENDATIONS", "POSTING_DRAFT") }) { "지원하지 않는 AI 경력 작업입니다." }
            require(values("career_assistant_task", "status").all { it in setOf("WAITING_AI", "COMPLETED", "FAILED") }) { "진행 중인 AI 경력 작업은 백업할 수 없습니다." }
        }
    }
    override fun replace(snapshot: WorkspaceSnapshot) {
        validate(snapshot)
        tables.asReversed().forEach { jdbc.update("DELETE FROM `$it`") }
        tables.filter { it in snapshot.tables }.forEach { table ->
            val data = snapshot.tables.getValue(table)
            val columns = metadata(table)
            val sql = "INSERT INTO `$table` (${columns.joinToString(",") { "`${it.name}`" }}) VALUES (${columns.joinToString(",") { "?" }})"
            data.rows.chunked(200).forEach { batch -> jdbc.batchUpdate(sql, batch, 200) { ps, row ->
                columns.forEachIndexed { index, column ->
                    val value = row[index]
                    when {
                        value == null -> ps.setNull(index + 1, column.type)
                        column.type in setOf(Types.TIMESTAMP, Types.TIMESTAMP_WITH_TIMEZONE) -> ps.setTimestamp(index + 1, java.sql.Timestamp.from(Instant.parse(value.toString())))
                        column.type == Types.DATE -> ps.setDate(index + 1, java.sql.Date.valueOf(value.toString()))
                        else -> ps.setObject(index + 1, value)
                    }
                }
            } }
        }
        bumpRevision()
    }
    override fun reset(scope: String) {
        when (scope) {
            "interests" -> jdbc.update("DELETE FROM posting_interest")
            "read" -> jdbc.update("UPDATE posting SET is_read=0")
            "presets" -> jdbc.update("DELETE FROM collection_preset")
            "career" -> { jdbc.update("DELETE FROM career_assistant_task"); jdbc.update("DELETE FROM career_material") }
            "postings" -> { jdbc.update("DELETE FROM career_assistant_task"); tables.asReversed().filter { it != "collection_preset" && !it.startsWith("career_") }.forEach { jdbc.update("DELETE FROM `$it`") } }
            else -> throw IllegalArgumentException("지원하지 않는 초기화 범위입니다.")
        }
        bumpRevision()
    }
}
