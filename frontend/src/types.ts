export type SourceId =
  | 'naver'
  | 'toss'
  | 'kakao'
  | 'coupang'
  | 'daangn'
  | 'line'
  | 'woowa'
  | 'bucketplace'
  | 'socar'
  | 'kurly'
  | 'musinsa'
  | 'yanolja'
  | 'ridi'
  | 'channel'
  | 'yeogi'
  | 'banksalad'
  | 'zigbang'
export interface Category {
  id: string
  name: string
  description?: string
}
export interface Company {
  id: string
  sourceId: SourceId
  name: string
  supportedCategoryIds: string[]
  applicationPageStatus?: 'unchecked' | 'checking' | 'public' | 'mixed' | 'login-required' | 'failed' | 'review'
  applicationPageNote?: string
  applicationPageChecked?: number
  applicationPageFetched?: number
  applicationPageLoginRequired?: number
  applicationPageFailed?: number
}
export interface Source {
  id: SourceId
  name: string
  mark: string
  logo: string
  url: string
}
export interface JobVersion {
  id: string
  savedAt: string
  body: string
  captureAvailable: boolean
  captureUrl?: string
  bodyLoaded?: boolean
}
export type InterestPriority = 'HIGH' | 'NORMAL' | 'LOW' | 'NONE'
export type ApplicationStatus = 'BEFORE_APPLY' | 'APPLIED'
export interface PostingInterest {
  starred: boolean
  note: string
  priority: InterestPriority
  applicationStatus: ApplicationStatus
  appliedOn: string | null
  updatedAt?: string | null
}
export type InterestManagement = Pick<PostingInterest, 'note' | 'priority' | 'applicationStatus' | 'appliedOn'>
export interface Job {
  interest?: PostingInterest
  companyId?: string
  classification?: {
    rulesVersion: string
    parserMode: string
    evidence: Record<string, string[]>
    classifiedAt: string
    unclassifiedReason?: string | null
  } | null
  categories?: string[]
  id: string
  sourceId: SourceId
  company: string
  title: string
  location: string
  url: string
  firstSeen: string
  lastSeenAt?: string
  read: boolean
  closed: boolean
  latestChange: 'new' | 'changed' | null
  versions: JobVersion[]
}
export type FailureStage = 'list' | 'body' | 'capture' | 'save'
export interface Failure {
  id: string
  sourceId: SourceId
  company: string
  title?: string
  url: string
  stage: FailureStage
  at: string
  attempts: number
  error: string
  jobId?: string
  versionId?: string
  pendingJob?: Job
  resolved: boolean
}
export interface SourceResult {
  closed?: number | null
  sourceId: SourceId
  status: 'waiting' | 'running' | 'success' | 'partial' | 'failed'
  added: number | null
  changed: number | null
  identical: number | null
  failed: number | null
  recovered: number
  failures: Failure[]
  succeeded?: number
  total?: number
  excluded?: number
}
export interface CollectionRun {
  categoryIds?: string[]
  categoryId?: string
  companyIds?: string[]
  id: string
  startedAt: string
  finishedAt?: string
  results: SourceResult[]
  retryOf?: string
  retriedFailures?: string[]
  progress?: { discoveryFinished: boolean; total: number; pending: number; processing: number; completed: number; failed: number; percent: number | null }
  applicationProgress?: { total: number; pending: number; processing: number; fetched: number; failed: number }
  applicationResults?: { companyId: string; company: string; status: 'running' | 'success' | 'partial' | 'login-required' | 'failed' | 'waiting'; total: number; fetched: number; failed: number; loginRequired: number; pending: number; processing: number }[]
}
export interface DemoState {
  workspaceRevision?: string
  version: 1
  jobs: Job[]
  runs: CollectionRun[]
}

export interface AiWorkerStatus {
  connected: boolean
  workerId?: string | null
  model: string
  webSearch: boolean
  lastSeenAt?: string | null
}
export type AiCitationType = 'POSTING' | 'WEB' | 'WORKSPACE'
export interface AiCitation {
  type: AiCitationType
  postingId?: number | null
  versionId?: number | null
  company?: string | null
  title: string
  url?: string | null
  reason?: string | null
  position: number
}
export type AiMessageRole = 'USER' | 'ASSISTANT'
export type AiMessageStatus = 'COMPLETE' | 'QUEUED' | 'PROCESSING' | 'FAILED'
export interface AiMessage {
  id: string
  conversationId: string
  role: AiMessageRole
  status: AiMessageStatus
  content: string
  errorMessage?: string | null
  createdAt: string
  updatedAt: string
  citations: AiCitation[]
  taskId?: string | null
  lastEventId: number
  searchPerformed: boolean
}
export interface AiConversationPreview {
  id: string
  title: string
  updatedAt: string
  preview: string
}
