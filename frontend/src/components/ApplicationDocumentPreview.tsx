import { useEffect } from 'react'
import { createPortal } from 'react-dom'

type DocumentApplication = { company: string; title: string; location: string }
type ResumeSection = { id: string; heading: string; content: string; included: boolean; order: number }
type PortfolioProject = {
  id: string; title: string; summary: string; role?: string; period?: string; technologies?: string[]
  problem?: string; contributions?: string[]; outcome?: string; included: boolean; order: number
}
type ApplicationDocument = {
  resumeHeadline: string; resumeSummary: string; candidateName?: string; contactEmail?: string; contactPhone?: string; contactLinks?: string[]
  resumeSections: ResumeSection[]; portfolioProjects: PortfolioProject[]
}
type DocumentKind = 'resume' | 'portfolio'

export function ApplicationDocumentPreview({ kind, application, draft, version, onClose }: {
  kind: DocumentKind; application: DocumentApplication; draft: ApplicationDocument; version: number | null; onClose: () => void
}) {
  const heading = kind === 'resume' ? '이력서' : '포트폴리오'
  useEffect(() => {
    const previousTitle = document.title
    document.title = `${application.company} · ${application.title} · ${heading}`
    return () => { document.title = previousTitle }
  }, [application.company, application.title, heading])
  const sections = draft.resumeSections.filter(item => item.included).sort((a, b) => a.order - b.order)
  const projects = draft.portfolioProjects.filter(item => item.included).sort((a, b) => a.order - b.order)

  return createPortal(<div className="application-document-overlay" role="dialog" aria-modal="true" aria-label={`${heading} 미리보기`} onMouseDown={event => { if (event.target === event.currentTarget) onClose() }}>
    <header className="application-document-toolbar">
      <div><strong>{heading} 미리보기{version ? ` · v${version}` : ''}</strong><span>PDF 저장 버튼을 누른 뒤 인쇄 창에서 ‘PDF로 저장’을 선택하세요.</span></div>
      <div><button className="button secondary" onClick={() => window.print()}>PDF로 저장</button><button className="button secondary" onClick={onClose}>닫기</button></div>
    </header>
    {kind === 'resume' ? <article className="application-document-page application-resume-document">
      <header className="application-document-heading">
        <span className="application-document-kicker">RESUME · {application.company}</span>
        <h1>{draft.candidateName || draft.resumeHeadline || application.title}</h1>
        <p>{[draft.candidateName && draft.resumeHeadline !== application.title ? draft.resumeHeadline : '', application.title, application.location].filter(Boolean).join(' · ')}</p>
        <DocumentContact draft={draft} />
      </header>
      {draft.resumeSummary.trim() && <section className="application-document-summary"><h2>프로필</h2><p>{draft.resumeSummary}</p></section>}
      {sections.map(section => <section className="application-document-section" key={section.id}>
        <h2>{section.heading}</h2><DocumentText value={section.content} />
      </section>)}
      {!sections.length && !draft.resumeSummary.trim() && <p className="application-document-empty">이력서에 넣을 내용이 아직 없습니다.</p>}
    </article> : <>
      <article className="application-document-page application-portfolio-cover">
        <span className="application-document-kicker">PORTFOLIO · {application.company}</span>
        <div className="application-portfolio-cover-content"><span className="application-portfolio-mark">P</span><h1>{draft.candidateName || draft.resumeHeadline || '프로젝트 포트폴리오'}</h1>{draft.candidateName && <p className="application-portfolio-role">{draft.resumeHeadline}</p>}<p>{draft.resumeSummary}</p><DocumentContact draft={draft} /></div>
        <div className="application-portfolio-cover-footer"><span>지원 직무</span><strong>{application.title}</strong>{application.location && <small>{application.location}</small>}</div>
        {!!projects.length && <ol className="application-portfolio-index">{projects.map(project => <li key={project.id}>{project.title}</li>)}</ol>}
      </article>
      {projects.map((project, index) => <article className="application-document-page application-portfolio-case" key={project.id}>
        <header className="application-document-heading">
          <span className="application-document-kicker">PROJECT {String(index + 1).padStart(2, '0')}</span>
          <h1>{project.title}</h1>
          {(project.role || project.period) && <p>{[project.role, project.period].filter(Boolean).join(' · ')}</p>}
        </header>
        {!!project.technologies?.length && <div className="application-document-tech">{project.technologies.map(technology => <span key={technology}>{technology}</span>)}</div>}
        <section className="application-document-section"><h2>프로젝트 개요</h2><DocumentText value={project.summary} /></section>
        {project.problem?.trim() && <section className="application-document-section"><h2>해결 과제</h2><DocumentText value={project.problem} /></section>}
        {!!project.contributions?.length && <section className="application-document-section"><h2>담당 역할과 기여</h2><ul>{project.contributions.map((item, contributionIndex) => <li key={contributionIndex}>{item}</li>)}</ul></section>}
        {project.outcome?.trim() && <section className="application-document-section"><h2>결과</h2><DocumentText value={project.outcome} /></section>}
        <footer className="application-document-page-footer"><span>{application.company} · {application.title}</span><span>{String(index + 1).padStart(2, '0')}</span></footer>
      </article>)}
      {!projects.length && <article className="application-document-page application-portfolio-case"><p className="application-document-empty">포트폴리오에 넣을 프로젝트가 아직 없습니다.</p></article>}
    </>}
  </div>, document.body)
}

function DocumentText({ value }: { value: string }) {
  const lines = value.split(/\r?\n/).map(line => line.trim()).filter(Boolean)
  const groups: Array<{ kind: 'bullet' | 'paragraph'; lines: string[] }> = []
  for (const line of lines) {
    const bullet = /^[-•▪]\s+/.test(line)
    const clean = bullet ? line.replace(/^[-•▪]\s+/, '') : line
    const last = groups.at(-1)
    if (last?.kind === (bullet ? 'bullet' : 'paragraph')) last.lines.push(clean)
    else groups.push({ kind: bullet ? 'bullet' : 'paragraph', lines: [clean] })
  }
  return <div className="application-document-text">{groups.map((group, index) => group.kind === 'bullet'
    ? <ul key={index}>{group.lines.map((line, itemIndex) => <li key={itemIndex}>{line}</li>)}</ul>
    : <p key={index}>{group.lines.join(' ')}</p>)}</div>
}

function DocumentContact({ draft }: { draft: ApplicationDocument }) {
  const email = draft.contactEmail?.trim()
  const phone = draft.contactPhone?.trim()
  const links = (draft.contactLinks ?? []).map(value => value.trim()).filter(Boolean)
  if (!email && !phone && !links.length) return null
  return <div className="application-document-contact">
    {email && <a href={`mailto:${email}`}>{email}</a>}
    {phone && <span>{phone}</span>}
    {links.map((value, index) => <a key={`${value}-${index}`} href={/^https?:\/\//i.test(value) ? value : `https://${value}`} target="_blank" rel="noreferrer">{value}</a>)}
  </div>
}
