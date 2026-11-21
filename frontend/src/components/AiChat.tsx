import { useCallback, useEffect, useRef, useState } from 'react'
import { api } from '../api'
import { Icon } from './Icon'
import type { AiCitation, AiConversationPreview, AiMessage, AiWorkerStatus } from '../types'
import './AiChat.css'

interface AiConversationDetail {
  conversation: { id: string; title: string; createdAt: string; updatedAt: string }
  messages: AiMessage[]
}
interface AiSendResponse { taskId: string; messages: AiMessage[] }
interface AiChatProps {
  demo: boolean
  onOpenPosting: (postingId: number) => void
}

export function AiChat({ demo, onOpenPosting }: AiChatProps) {
  const [worker, setWorker] = useState<AiWorkerStatus | null>(null)
  const [conversations, setConversations] = useState<AiConversationPreview[]>([])
  const [selectedId, setSelectedId] = useState<string | null>(null)
  const [messages, setMessages] = useState<AiMessage[]>([])
  const [draft, setDraft] = useState('')
  const [loading, setLoading] = useState(true)
  const [sending, setSending] = useState(false)
  const [error, setError] = useState('')
  const [progress, setProgress] = useState('')
  const threadEnd = useRef<HTMLDivElement>(null)
  const streamRef = useRef<EventSource | null>(null)
  const highestEventId = useRef(0)
  const freshConversationId = useRef<string | null>(null)
  const selectedConversationRef = useRef<string | null>(selectedId)
  selectedConversationRef.current = selectedId

  const closeStream = useCallback(() => {
    streamRef.current?.close()
    streamRef.current = null
  }, [])

  const refreshConversations = useCallback(async () => {
    const list = await api<AiConversationPreview[]>('/api/ai/conversations')
    setConversations(list)
    return list
  }, [])

  const loadConversation = useCallback(async (id: string) => {
    const detail = await api<AiConversationDetail>(`/api/ai/conversations/${id}`)
    return detail.messages
  }, [])

  const startStream = useCallback((conversationId: string, taskId: string, assistantId: string, after: number) => {
    closeStream()
    highestEventId.current = after
    const source = new EventSource(`/api/ai/tasks/${taskId}/events?after=${after}`)
    streamRef.current = source
    const acceptEvent = (event: MessageEvent<string>) => {
      const id = Number(event.lastEventId)
      if (!Number.isFinite(id) || id <= 0) return highestEventId.current
      if (id <= highestEventId.current) return null
      highestEventId.current = id
      return id
    }
    const readPayload = (event: MessageEvent<string>) => {
      try { return JSON.parse(event.data) as Record<string, string> } catch { return {} }
    }
    const statusHandler = ((raw: Event) => {
      if (streamRef.current !== source) return
      const event = raw as MessageEvent<string>
      const id = acceptEvent(event)
      if (id === null) return
      const payload = readPayload(event)
      setProgress(payload.message ?? '답변을 준비하고 있어요')
      setMessages(current => current.map(message => message.id === assistantId
        ? { ...message, status: 'PROCESSING', content: payload.resetAnswer === 'true' ? '' : message.content, lastEventId: id }
        : message))
    }) as EventListener
    const searchHandler = ((raw: Event) => {
      if (streamRef.current !== source) return
      const event = raw as MessageEvent<string>
      const id = acceptEvent(event)
      if (id === null) return
      const payload = readPayload(event)
      setProgress(payload.message ?? '외부 웹 검색 중')
      setMessages(current => current.map(message => message.id === assistantId
        ? { ...message, status: 'PROCESSING', lastEventId: id, searchPerformed: true }
        : message))
    }) as EventListener
    const deltaHandler = ((raw: Event) => {
      if (streamRef.current !== source) return
      const event = raw as MessageEvent<string>
      const id = acceptEvent(event)
      if (id === null) return
      const payload = readPayload(event)
      if (!payload.text) return
      setProgress('답변을 작성하고 있어요')
      setMessages(current => current.map(message => message.id === assistantId
        ? { ...message, status: 'PROCESSING', content: message.content + payload.text, lastEventId: id }
        : message))
    }) as EventListener
    const completeHandler = ((raw: Event) => {
      if (streamRef.current !== source) return
      if (acceptEvent(raw as MessageEvent<string>) === null) return
      closeStream()
      setProgress('')
      void loadConversation(conversationId).then(items => {
        if (selectedConversationRef.current === conversationId) setMessages(items)
      }).catch(() => undefined)
      void refreshConversations().catch(() => undefined)
    }) as EventListener
    const errorHandler = ((raw: Event) => {
      if (streamRef.current !== source) return
      const event = raw as MessageEvent<string>
      const id = acceptEvent(event)
      if (id === null) return
      const payload = readPayload(event)
      closeStream()
      setProgress('')
      setMessages(current => current.map(message => message.id === assistantId
        ? { ...message, status: 'FAILED', errorMessage: payload.message ?? '답변 생성에 실패했습니다.' }
        : message))
      void refreshConversations().catch(() => undefined)
    }) as EventListener
    source.addEventListener('status', statusHandler)
    source.addEventListener('search', searchHandler)
    source.addEventListener('delta', deltaHandler)
    source.addEventListener('complete', completeHandler)
    source.addEventListener('failed', errorHandler)
    source.onerror = () => {
      if (streamRef.current !== source) return
      if (source.readyState !== EventSource.CLOSED) setProgress('브라우저 연결을 복구 중이에요. 작업은 계속 진행됩니다.')
    }
  }, [closeStream, loadConversation, refreshConversations])

  useEffect(() => {
    if (demo) {
      setLoading(false)
      return
    }
    let alive = true
    const load = async () => {
      try {
        const [status, list] = await Promise.all([
          api<AiWorkerStatus>('/api/ai/status'),
          refreshConversations(),
        ])
        if (!alive) return
        setWorker(status)
        setConversations(list)
        if (!selectedId && list.length) setSelectedId(list[0].id)
      } catch (cause) {
        if (alive) setError(cause instanceof Error ? cause.message : 'AI 화면을 불러오지 못했습니다.')
      } finally {
        if (alive) setLoading(false)
      }
    }
    void load()
    const timer = window.setInterval(() => {
      void api<AiWorkerStatus>('/api/ai/status').then(value => alive && setWorker(value)).catch(() => undefined)
    }, 12000)
    return () => { alive = false; window.clearInterval(timer) }
  }, [demo, refreshConversations, selectedId])

  useEffect(() => {
    if (!selectedId || demo) {
      setMessages([])
      return
    }
    if (freshConversationId.current === selectedId) {
      freshConversationId.current = null
      return
    }
    let alive = true
    closeStream()
    setProgress('')
    void loadConversation(selectedId).then(items => {
      if (!alive) return
      setMessages(items)
      const pending = [...items].reverse().find(message => message.role === 'ASSISTANT' &&
        (message.status === 'QUEUED' || message.status === 'PROCESSING') && message.taskId)
      if (pending?.taskId) {
        setProgress('저장된 작업을 이어서 확인하고 있어요')
        startStream(selectedId, pending.taskId, pending.id, pending.lastEventId)
      }
    }).catch(cause => {
      if (alive) setError(cause instanceof Error ? cause.message : '대화를 불러오지 못했습니다.')
    })
    return () => { alive = false; closeStream() }
  }, [selectedId, demo, closeStream, loadConversation, startStream])

  useEffect(() => {
    threadEnd.current?.scrollIntoView({ behavior: 'smooth', block: 'end' })
  }, [messages, progress])

  useEffect(() => () => closeStream(), [closeStream])

  const createConversation = async () => {
    setError('')
    closeStream()
    try {
      const conversation = await api<AiConversationDetail['conversation']>('/api/ai/conversations', { method: 'POST' })
      selectedConversationRef.current = conversation.id
      freshConversationId.current = conversation.id
      setConversations(current => [{ id: conversation.id, title: conversation.title, updatedAt: conversation.updatedAt, preview: '' }, ...current])
      setMessages([])
      setSelectedId(conversation.id)
    } catch (cause) {
      setError(cause instanceof Error ? cause.message : '새 대화를 만들지 못했습니다.')
    }
  }

  const send = async (question = draft) => {
    const content = question.trim()
    if (!content || sending) return
    setSending(true)
    setError('')
    try {
      let conversationId = selectedId
      if (!conversationId) {
        const conversation = await api<AiConversationDetail['conversation']>('/api/ai/conversations', { method: 'POST' })
        conversationId = conversation.id
        selectedConversationRef.current = conversation.id
        freshConversationId.current = conversationId
        setSelectedId(conversationId)
        setMessages([])
      }
      const result = await api<AiSendResponse>(`/api/ai/conversations/${conversationId}/messages`, {
        method: 'POST', body: JSON.stringify({ content }),
      })
      setMessages(current => [...current, ...result.messages])
      setDraft('')
      setProgress('질문을 작업 대기열에 저장했어요')
      const assistant = result.messages.find(message => message.role === 'ASSISTANT')
      if (assistant) startStream(conversationId, result.taskId, assistant.id, assistant.lastEventId)
      await refreshConversations()
    } catch (cause) {
      setError(cause instanceof Error ? cause.message : '질문을 보내지 못했습니다.')
    } finally {
      setSending(false)
    }
  }

  const retry = async (messageId: string, taskId?: string | null) => {
    try {
      const message = await api<AiMessage>(`/api/ai/messages/${messageId}/retry`, { method: 'POST' })
      setMessages(current => current.map(item => item.id === messageId ? message : item))
      if (message.taskId ?? taskId) startStream(selectedId ?? message.conversationId, message.taskId ?? taskId!, messageId, message.lastEventId)
    } catch (cause) {
      setError(cause instanceof Error ? cause.message : '답변을 다시 요청하지 못했습니다.')
    }
  }

  const deleteConversation = async () => {
    if (!selectedId || !window.confirm('이 대화와 질문 기록을 삭제할까요?')) return
    const id = selectedId
    closeStream()
    try {
      await api<void>(`/api/ai/conversations/${id}`, { method: 'DELETE' })
      const remaining = conversations.filter(item => item.id !== id)
      selectedConversationRef.current = remaining[0]?.id ?? null
      setConversations(remaining)
      setMessages([])
      setSelectedId(remaining[0]?.id ?? null)
    } catch (cause) {
      setError(cause instanceof Error ? cause.message : '대화를 삭제하지 못했습니다.')
    }
  }

  const selectConversation = (id: string) => {
    if (id === selectedId) return
    selectedConversationRef.current = id
    setError('')
    closeStream()
    setMessages([])
    setSelectedId(id)
  }

  const pendingMessage = [...messages].reverse().find(message => message.role === 'ASSISTANT' &&
    (message.status === 'QUEUED' || message.status === 'PROCESSING'))
  const workerReady = worker?.connected === true && !demo

  if (demo) {
    return <section className="ai-view"><div className="ai-offline-card"><Icon name="spark" size={24} /><h2>AI 질문은 실제 공고 화면에서 사용할 수 있어요.</h2><p>샘플 보기에서는 저장된 대화나 Codex 워커를 호출하지 않습니다.</p></div></section>
  }

  return (
    <section className="ai-view" aria-label="AI 공고 질문">
      <aside className="ai-conversation-list">
        <button className="button primary ai-new-chat" onClick={() => void createConversation()}><Icon name="plus" size={16} />새 대화</button>
        <div className="ai-conversation-heading">이전 대화 <span>{conversations.length}</span></div>
        {loading ? <div className="ai-list-empty">대화를 불러오는 중…</div> : conversations.length === 0
          ? <div className="ai-list-empty">아직 저장된 대화가 없어요.</div>
          : conversations.map(item => (
            <button key={item.id} className={`ai-conversation-item ${selectedId === item.id ? 'selected' : ''}`} onClick={() => selectConversation(item.id)}>
              <strong>{item.title}</strong><span>{item.preview || '새 대화'}</span><time>{new Date(item.updatedAt).toLocaleDateString('ko-KR')}</time>
            </button>
          ))}
      </aside>

      <div className="ai-chat-panel">
        <header className="ai-chat-header">
          <div><span className={`ai-worker-indicator ${workerReady ? 'online' : ''}`} /><div><strong>{workerReady ? 'AI 워커 연결됨' : 'AI 질문 대기 가능'}</strong><span>Codex · {worker?.model ?? 'gpt-6-luna'} · 질문에 따라 웹 검색</span></div></div>
          {selectedId && <button className="icon-button ai-delete-chat" aria-label="대화 삭제" title="대화 삭제" onClick={() => void deleteConversation()}><Icon name="trash" size={16} /></button>}
        </header>

        {!workerReady && <div className="ai-connection-note" role="status"><Icon name="info" size={16} /><span>질문은 작업 대기열에 저장돼요. Codex 워커가 꺼져 있어 답변은 <code>./start.sh --ai-worker</code>를 실행하면 시작됩니다. 공고 수집과 관리는 워커 없이도 사용할 수 있어요.</span></div>}
        {error && <div className="ai-error" role="alert">{error}</div>}

        <div className="ai-thread" aria-live="polite">
          {messages.length === 0 ? (
            <div className="ai-empty-state">
              <div className="ai-empty-mark"><Icon name="spark" size={23} /></div>
              <h2>모든 공고를 놓고 질문해 보세요.</h2>
              <p>회사·직군·마감 상태를 질문에서 파악하고, 필요한 공고 근거를 찾아 답해요.</p>
              <div className="ai-suggestions">
                {['현재 열려 있는 백엔드 공고 중 자격 요건이 비슷한 곳을 비교해줘', '관심 기업 중 마감되지 않은 데이터 직군 공고를 정리해줘', '토스와 네이버의 개발 공고에서 요구 기술 차이를 알려줘'].map(example => <button key={example} onClick={() => setDraft(example)}>{example}<Icon name="chevron" size={13} /></button>)}
              </div>
            </div>
          ) : messages.map(message => (
            <article key={message.id} className={`ai-message ${message.role === 'USER' ? 'user' : 'assistant'}`}>
              <div className="ai-message-label">{message.role === 'USER' ? '나' : <><Icon name="spark" size={13} />JobMoa AI</>}</div>
              {message.role === 'USER' ? <div className="ai-message-content">{message.content}</div> : (
                <>
                  {message.content ? <div className="ai-message-content ai-answer">{message.content}</div> : message.status === 'QUEUED' || message.status === 'PROCESSING' ? <div className="ai-message-content ai-waiting"><span className="ai-typing-dots"><i /><i /><i /></span>{progress || '답변을 준비하고 있어요'}</div> : null}
                  {message.status === 'FAILED' && <div className="ai-failed"><span>{message.errorMessage || '답변 생성에 실패했습니다.'}</span><button className="text-button" onClick={() => void retry(message.id, message.taskId)}>다시 시도</button></div>}
                  {message.searchPerformed && message.status === 'COMPLETE' && <div className="ai-search-used"><Icon name="search" size={13} />외부 웹 검색 포함</div>}
                  {message.citations.length > 0 && <CitationList citations={message.citations} onOpenPosting={onOpenPosting} />}
                </>
              )}
            </article>
          ))}
          {pendingMessage?.content && progress && <div className="ai-live-status"><span className="ai-live-pulse" />{progress}</div>}
          <div ref={threadEnd} />
        </div>

        <form className="ai-composer" onSubmit={event => { event.preventDefault(); void send() }}>
          <textarea value={draft} onChange={event => setDraft(event.target.value)} placeholder="모아둔 공고에 대해 질문하세요" maxLength={6000} rows={2} disabled={!!pendingMessage} onKeyDown={event => { if (event.key === 'Enter' && !event.shiftKey) { event.preventDefault(); void send() } }} />
          <div><span>질문에 필요한 경우 외부 웹을 검색하고 출처를 표시해요. · {draft.length}/6000</span><button className="button primary" type="submit" disabled={!!pendingMessage || sending || !draft.trim()}><Icon name="spark" size={15} />{sending ? '저장 중' : '질문 보내기'}</button></div>
        </form>
      </div>
    </section>
  )
}

