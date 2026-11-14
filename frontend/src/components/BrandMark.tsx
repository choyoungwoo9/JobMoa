import { sourceById } from '../data'
import type { SourceId } from '../types'
export function BrandMark({
  sourceId,
  small = false,
}: {
  sourceId: SourceId
  small?: boolean
}) {
  return (
    <span
      className={`brand-mark brand-${sourceId}${small ? ' brand-small' : ''}`}
      aria-hidden="true"
    >
      {sourceById(sourceId).mark}
    </span>
  )
}
