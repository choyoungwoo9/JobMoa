import type {
  CollectionRun,
  DemoState,
  Failure,
  Job,
  JobVersion,
  Source,
  SourceId,
  SourceResult,
} from './types'

export const SOURCES: Source[] = [
  {
    id: 'naver',
    logo: '/logos/naver.ico',
    name: '네이버',
    mark: 'N',
    url: 'https://recruit.navercorp.com/rcrt/list.do',
  },
  { id: 'toss', logo: '/logos/toss.png', name: '토스', mark: 't', url: 'https://toss.im/career/jobs' },
  {
    id: 'kakao',
    logo: '/logos/kakao.ico',
    name: '카카오',
    mark: 'k',
    url: 'https://careers.kakao.com/jobs',
  },
  { id: 'coupang', logo: '/logos/coupang.ico', name: '쿠팡', mark: 'c', url: 'https://www.coupang.jobs/' },
  {
    id: 'daangn',
    logo: '/logos/daangn.svg',
    name: '당근',
    mark: '당',
    url: 'https://careers.daangn.com/jobs/',
  },
  {
    id: 'line',
    logo: '/logos/line.png',
    name: 'LINE',
    mark: 'L',
    url: 'https://careers.linecorp.com/ko/jobs/',
  },
  {
    id: 'woowa',
    logo: '/logos/woowa.ico',
    name: '우아한형제들',
    mark: '배',
    url: 'https://career.woowahan.com/',
  },
  { id: 'bucketplace', logo: '/logos/bucketplace.jpg', name: '오늘의집', mark: '집', url: 'https://www.bucketplace.com/careers/' },
  { id: 'socar', logo: '/logos/socar.png', name: '쏘카', mark: '쏘', url: 'https://www.socarcorp.kr/careers/jobs' },
  { id: 'kurly', logo: '/logos/kurly.png', name: '컬리', mark: '컬', url: 'https://kurly.career.greetinghr.com/ko/recruiting' },
  { id: 'musinsa', logo: '/logos/musinsa.jpg', name: '무신사', mark: 'M', url: 'https://www.musinsacareers.com/ko/home' },
  { id: 'yanolja', logo: '/logos/yanolja.ico', name: '야놀자', mark: '야', url: 'https://www.yanoljagroup.com/ko/apply' },
  { id: 'ridi', logo: '/logos/ridi.png', name: '리디', mark: 'R', url: 'https://ridi.recruit.roundhr.com/' },
  { id: 'channel', logo: '/logos/channel.ico', name: '채널코퍼레이션', mark: 'C', url: 'https://channel.io/kr/careers' },
  { id: 'yeogi', logo: '/logos/yeogi.png', name: '여기어때', mark: '여', url: 'https://internship.yeogi.com/' },
  { id: 'banksalad', logo: '/logos/banksalad.ico', name: '뱅크샐러드', mark: 'B', url: 'https://corp.banksalad.com/jobs/' },
  { id: 'zigbang', logo: '/logos/zigbang.png', name: '직방', mark: '직', url: 'https://zigbang.career.greetinghr.com/ko/home' },
]
export const sourceById = (id: SourceId) =>
  SOURCES.find((source) => source.id === id)!
export const STAGES = {
  list: '목록 조회',
  body: '본문 추출',
  capture: '캡처',
  save: '저장',
}
export const STATUS_LABELS = {
  waiting: '대기',
  running: '수집 중',
  success: '성공',
  partial: '일부 실패',
  failed: '실패',
}

export function sampleBody(company: string, title: string, revision = 1) {
  return `${company}\n${title}\n\n이 본문은 화면 검토용 예시이며 실제 채용공고가 아닙니다.\n\n함께할 팀을 소개해요\n사용자의 일상을 더 편리하게 만드는 서비스를 개발하는 팀이에요. 안정적으로 서비스를 운영하고, 동료들과 더 나은 개발 경험을 만들어가요.\n\n이런 일을 함께해요\n• 서비스의 핵심 기능을 설계하고 개발해요.\n• 성능과 안정성을 개선하고 운영 과정의 문제를 해결해요.\n• 코드 리뷰와 기술 공유를 통해 함께 성장해요.\n${revision > 1 ? '• 서비스 확장에 맞춰 데이터 처리 구조를 개선해요. (변경된 내용 예시)\n' : ''}\n이런 경험이 있으면 좋아요\n• 서비스 개발과 운영을 경험하신 분\n• 문제의 원인을 파악하고 해결 과정을 설명할 수 있는 분\n• 동료와 의견을 나누며 제품을 개선하는 것을 즐기는 분\n\n저장된 버전 ${revision}\n실제 수집 시 브라우저로 렌더링한 공고에서 본문 텍스트와 캡처 이미지를 함께 보관하는 방식을 검토 중이에요. 지금은 모두 샘플입니다.`
}