function CitationList({ citations, onOpenPosting }: { citations: AiCitation[]; onOpenPosting: (postingId: number) => void }) {
  const postings = citations.filter(citation => citation.type === 'POSTING')
  const web = citations.filter(citation => citation.type === 'WEB')
  const workspace = citations.filter(citation => citation.type === 'WORKSPACE')
  return <div className="ai-citations">
    {postings.length > 0 && <div><strong>공고 근거</strong>{postings.map((citation, index) => <button key={`${citation.postingId}-${citation.position}`} onClick={() => citation.postingId && onOpenPosting(citation.postingId)}><span>{index + 1}</span><span><b>{citation.company} · {citation.title}</b><small>{citation.reason}</small></span><Icon name="chevron" size={13} /></button>)}</div>}
    {web.length > 0 && <div><strong>외부 웹 근거</strong>{web.map((citation, index) => <a key={`${citation.url}-${citation.position}`} href={citation.url ?? '#'} target="_blank" rel="noreferrer"><span>{index + 1}</span><span><b>{citation.title}</b><small>{citation.reason || citation.url}</small></span><Icon name="external" size={13} /></a>)}</div>}
    {workspace.map(citation => <p className="ai-workspace-citation" key={citation.title}><Icon name="layers" size={13} />{citation.title} · {citation.reason}</p>)}
  </div>
}
