import { VersionDiff } from './VersionDiff'
import { useCallback, useEffect, useRef, useState } from 'react'
import { dateLabel, sourceById } from '../data'
import type { Category, InterestManagement, Job, JobVersion } from '../types'
import { BrandMark } from './BrandMark'
import { Icon } from './Icon'
import { Modal } from './Modal'
import { InterestEditor, InterestStar } from './InterestControls'
import { ApplicationRequirementsPanel } from './ApplicationRequirementsPanel'

function sampleCapture(job: Job, version: JobVersion, revision: number) {
  const escape = (value: string) =>
    value.replace(
      /[&<>"']/g,
      (c) =>
        ({
          '&': '&amp;',
          '<': '&lt;',
          '>': '&gt;',
          '"': '&quot;',
          "'": '&apos;',
        })[c]!,
    )
  const title = job.title.length > 34 ? `${job.title.slice(0, 33)}…` : job.title
  const svg = `<svg xmlns="http://www.w3.org/2000/svg" width="880" height="1120" viewBox="0 0 880 1120"><rect width="880" height="1120" fill="#fff"/><rect width="880" height="62" fill="#f6f7f8"/><circle cx="28" cy="31" r="5" fill="#e5b0a8"/><circle cx="46" cy="31" r="5" fill="#e7d19b"/><circle cx="64" cy="31" r="5" fill="#a8cbb6"/><text x="440" y="37" text-anchor="middle" font-size="14" fill="#858a91" font-family="sans-serif">JOBMOA · SAMPLE CAPTURE · VERSION ${revision}</text><g font-family="sans-serif"><text x="72" y="146" font-size="23" font-weight="700" fill="#347052">${escape(job.company)}</text><rect x="72" y="190" width="117" height="29" rx="5" fill="#edf3e8"/><text x="86" y="210" font-size="13" fill="#6a8458">화면 검토용 예시</text><text x="72" y="272" font-size="27" font-weight="700" fill="#29362b">${escape(title)}</text><text x="72" y="313" font-size="16" fill="#8a9584">${escape(job.location)} · v${revision} · ${escape(dateLabel(version.savedAt))}</text><path d="M72 352H808" stroke="#e7eae2"/><text x="72" y="410" font-size="21" font-weight="700" fill="#34432e">함께할 팀을 소개해요</text><text x="72" y="455" font-size="16" fill="#7d8874">사용자의 일상을 더 편리하게 만드는 서비스를 개발하는 팀이에요.</text><text x="72" y="485" font-size="16" fill="#7d8874">안정적인 서비스와 더 나은 개발 경험을 함께 만들어가요.</text><text x="72" y="560" font-size="21" font-weight="700" fill="#34432e">이런 일을 함께해요</text><text x="72" y="606" font-size="16" fill="#7d8874">• 서비스의 핵심 기능을 설계하고 개발해요.</text><text x="72" y="640" font-size="16" fill="#7d8874">• 성능과 안정성을 개선하고 운영 과정의 문제를 해결해요.</text><text x="72" y="674" font-size="16" fill="#7d8874">• 코드 리뷰와 기술 공유를 통해 함께 성장해요.</text>${revision > 1 ? '<rect x="67" y="689" width="741" height="35" rx="4" fill="#eff5e6"/><text x="72" y="713" font-size="16" fill="#6d874d">• 서비스 확장에 맞춰 데이터 처리 구조를 개선해요. (변경 예시)</text>' : ''}<text x="72" y="784" font-size="21" font-weight="700" fill="#34432e">이런 경험이 있으면 좋아요</text><text x="72" y="830" font-size="16" fill="#7d8874">• 서비스 개발과 운영을 경험하신 분</text><text x="72" y="864" font-size="16" fill="#7d8874">• 문제의 원인을 파악하고 해결 과정을 설명할 수 있는 분</text><text x="72" y="898" font-size="16" fill="#7d8874">• 동료와 의견을 나누며 제품을 개선하는 것을 즐기는 분</text><rect x="72" y="971" width="736" height="85" rx="10" fill="#f8f7f2"/><text x="96" y="1006" font-size="16" font-weight="700" fill="#9a8a63">실제 채용공고의 캡처가 아닙니다.</text><text x="96" y="1035" font-size="14" fill="#a39575">본문·이미지 저장 방식을 검토하기 위한 샘플 이미지입니다.</text></g></svg>`
  return `data:image/svg+xml;charset=utf-8,${encodeURIComponent(svg)}`
}

export function JobDrawer({
  job,
  categories,
  onClose,
  onToggleRead,
  onStar,
  onSaveInterest,
  position,
  total,
  onPrevious,
  onNext,
  demo = false,
}: {
  job: Job
  categories: Category[]
  onClose: () => void
  onToggleRead: () => void
  onStar: (id: string, starred: boolean) => Promise<boolean>
  onSaveInterest: (id: string, management: InterestManagement) => Promise<boolean>
  position: number
  total: number
  onPrevious: () => void
  onNext: () => void
  demo?: boolean
}) {
  const [tab, setTab] = useState<'capture' | 'text' | 'diff'>('capture')
  const [versionId, setVersionId] = useState(job.versions[0].id)
  const [zoomed, setZoomed] = useState(false)
  const closeButton = useRef<HTMLButtonElement>(null)
  const zoomedRef = useRef(zoomed)
  const jobIdRef = useRef(job.id)
  const closeZoom = useCallback(() => setZoomed(false), [])
  useEffect(() => { zoomedRef.current = zoomed }, [zoomed])
  useEffect(() => { jobIdRef.current = job.id }, [job.id])
  useEffect(() => {
    const body = document.body
    const previousOverflow = body.style.overflow
    const previousPaddingRight = body.style.paddingRight
    const previousFocus = document.activeElement instanceof HTMLElement ? document.activeElement : null
    const scrollY = window.scrollY
    const scrollbarWidth = window.innerWidth - document.documentElement.clientWidth
    body.style.overflow = 'hidden'
    if (scrollbarWidth > 0) {
      const currentPadding = Number.parseFloat(getComputedStyle(body).paddingRight) || 0
      body.style.paddingRight = `${currentPadding + scrollbarWidth}px`
    }
    closeButton.current?.focus()
    const onKeyDown = (event: KeyboardEvent) => {
      if (event.key === 'Escape') {
        event.preventDefault()
        if (zoomedRef.current) closeZoom()
        else onClose()
      } else if (event.key === 'Tab') {
        const drawer = document.querySelector<HTMLElement>('.job-drawer')
        const focusable = drawer ? Array.from(drawer.querySelectorAll<HTMLElement>('a[href], button:not(:disabled), input:not(:disabled), select:not(:disabled), textarea:not(:disabled), [tabindex]:not([tabindex="-1"])')) : []
        const first = focusable[0]
        const last = focusable.at(-1)
        if (event.shiftKey && (document.activeElement === first || !drawer?.contains(document.activeElement))) {
          event.preventDefault()
          last?.focus()
        } else if (!event.shiftKey && (document.activeElement === last || !drawer?.contains(document.activeElement))) {
          event.preventDefault()
          first?.focus()
        }
      }
    }
    window.addEventListener('keydown', onKeyDown)
    return () => {
      window.removeEventListener('keydown', onKeyDown)
      body.style.overflow = previousOverflow
      body.style.paddingRight = previousPaddingRight
      window.scrollTo(0, scrollY)
      const returnFocus = previousFocus?.isConnected
        ? previousFocus
        : Array.from(document.querySelectorAll<HTMLElement>('[data-job-id]')).find(element => element.dataset.jobId === jobIdRef.current) ?? document.querySelector<HTMLElement>('.jobs-section')
      returnFocus?.focus({ preventScroll: true })
    }
  }, [closeZoom, onClose])
  useEffect(() => {
    setTab('capture')
    setVersionId(job.versions[0].id)
    setZoomed(false)
  }, [job.id])
  const version =
    job.versions.find((v) => v.id === versionId) ?? job.versions[0]
  const index = job.versions.findIndex((v) => v.id === version.id)
  const revision = job.versions.length - index
  return (
    <div className="job-drawer-backdrop" onClick={(event) => { if (event.target === event.currentTarget) onClose() }}>
    <aside aria-labelledby="job-title" aria-modal="true" role="dialog" className="job-drawer">
      <div className="drawer-top">
        <span>
          <Icon name="file" size={17} />
          저장된 공고{!job.versions[0].captureUrl && <span className="sample-pill">샘플</span>}
        </span>
        <nav className="drawer-navigation" aria-label="공고 연속 탐색">
          <button
            className="icon-button"
            onClick={onPrevious}
            disabled={position <= 0}
            aria-label="이전 공고"
          >
            <Icon name="chevron" size={14} className="rotate-180" />
          </button>
          <span>
            {position + 1} / {total}
          </span>
          <button
            className="icon-button"
            onClick={onNext}
            disabled={position >= total - 1}
            aria-label="다음 공고"
          >
            <Icon name="chevron" size={14} />
          </button>
          <button
            ref={closeButton}
            className="icon-button"
            onClick={onClose}
            aria-label="공고 상세 닫기"
          >
            <Icon name="close" size={17} />
          </button>
        </nav>
      </div>
      <div className="drawer-body">
        <div className="drawer-company">
          <BrandMark sourceId={job.sourceId} />
          <span>
            {job.company}
            <small>{sourceById(job.sourceId).name} 기업군</small>
          </span>
          {job.closed && <span className="closed-badge">마감 · 보관 중</span>}
        </div>
        <div className="drawer-title-line"><h2 id="job-title">{job.title}</h2><InterestStar job={job} onChange={onStar} /></div>
        <InterestEditor key={job.id} job={job} onSave={onSaveInterest} />
        <dl className="job-metadata">
          <div>
            <dt>근무지</dt>
            <dd>{job.location}</dd>
          </div>
          <div>
            <dt>최초 발견</dt>
            <dd>{dateLabel(job.firstSeen, true)}</dd>
          </div>
          <div>
            <dt>보관된 버전</dt>
            <dd>{job.versions.length}개</dd>
          </div>
          {job.closed && <div>
            <dt>마감 확인</dt>
            <dd>{job.lastSeenAt ? dateLabel(job.lastSeenAt, true) : '확인 시각 정보 없음'}</dd>
          </div>}
        </dl>
        <section className="classification-detail" aria-label="직군 분류">
          <div className="classification-tags">{job.categories?.length ? job.categories.map(id => <span className="job-category" key={id}>{categories.find(category => category.id === id)?.name ?? id}</span>) : <span className="job-category unmapped">미분류</span>}</div>
          {job.classification ? <details>
            <summary>분류 근거 · 규칙 v{job.classification.rulesVersion}</summary>
            <p>최신 저장 본문 기준 · {dateLabel(job.classification.classifiedAt, true)} 분류</p>
            {job.classification.unclassifiedReason && <p>{job.classification.unclassifiedReason}</p>}
            {job.categories?.map(id => <div key={id}>
              <strong>{categories.find(category => category.id === id)?.name ?? id}</strong>
              {job.classification!.evidence[id]?.length ? job.classification!.evidence[id].map((text, index) => <blockquote key={index}>{text}</blockquote>) : <p>{id === 'web-backend' || id === 'web-frontend' ? '직군 조건 충족 또는 풀스택의 프론트엔드·백엔드 동시 담당 규칙을 적용했어요.' : '직함과 본문의 직군 조건에 부합해요.'}</p>}
            </div>)}
          </details> : <p>아직 자동 분류를 실행하지 않은 공고예요.</p>}
        </section>
        {!demo && <ApplicationRequirementsPanel key={job.id} job={job} />}
        <div className="drawer-actions">
          <button
            className={`button ${job.read ? 'read-button' : 'secondary'}`}
            onClick={onToggleRead}
          >
            <Icon name="check" size={16} />
            {job.read ? '안 읽음으로 변경' : '읽음으로 표시'}
          </button>
          <a
            className="button secondary"
            href={job.url}
            target="_blank"
            rel="noreferrer"
          >
            원문 새 탭
            <Icon name="external" size={14} />
          </a>
        </div>
        <div className="inline-note">
          <Icon name="info" size={14} />
          <span>
            열면 자동으로 읽음 처리해요. 원문은 새 탭에서 확인할 수 있어요.
          </span>
        </div>
        <div className="version-picker">
          <span>
            <Icon name="history" size={15} />
            저장된 버전
          </span>
          <select
            aria-label="저장된 공고 버전"
            value={version.id}
            onChange={(e) => setVersionId(e.target.value)}
          >
            {job.versions.map((v, i) => (
              <option key={v.id} value={v.id}>
                v{job.versions.length - i} · {dateLabel(v.savedAt, true)}
                {i === 0 ? ' · 최신' : ' · 이전 버전'}
              </option>
            ))}
          </select>
        </div>
        {index > 0 && (
          <div className="old-version-note">
            이전 버전을 보고 있어요. 최신 내용은 버전을 변경해 확인하세요.
          </div>
        )}
        <div
          className="content-tabs"
          role="tablist"
          aria-label="저장된 공고 형식"
        >
          <button
            role="tab"
            id="text-tab"
            aria-selected={tab === 'text'}
            aria-controls="saved-content"
            className={tab === 'text' ? 'active' : ''}
            onClick={() => setTab('text')}
          >
            <Icon name="file" size={17} />
            저장된 본문
          </button>
          <button
            role="tab"
            id="capture-tab"
            aria-selected={tab === 'capture'}
            aria-controls="saved-content"
            className={tab === 'capture' ? 'active' : ''}
            onClick={() => setTab('capture')}
          >
            <Icon name="image" size={17} />
            캡처 보기
          </button>
          <button role="tab" id="diff-tab" aria-selected={tab === 'diff'} aria-controls="saved-content" className={tab === 'diff' ? 'active' : ''} onClick={() => setTab('diff')}>변경점 비교</button>
          <span>
            v{revision} · {dateLabel(version.savedAt)}
          </span>
        </div>
        <div
          role="tabpanel"
          id="saved-content"
          aria-labelledby={`${tab}-tab`}
        >
          {tab === 'diff' ? <VersionDiff key={version.id} versions={job.versions} current={version} /> : tab === 'text' ? (
            <div className="saved-text">
              {version.bodyLoaded === false ? '저장된 본문을 불러오는 중이에요…' : version.body ||
                '본문 추출에 실패했어요. 수집 기록에서 해당 단계를 재시도할 수 있어요.'}
            </div>
          ) : version.captureAvailable ? (
            <div className="capture-preview">
              <div>
                <Icon name="image" size={15} />
                <span>{version.captureUrl ? '수집 시점의 원문 캡처' : '예시 이미지 · 실제 공고가 아니에요'}</span>
                <button className="text-button" onClick={() => setZoomed(true)}>
                  캡처 확대
                  <Icon name="external" size={11} />
                </button>
              </div>
              <button
                className="capture-image-button"
                onClick={() => setZoomed(true)}
                aria-label="캡처 이미지 확대"
              >
                <img
                  src={version.captureUrl ?? sampleCapture(job, version, revision)}
                  alt={`${job.company} ${job.title} 버전 ${revision} 캡처 이미지`}
                />
              </button>
            </div>
          ) : (
            <div className="capture-missing">
              <Icon name="image" size={30} />
              <h3>아직 저장된 캡처가 없어요</h3>
              <p>
                수집 기록에서 실패한 단계부터 재시도할 수 있어요.
                <br />
                {version.body
                  ? '본문 텍스트는 저장되어 있어요.'
                  : '본문 추출과 캡처를 다시 진행해야 해요.'}
              </p>
            </div>
          )}
        </div>
      </div>
      {zoomed && (
        <Modal
          onClose={closeZoom}
          labelId="capture-zoom-title"
          className="capture-dialog"
        >
          <div className="drawer-top">
            <span id="capture-zoom-title">
              {job.company} · 캡처 v{revision}
            </span>
            <button
              className="icon-button"
              onClick={closeZoom}
              aria-label="캡처 확대 닫기"
            >
              <Icon name="close" />
            </button>
          </div>
          <img
            src={version.captureUrl ?? sampleCapture(job, version, revision)}
            alt={`${job.title} 버전 ${revision} 확대 이미지`}
          />
        </Modal>
      )}
    </aside>
    </div>
  )
}
