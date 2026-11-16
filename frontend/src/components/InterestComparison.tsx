import { Modal } from './Modal'
import { BrandMark } from './BrandMark'
import { interestOf, priorityOf } from '../interest'
import type { Category, Job } from '../types'

export function InterestComparison({ jobs, categories, onClose }: { jobs: Job[]; categories: Category[]; onClose: () => void }) {
  return <Modal onClose={onClose} labelId="compare-title" className="compare-dialog">
    <header className="compare-heading"><div><h2 id="compare-title">관심 공고 비교</h2><p>같은 항목을 나란히 보면서 지원 후보를 추려보세요.</p></div><button className="button secondary small" onClick={onClose}>비교 닫기</button></header>
    <div className="comparison-scroll"><div className="comparison-grid" style={{ gridTemplateColumns: `repeat(${jobs.length}, minmax(280px, 1fr))` }}>
      {jobs.map(job => { const interest = interestOf(job), version = job.versions[0]; return <article className="comparison-column" key={job.id}>
        <header><BrandMark sourceId={job.sourceId} small /><span>{job.company}</span>{job.closed && <span className="closed-badge">마감</span>}<h3>{job.title}</h3></header>
        <dl><div><dt>근무지</dt><dd>{job.location || '정보 없음'}</dd></div><div><dt>직군</dt><dd>{job.categories?.map(id => categories.find(c => c.id === id)?.name ?? id).join(' · ') || '미분류'}</dd></div><div><dt>우선순위</dt><dd>{priorityOf(interest.priority).name}</dd></div><div><dt>지원 상태</dt><dd>{interest.applicationStatus === 'APPLIED' ? `지원 완료 · ${interest.appliedOn}` : '지원 전'}</dd></div></dl>
        <section className="comparison-note"><h4>내 메모</h4><p>{interest.note || '아직 메모가 없어요.'}</p></section>
        <section className="comparison-body"><h4>최신 저장 본문</h4><div>{version?.bodyLoaded === false ? '본문을 불러오는 중이에요. 잠시 후에도 표시되지 않으면 닫았다가 다시 열어주세요.' : version?.body || '저장된 본문이 없어요.'}</div></section>
        <a className="button secondary small" href={job.url} target="_blank" rel="noreferrer">원문 새 탭 ↗</a>
      </article> })}
    </div></div>
  </Modal>
}
