export interface DiffLine { kind: 'same' | 'add' | 'remove'; text: string; oldNumber?: number; newNumber?: number }
export function sentences(text: string): string[] {
  const segmenter = new Intl.Segmenter('ko', { granularity: 'sentence' })
  return text.replace(/\r\n?/g, '\n').split('\n').flatMap(line => line.trim() ? [...segmenter.segment(line.trim())].map(part => part.segment.trim()).filter(Boolean) : [''])
}
/** 제한된 LCS. 큰 변경은 공통 앞·뒤 문장을 보존한 정확한 블록 diff로 대체한다. */
export function sentenceDiff(before: string, after: string): { lines: DiffLine[]; coarse: boolean } {
  const a = sentences(before), b = sentences(after)
  let start = 0
  while (start < a.length && start < b.length && a[start] === b[start]) start++
  let end = 0
  while (end < a.length - start && end < b.length - start && a[a.length - 1 - end] === b[b.length - 1 - end]) end++
  const x = a.slice(start, a.length - end), y = b.slice(start, b.length - end)
  const coarse = (x.length + 1) * (y.length + 1) > 2_000_000
  const changes: { kind: DiffLine['kind']; text: string }[] = []
  if (coarse) {
    changes.push(...x.map(text => ({ kind: 'remove' as const, text })), ...y.map(text => ({ kind: 'add' as const, text })))
  } else {
    const width = y.length + 1
    const matrix = new Uint32Array((x.length + 1) * width)
    for (let i = x.length - 1; i >= 0; i--) for (let j = y.length - 1; j >= 0; j--)
      matrix[i * width + j] = x[i] === y[j] ? matrix[(i + 1) * width + j + 1] + 1 : Math.max(matrix[(i + 1) * width + j], matrix[i * width + j + 1])
    let i = 0, j = 0
    while (i < x.length || j < y.length) {
      if (i < x.length && j < y.length && x[i] === y[j]) { changes.push({ kind: 'same', text: x[i++] }); j++ }
      else if (i < x.length && (j === y.length || matrix[(i + 1) * width + j] >= matrix[i * width + j + 1])) changes.push({ kind: 'remove', text: x[i++] })
      else changes.push({ kind: 'add', text: y[j++] })
    }
  }
  const all = [...a.slice(0, start).map(text => ({ kind: 'same' as const, text })), ...changes, ...a.slice(a.length - end).map(text => ({ kind: 'same' as const, text }))]
  let oldNumber = 0, newNumber = 0
  return { coarse, lines: all.map(line => ({ ...line, oldNumber: line.kind === 'add' ? undefined : ++oldNumber, newNumber: line.kind === 'remove' ? undefined : ++newNumber })) }
}
