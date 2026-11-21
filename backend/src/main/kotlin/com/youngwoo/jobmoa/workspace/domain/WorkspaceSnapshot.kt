package com.youngwoo.jobmoa.workspace.domain

/** 테이블 이름·컬럼은 서버가 정한 목록만 사용한다. SQL을 백업에서 읽어 실행하지 않는다. */
data class SnapshotTable(val columns: List<String>, val rows: List<List<Any?>>)
data class WorkspaceSnapshot(val format: String = "jobmoa", val version: Int = 2, val schema: Int = 11,
    val exportedAt: String, val tables: Map<String, SnapshotTable>, val captures: List<String>, val careerFiles: List<String> = emptyList())
interface WorkspaceSnapshotRepository {
    fun revision(): String
    fun bumpRevision()
    fun exportTables(): Map<String, SnapshotTable>
    fun validate(snapshot: WorkspaceSnapshot)
    fun replace(snapshot: WorkspaceSnapshot)
    fun reset(scope: String)
    fun counts(): Map<String, Long>
    fun captureNames(): Set<String>
    fun careerMaterialNames(): Set<String>
}
