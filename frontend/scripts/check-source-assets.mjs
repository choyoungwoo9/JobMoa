import { existsSync, readFileSync } from 'node:fs'
import { dirname, resolve, sep } from 'node:path'
import { fileURLToPath } from 'node:url'

const frontendRoot = resolve(dirname(fileURLToPath(import.meta.url)), '..')
const typesPath = resolve(frontendRoot, 'src/types.ts')
const dataPath = resolve(frontendRoot, 'src/data.ts')
const logoIndexPath = resolve(frontendRoot, 'public/logos/README.md')
const errors = []

const typeSource = readFileSync(typesPath, 'utf8')
const sourceId = typeSource.match(/export\s+type\s+SourceId\s*=([\s\S]*?)(?=\nexport\s|$)/)
const expectedIds = sourceId?.[1].matchAll(/'([^']+)'/g)
  ? [...sourceId[1].matchAll(/'([^']+)'/g)].map((match) => match[1])
  : []
if (!expectedIds.length) errors.push('src/types.ts의 SourceId 문자열 union을 찾을 수 없습니다.')

const dataSource = readFileSync(dataPath, 'utf8')
const sourceArray = dataSource.match(/export\s+const\s+SOURCES\s*:[^=]+=[\s\S]*?\[([\s\S]*?)\n\]/)
if (!sourceArray) errors.push('src/data.ts의 SOURCES 배열을 찾을 수 없습니다.')

const sources = [...(sourceArray?.[1] ?? '').matchAll(/\{([^{}]*)\}/g)].map(([, object]) => {
  const value = (key) => object.match(new RegExp(`\\b${key}\\s*:\\s*['\"]([^'\"]*)['\"]`))?.[1] ?? null
  return { id: value('id'), name: value('name'), mark: value('mark'), logo: value('logo'), url: value('url') }
})
if (!sources.length) errors.push('SOURCES 배열에서 사이트 객체를 찾을 수 없습니다.')

const actualIds = sources.map((source) => source.id).filter(Boolean)
for (const id of expectedIds) {
  if (actualIds.filter((actual) => actual === id).length !== 1) {
    errors.push(`SourceId '${id}'는 SOURCES에 정확히 한 번 등록해야 합니다.`)
  }
}
for (const id of actualIds) {
  if (!expectedIds.includes(id)) errors.push(`SOURCES의 '${id}'가 SourceId에 선언되지 않았습니다.`)
}

const logoIndex = readFileSync(logoIndexPath, 'utf8')
for (const source of sources) {
  for (const property of ['name', 'mark', 'url']) {
    if (!source[property]?.trim()) errors.push(`${source.id ?? '(ID 누락)'}: '${property}'를 채워야 합니다.`)
  }
  if (!source.logo?.startsWith('/logos/')) {
    errors.push(`${source.id ?? '(ID 누락)'}: 공식 로컬 로고 파일 경로를 등록해야 합니다.`)
    continue
  }
  const assetPath = resolve(frontendRoot, 'public', source.logo.slice(1))
  const logosRoot = resolve(frontendRoot, 'public/logos')
  if (assetPath !== logosRoot && !assetPath.startsWith(logosRoot + sep)) {
    errors.push(`${source.id}: 로고 경로가 public/logos 밖을 가리킵니다.`)
  } else if (!existsSync(assetPath)) {
    errors.push(`${source.id}: 로고 파일이 없습니다 (${source.logo}).`)
  }
  const filename = source.logo.slice('/logos/'.length)
  if (!logoIndex.includes(`](${filename})`)) {
    errors.push(`${source.id}: frontend/public/logos/README.md에 로고 출처를 기록해야 합니다.`)
  }
}

if (errors.length) {
  console.error('채용 사이트 카탈로그 확인 실패:')
  for (const error of errors) console.error(`- ${error}`)
  process.exitCode = 1
} else {
  console.log(`채용 사이트 ${sources.length}곳의 프론트 정보·공식 로고 파일·출처가 모두 등록되어 있습니다.`)
}