const JOB_SEEDS: [SourceId, string, string, string][] = [
  ['naver', '네이버', '검색 서비스 Backend Developer', '경기 성남 · 분당'],
  ['toss', '토스뱅크', 'Server Developer', '서울 강남'],
  ['daangn', '당근', 'Software Engineer, Frontend', '서울 서초'],
  ['kakao', '카카오페이', '결제 플랫폼 서버 개발자', '경기 성남 · 판교'],
  ['woowa', '우아한형제들', '커머스 서비스 백엔드 개발자', '서울 송파'],
  ['line', 'LINE Plus', 'LINE Platform Server Engineer', '경기 성남 · 분당'],
  ['coupang', '쿠팡', 'Software Engineer, Fulfillment Platform', '서울 송파'],
  ['naver', '네이버클라우드', 'Cloud Platform Engineer', '경기 성남 · 분당'],
  ['toss', '토스', 'Frontend Developer', '서울 강남'],
  ['kakao', '카카오', '카카오톡 Android 개발자', '경기 성남 · 판교'],
  ['daangn', '당근페이', 'Software Engineer, Backend', '서울 서초'],
  ['woowa', '우아한형제들', '배달 플랫폼 iOS 개발자', '서울 송파'],
  ['naver', '네이버파이낸셜', '페이 서비스 서버 개발자', '경기 성남 · 분당'],
  ['toss', '토스증권', 'Data Platform Engineer', '서울 강남'],
  ['line', 'LINE Pay Plus', 'Frontend Engineer', '경기 성남 · 분당'],
  ['coupang', '쿠팡이츠서비스', 'Backend Software Engineer', '서울 송파'],
  ['kakao', '카카오뱅크', '금융 서비스 백엔드 개발자', '경기 성남 · 판교'],
  ['daangn', '당근', 'Software Engineer, Android', '서울 서초'],
  ['naver', '네이버웹툰', '글로벌 웹툰 Frontend Developer', '경기 성남 · 판교'],
  ['toss', '토스페이먼츠', 'Server Developer, Payments', '서울 강남'],
  ['line', 'LINE Plus', 'Machine Learning Engineer', '경기 성남 · 분당'],
  ['woowa', '우아한형제들', '검색 플랫폼 개발자', '서울 송파'],
  [
    'kakao',
    '카카오모빌리티',
    '모빌리티 플랫폼 서버 개발자',
    '경기 성남 · 판교',
  ],
  ['coupang', '쿠팡', 'Software Engineer, Search', '서울 송파'],
  ['toss', '토스플레이스', 'Android Developer', '서울 강남'],
  ['naver', '네이버클라우드', 'AI 서비스 백엔드 개발자', '경기 성남 · 분당'],
  ['kakao', '카카오페이', '서비스 Frontend 개발자', '경기 성남 · 판교'],
  ['daangn', '당근', 'Software Engineer, iOS', '서울 서초'],
  ['line', 'LINE Plus', 'Data Engineer', '경기 성남 · 분당'],
  ['woowa', '우아한형제들', '배달 서비스 Android 개발자', '서울 송파'],
  ['naver', '네이버', '웹 서비스 개발자', '경기 성남 · 분당'],
  ['toss', '토스', 'Frontend Developer, Platform', '서울 강남'],
  ['kakao', '카카오', '검색 서비스 서버 개발자', '경기 성남 · 판교'],
  ['bucketplace', '오늘의집', 'Product Designer', '서울 서초'],
]

export function emptyResult(sourceId: SourceId): SourceResult {
  return {
    sourceId,
    status: 'waiting',
    added: null,
    changed: null,
    identical: null,
    failed: null,
    recovered: 0,
    failures: [],
  }
}

