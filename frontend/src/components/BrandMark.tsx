import { useState } from 'react'
import { sourceById } from '../data'
import type { SourceId } from '../types'

export function BrandMark({
  sourceId,
  small = false,
}: {
  sourceId: SourceId
  small?: boolean
}) {
  const source = sourceById(sourceId)
  const [failedLogo, setFailedLogo] = useState<string | null>(null)
  return (
    <span
      className={`brand-mark brand-${sourceId}${small ? ' brand-small' : ''}`}
      aria-hidden="true"
    >
      {failedLogo === source.logo ? source.mark : <img
        src={source.logo}
        alt=""
        width={35}
        height={35}
        decoding="async"
        onError={() => setFailedLogo(source.logo)}
      />}
    </span>
  )
}
