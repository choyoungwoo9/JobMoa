import type { ReactNode } from 'react'
const paths: Record<string, ReactNode> = {
  grid: <><rect x="3" y="3" width="7" height="7" rx="1.5"/><rect x="14" y="3" width="7" height="7" rx="1.5"/><rect x="3" y="14" width="7" height="7" rx="1.5"/><rect x="14" y="14" width="7" height="7" rx="1.5"/></>,
  layers: <><path d="m12 3 9 5-9 5-9-5 9-5Z"/><path d="m3 12 9 5 9-5M3 16l9 5 9-5"/></>,
  history: <><path d="M3 11a9 9 0 1 1 2.5 7M3 4v7h7"/><path d="M12 7v5l3 2"/></>,
  external: <><path d="M14 3h7v7m0-7L10 14"/><path d="M10 3H5a2 2 0 0 0-2 2v14a2 2 0 0 0 2 2h14a2 2 0 0 0 2-2v-5"/></>,
  search: <><circle cx="10.5" cy="10.5" r="6.5"/><path d="m16 16 4.5 4.5"/></>,
  chevron: <path d="m9 5 7 7-7 7"/>, down: <path d="m6 9 6 6 6-6"/>, check: <path d="m5 12 4 4L19 6"/>,
  refresh: <><path d="M20 7A9 9 0 0 0 5 5l-2 3m0-5v5h5M4 17a9 9 0 0 0 15 2l2-3m0 5v-5h-5"/></>,
  close: <path d="m6 6 12 12M6 18 18 6"/>,
  monitor: <><rect x="3" y="3" width="18" height="13" rx="2"/><path d="M8 21h8m-4-5v5"/></>,
  info: <><circle cx="12" cy="12" r="9"/><path d="M12 11v5m0-9v.2"/></>,
  file: <><path d="M14 2H6a2 2 0 0 0-2 2v16a2 2 0 0 0 2 2h12a2 2 0 0 0 2-2V8l-6-6Z"/><path d="M14 2v6h6M8 13h8m-8 4h6"/></>,
  image: <><rect x="3" y="3" width="18" height="18" rx="2"/><circle cx="8" cy="8" r="1.5"/><path d="m21 15-5-5L5 21"/></>,
  spark: <path d="m12 3 2.5 6.5L21 12l-6.5 2.5L12 21l-2.5-6.5L3 12l6.5-2.5L12 3Z"/>,
  alert: <><path d="m10.3 3.9-8 14A2 2 0 0 0 4 21h16a2 2 0 0 0 1.7-3.1l-8-14a2 2 0 0 0-3.4 0Z"/><path d="M12 9v4m0 4v.1"/></>,
}
export function Icon({ name, size = 20, className = '' }: { name: string; size?: number; className?: string }) {
  return <svg width={size} height={size} viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.7" strokeLinecap="round" strokeLinejoin="round" className={className} aria-hidden="true">{paths[name]}</svg>
}