export function createSeedState(): DemoState {
  const now = Date.now()
  const recent = new Date(now - 42 * 60_000).toISOString()
  const jobs: Job[] = JOB_SEEDS.map(
    ([sourceId, company, title, location], index) => {
      const firstSeen = new Date(
        now -
          (index < 6 ? 42 * 60_000 : (Math.floor(index / 5) + 1) * 86_400_000),
      ).toISOString()
      const body = sampleBody(company, title)
      const versions: JobVersion[] = [
        {
          id: `v-${index}-1`,
          savedAt: firstSeen,
          body,
          captureAvailable: true,
        },
      ]
      if ([7, 8, 10].includes(index))
        versions.unshift({
          id: `v-${index}-2`,
          savedAt: recent,
          body: sampleBody(company, title, 2),
          captureAvailable: true,
        })
      return {
        id: `sample-${index + 1}`,
        sourceId,
        company,
        title,
        location,
        url: sourceById(sourceId).url,
        firstSeen,
        read: index > 5 && index % 3 === 0,
        closed: index >= 30,
        latestChange:
          index < 6 ? 'new' : [7, 8, 10].includes(index) ? 'changed' : null,
        versions,
      }
    },
  )
  const failedJob = jobs[7]
  failedJob.versions[0].captureAvailable = false
  const failure: Failure = {
    id: 'sample-capture-failure',
    sourceId: 'naver',
    company: failedJob.company,
    title: failedJob.title,
    url: failedJob.url,
    stage: 'capture',
    at: recent,
    attempts: 1,
    error:
      'TimeoutError: page.screenshot: Timeout 30000ms exceeded.\nStage: capture\nSample diagnostic — no request was sent.',
    jobId: failedJob.id,
    versionId: failedJob.versions[0].id,
    resolved: false,
  }
  const results = SOURCES.map((source): SourceResult => {
    const groupJobs = jobs.filter((job) => job.sourceId === source.id)
    if (source.id === 'kakao')
      return {
        ...emptyResult(source.id),
        status: 'failed',
        failures: [
          {
            id: 'sample-list-failure',
            sourceId: source.id,
            company: source.name,
            url: source.url,
            stage: 'list',
            at: recent,
            attempts: 1,
            error:
              'HTTP 503 Service Unavailable\nStage: list\nSample diagnostic — no request was sent.',
            resolved: false,
          },
        ],
      }
    return {
      sourceId: source.id,
      status: source.id === 'naver' ? 'partial' : 'success',
      added: groupJobs.filter((job) => job.latestChange === 'new').length,
      changed: groupJobs.filter((job) => job.latestChange === 'changed').length,
      identical: groupJobs.filter((job) => !job.latestChange).length,
      failed: source.id === 'naver' ? 1 : 0,
      recovered: 0,
      failures: source.id === 'naver' ? [failure] : [],
    }
  })
  // Kakao's list failed in the last run: its rows came from an earlier collection.
  jobs
    .filter((job) => job.sourceId === 'kakao')
    .forEach((job) => {
      job.latestChange = null
      job.firstSeen = new Date(now - 86_400_000).toISOString()
      job.versions[0].savedAt = job.firstSeen
    })
  return {
    version: 1,
    jobs,
    runs: [
      {
        id: 'sample-run-latest',
        startedAt: recent,
        finishedAt: new Date(new Date(recent).getTime() + 34_000).toISOString(),
        results,
      },
      {
        id: 'sample-run-earlier',
        startedAt: new Date(now - 86_400_000).toISOString(),
        finishedAt: new Date(now - 86_400_000 + 27_000).toISOString(),
        results: SOURCES.map((source) => ({
          ...emptyResult(source.id),
          status: 'success',
          added: jobs.filter(
            (job) => job.sourceId === source.id && job.latestChange !== 'new',
          ).length,
          changed: 0,
          identical: 0,
          failed: 0,
        })),
      },
    ],
  }
}

export function makeSampleJob(
  sourceId: SourceId,
  runId: string,
  date: string,
  captureAvailable = true,
): Job {
  const [, company, title, location] = JOB_SEEDS.find(
    ([id]) => id === sourceId,
  )!
  return {
    id: `preview-${runId}-${sourceId}`,
    sourceId,
    company,
    title: `${title} · 수집 예시 ${runId.slice(-4)}`,
    location,
    url: sourceById(sourceId).url,
    firstSeen: date,
    read: false,
    closed: false,
    latestChange: 'new',
    versions: [
      {
        id: `${runId}-${sourceId}-v1`,
        savedAt: date,
        body: sampleBody(company, title),
        captureAvailable,
      },
    ],
  }
}

