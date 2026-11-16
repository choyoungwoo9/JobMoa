import type { InterestPriority, Job, PostingInterest } from './types'

export const EMPTY_INTEREST: PostingInterest = { starred: false, note: '', priority: 'NONE', applicationStatus: 'BEFORE_APPLY', appliedOn: null }
export const interestOf = (job: Job): PostingInterest => job.interest ?? EMPTY_INTEREST
export const PRIORITIES: { id: InterestPriority; name: string; rank: number }[] = [
  { id: 'HIGH', name: '높음', rank: 0 }, { id: 'NORMAL', name: '보통', rank: 1 },
  { id: 'LOW', name: '낮음', rank: 2 }, { id: 'NONE', name: '미지정', rank: 3 },
]
export const priorityOf = (id: InterestPriority) => PRIORITIES.find(p => p.id === id)!
export function koreaToday() {
  const parts = new Intl.DateTimeFormat('en-CA', { timeZone: 'Asia/Seoul', year: 'numeric', month: '2-digit', day: '2-digit' }).formatToParts(new Date())
  return ['year', 'month', 'day'].map(type => parts.find(part => part.type === type)!.value).join('-')
}
