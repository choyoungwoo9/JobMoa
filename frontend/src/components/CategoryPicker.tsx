import { useEffect, useMemo, useRef, useState } from 'react'
import type { Category } from '../types'
import { Icon } from './Icon'

const categoryGroups = [
  {
    name: '서비스·시스템 개발',
    ids: ['web-backend', 'web-frontend', 'android', 'ios', 'fullstack', 'embedded-system', 'robotics', 'qa-test'],
  },
  {
    name: '데이터·AI·인프라',
    ids: ['data-engineer', 'ml-engineer', 'ai-application', 'ml-platform', 'devops-sre', 'cloud-infrastructure', 'database', 'data-analysis-bi'],
  },
  {
    name: '기획·비즈니스·디자인',
    ids: ['planning', 'product-management', 'design', 'marketing', 'sales'],
  },
]

interface CategoryPickerProps {
  categories: Category[]
  counts: Record<string, number>
  value: string
  onSelect: (categoryId: string) => void
}

export function CategoryPicker({ categories, counts, value, onSelect }: CategoryPickerProps) {
  const [open, setOpen] = useState(false)
  const [query, setQuery] = useState('')
  const rootRef = useRef<HTMLDivElement>(null)
  const triggerRef = useRef<HTMLButtonElement>(null)
  const searchRef = useRef<HTMLInputElement>(null)

  const groups = useMemo(() => {
    const groupedIds = new Set(categoryGroups.flatMap(group => group.ids))
    const configured = categoryGroups.map(group => ({
      name: group.name,
      categories: group.ids.flatMap(id => {
        const category = categories.find(item => item.id === id)
        return category ? [category] : []
      }),
    })).filter(group => group.categories.length)
    const extra = categories.filter(category => category.id !== 'all' && !groupedIds.has(category.id))
    return extra.length ? [...configured, { name: '기타 직군', categories: extra }] : configured
  }, [categories])

  const selected = categories.find(category => category.id === value && category.id !== 'all')
  const triggerLabel = selected?.name ?? (value === 'unclassified' ? '직군 선택' : '전체 직군')
  const normalizedQuery = query.trim().toLocaleLowerCase()
  const visibleGroups = groups.map(group => ({
    ...group,
    categories: group.categories.filter(category => category.name.toLocaleLowerCase().includes(normalizedQuery)),
  })).filter(group => group.categories.length)

  useEffect(() => {
    if (!open) return
    searchRef.current?.focus()
    const onPointerDown = (event: PointerEvent) => {
      if (!rootRef.current?.contains(event.target as Node)) setOpen(false)
    }
    const onKeyDown = (event: KeyboardEvent) => {
      if (event.key === 'Escape') {
        event.preventDefault()
        setOpen(false)
        setQuery('')
        triggerRef.current?.focus()
      }
    }
    document.addEventListener('pointerdown', onPointerDown)
    document.addEventListener('keydown', onKeyDown)
    return () => {
      document.removeEventListener('pointerdown', onPointerDown)
      document.removeEventListener('keydown', onKeyDown)
    }
  }, [open])

  const choose = (categoryId: string) => {
    onSelect(categoryId)
    setOpen(false)
    setQuery('')
    requestAnimationFrame(() => triggerRef.current?.focus())
  }

  return <div className="category-picker" ref={rootRef} onBlur={event => {
    if (!rootRef.current?.contains(event.relatedTarget as Node | null)) setOpen(false)
  }}>
    <button
      ref={triggerRef}
      type="button"
      className="category-picker-trigger"
      aria-label={`직군 선택: ${triggerLabel}`}
      aria-expanded={open}
      aria-haspopup="dialog"
      aria-controls="category-picker-panel"
      onClick={() => setOpen(current => !current)}
    >
      <strong>{triggerLabel}</strong>
      {(value === 'all' || selected) && <span className="category-picker-count">{counts[selected?.id ?? 'all'] ?? 0}</span>}
      <Icon name="down" size={14} />
    </button>
    {open && <div id="category-picker-panel" className="category-picker-panel" role="dialog" aria-label="직군 선택">
      <label className="category-picker-search">
        <Icon name="search" size={15} />
        <input
          ref={searchRef}
          type="search"
          value={query}
          placeholder="직군 이름 검색"
          aria-label="직군 이름 검색"
          onChange={event => setQuery(event.target.value)}
        />
      </label>
      <div className="category-picker-options">
        {!normalizedQuery && <button type="button" className={`category-picker-option category-picker-all ${value === 'all' ? 'selected' : ''}`} aria-pressed={value === 'all'} onClick={() => choose('all')}>
          <span>전체 직군</span><span>{counts.all ?? 0}</span>{value === 'all' && <Icon name="check" size={14} />}
        </button>}
        {visibleGroups.map(group => <section className="category-picker-group" key={group.name}>
          <h3>{group.name}</h3>
          {group.categories.map(category => <button
            type="button"
            key={category.id}
            className={`category-picker-option ${value === category.id ? 'selected' : ''}`}
            aria-pressed={value === category.id}
            onClick={() => choose(category.id)}
          >
            <span>{category.name}</span>
            <span>{counts[category.id] ?? 0}</span>
            {value === category.id && <Icon name="check" size={14} />}
          </button>)}
        </section>)}
        {!visibleGroups.length && <p className="category-picker-empty">일치하는 직군이 없어요.</p>}
      </div>
      <p className="category-picker-footnote">한 공고가 여러 직군에 포함될 수 있어요.</p>
    </div>}
  </div>
}