const KEY = 'jobmoa.frontend-preview.v1'
export function loadDemo(): DemoState {
  try {
    const value = JSON.parse(
      localStorage.getItem(KEY) ?? 'null',
    ) as DemoState | null
    if (
      !value ||
      value.version !== 1 ||
      !Array.isArray(value.jobs) ||
      !Array.isArray(value.runs)
    )
      return createSeedState()
    if (
      !value.jobs.every(
        (job) =>
          Array.isArray(job.versions) &&
          job.versions.length > 0 &&
          SOURCES.some((s) => s.id === job.sourceId),
      )
    )
      return createSeedState()
    return {
      ...value,
      runs: value.runs.map((run) =>
        run.finishedAt
          ? run
          : {
              ...run,
              finishedAt: new Date().toISOString(),
              results: run.results.map((r) =>
                ['waiting', 'running'].includes(r.status)
                  ? {
                      ...r,
                      status: 'failed' as const,
                      failures: [
                        ...r.failures,
                        {
                          id: `interrupted-${run.id}-${r.sourceId}`,
                          sourceId: r.sourceId,
                          company: sourceById(r.sourceId).name,
                          url: sourceById(r.sourceId).url,
                          stage: 'list' as const,
                          at: new Date().toISOString(),
                          attempts: 1,
                          error:
                            'PREVIEW_INTERRUPTED: 화면이 종료되어 미리보기가 중단됐어요.',
                          resolved: false,
                        },
                      ],
                    }
                  : r,
              ),
            },
      ),
    }
  } catch {
    return createSeedState()
  }
}
export function saveDemo(value: DemoState) {
  try {
    localStorage.setItem(KEY, JSON.stringify(value))
  } catch {
    /* Storage-free preview remains usable. */
  }
}
export function dateLabel(value: string, withTime = false) {
  return new Intl.DateTimeFormat('ko-KR', {
    timeZone: 'Asia/Seoul',
    month: '2-digit',
    day: '2-digit',
    ...(withTime ? { hour: '2-digit', minute: '2-digit', hour12: false } : {}),
  })
    .format(new Date(value))
    .replace(/\.\s*$/, '')
}
export function durationLabel(run: CollectionRun) {
  if (!run.finishedAt) return '진행 중'
  const seconds = Math.max(
    1,
    Math.round(
      (new Date(run.finishedAt).getTime() - new Date(run.startedAt).getTime()) /
        1000,
    ),
  )
  return seconds >= 60
    ? `${Math.floor(seconds / 60)}분 ${seconds % 60}초`
    : `${seconds}초`
}
export function summarize(run: CollectionRun) {
  const done = run.results.filter(
    (r) => !['waiting', 'running'].includes(r.status),
  ).length
  const failures = run.results.flatMap((r) => r.failures)
  return {
    added: run.results.reduce((s, r) => s + (r.added ?? 0), 0),
    changed: run.results.reduce((s, r) => s + (r.changed ?? 0), 0),
    failed: failures.length,
    done,
    status: !run.finishedAt
      ? 'running'
      : run.results.every((r) => r.status === 'failed')
        ? 'failed'
        : failures.length
          ? 'partial'
          : 'success',
  } as const
}

export const CATEGORIES = [
  { id: 'all', name: '전체 공고', description: '선택한 회사에서 수집을 지원하는 모든 직무의 공고' },
  { id: 'web-backend', name: '웹 백엔드', description: '웹·앱 서비스 API와 서버 로직 개발 · 풀스택 제외' },
  { id: 'web-frontend', name: '프론트엔드', description: '웹 화면과 브라우저 클라이언트 개발 · 풀스택 제외' },
  { id: 'android', name: 'Android', description: 'Android 앱과 클라이언트 개발' },
  { id: 'ios', name: 'iOS', description: 'iOS 앱과 클라이언트 개발' },
  { id: 'fullstack', name: '풀스택', description: '프론트엔드와 백엔드 양쪽을 담당하는 명확한 풀스택 직무' },
  { id: 'data-engineer', name: '데이터 엔지니어', description: '데이터 파이프라인·수집·처리 플랫폼 개발 · 분석가 제외' },
  { id: 'ml-engineer', name: 'ML 엔지니어', description: '머신러닝 모델·추론 서비스 구현 · 순수 연구 직무 제외' },
  { id: 'devops-sre', name: 'DevOps/SRE', description: '배포 자동화·서비스 신뢰성·운영 인프라 엔지니어링' },
  { id: 'planning', name: '기획', description: '서비스·사업 기획과 실행 계획 수립' },
  { id: 'product-management', name: 'PM', description: '제품·프로젝트의 우선순위와 실행을 이끄는 PM 직무' },
  { id: 'design', name: '디자인', description: '프로덕트·UX/UI·브랜드 등 명확한 디자인 직무' },
  { id: 'marketing', name: '마케팅', description: '브랜드·콘텐츠·퍼포먼스·CRM 등 마케팅 직무' },
  { id: 'sales', name: '영업', description: '고객·파트너 발굴과 매출 창출을 담당하는 영업 직무' },
]
export const categoryName = (id = 'all') => CATEGORIES.find(category => category.id === id)?.name ?? id
