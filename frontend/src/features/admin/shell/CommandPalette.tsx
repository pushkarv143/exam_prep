import { useEffect, useMemo, useRef, useState } from 'react'
import { useQuery } from '@tanstack/react-query'
import { useNavigate } from 'react-router-dom'
import { CornerDownLeft, FileQuestion, ListChecks, Search, User } from 'lucide-react'
import { adminApi } from '@/api/admin'
import { usePermissions } from '@/api/portal'
import { Dialog, DialogContent, DialogTitle } from '@/components/ui/dialog'
import { cn } from '@/lib/utils'
import { ALL_NAV_ITEMS } from './adminNav'

interface Command {
  id: string
  group: string
  label: string
  hint?: string
  icon: React.ComponentType<{ className?: string }>
  run: () => void
}

/**
 * Ctrl+K / ⌘K: jump to any page you can use, or search users, tests and questions.
 * Arrow keys move, Enter opens, Esc closes.
 */
export function CommandPalette({ open, onOpenChange }: { open: boolean; onOpenChange: (o: boolean) => void }) {
  const navigate = useNavigate()
  const { can, any } = usePermissions()
  const [query, setQuery] = useState('')
  const [debounced, setDebounced] = useState('')
  const [active, setActive] = useState(0)
  const listRef = useRef<HTMLUListElement>(null)

  useEffect(() => {
    if (open) {
      setQuery('')
      setActive(0)
    }
  }, [open])
  useEffect(() => {
    const t = setTimeout(() => setDebounced(query.trim()), 200)
    return () => clearTimeout(t)
  }, [query])

  const searching = debounced.length >= 2
  const users = useQuery({
    queryKey: ['palette', 'users', debounced], enabled: open && searching && can('user.view'),
    queryFn: () => adminApi.users({ q: debounced }), staleTime: 30_000,
  })
  const tests = useQuery({
    queryKey: ['palette', 'tests', debounced], enabled: open && searching && can('test.view'),
    queryFn: () => adminApi.tests({ q: debounced, size: 5 }), staleTime: 30_000,
  })
  const questions = useQuery({
    queryKey: ['palette', 'questions', debounced], enabled: open && searching && can('question.view'),
    queryFn: () => adminApi.questions({ q: debounced, size: 5 }), staleTime: 30_000,
  })

  const go = (to: string) => {
    onOpenChange(false)
    navigate(to)
  }

  const commands = useMemo<Command[]>(() => {
    const q = query.trim().toLowerCase()
    const pages = ALL_NAV_ITEMS
      .filter((i) => i.perms.length === 0 || any(...i.perms))
      .filter((i) => !q || `${i.label} ${i.keywords ?? ''}`.toLowerCase().includes(q))
      .map<Command>((i) => ({ id: `page:${i.to}`, group: 'Go to', label: i.label, hint: i.shortcut ? `g ${i.shortcut}` : undefined,
        icon: i.icon, run: () => go(i.to) }))
    const results: Command[] = []
    users.data?.content.slice(0, 5).forEach((u) => results.push({ id: `user:${u.id}`, group: 'Users', label: u.fullName,
      hint: u.email, icon: User, run: () => go(`/admin/users?q=${encodeURIComponent(u.email)}`) }))
    tests.data?.content.slice(0, 5).forEach((t) => results.push({ id: `test:${t.id}`, group: 'Tests', label: t.title,
      hint: t.status.toLowerCase(), icon: ListChecks, run: () => go(`/admin/tests/${t.id}`) }))
    questions.data?.content.slice(0, 5).forEach((qq) => results.push({ id: `q:${qq.id}`, group: 'Questions',
      label: (qq.textPreview ?? '(no text)').replace(/\$/g, '').slice(0, 80), hint: qq.topic.topicName,
      icon: FileQuestion, run: () => go(`/admin/questions/${qq.id}`) }))
    return [...pages, ...results]
  }, [query, users.data, tests.data, questions.data, any])

  useEffect(() => setActive((a) => Math.min(a, Math.max(0, commands.length - 1))), [commands.length])
  useEffect(() => {
    listRef.current?.querySelector(`[data-index="${active}"]`)?.scrollIntoView({ block: 'nearest' })
  }, [active])

  const onKey = (e: React.KeyboardEvent) => {
    if (e.key === 'ArrowDown') {
      e.preventDefault()
      setActive((a) => (a + 1) % Math.max(1, commands.length))
    } else if (e.key === 'ArrowUp') {
      e.preventDefault()
      setActive((a) => (a - 1 + commands.length) % Math.max(1, commands.length))
    } else if (e.key === 'Enter') {
      e.preventDefault()
      commands[active]?.run()
    }
  }

  let lastGroup = ''
  const loading = searching && (users.isFetching || tests.isFetching || questions.isFetching)
  return (
    <Dialog open={open} onOpenChange={onOpenChange}>
      <DialogContent className="top-[15%] translate-y-0 gap-0 overflow-hidden p-0 sm:max-w-xl" showCloseButton={false}
                     aria-describedby={undefined}>
        <DialogTitle className="sr-only">Command palette</DialogTitle>
        <div className="flex items-center gap-2 border-b px-4">
          <Search className="text-muted-foreground size-4 shrink-0" />
          <input autoFocus value={query} onChange={(e) => { setQuery(e.target.value); setActive(0) }} onKeyDown={onKey}
                 placeholder="Go to a page, or search users, tests, questions…" aria-label="Search"
                 role="combobox" aria-expanded="true" aria-controls="palette-list"
                 aria-activedescendant={commands[active] ? `palette-${commands[active].id}` : undefined}
                 className="h-12 flex-1 bg-transparent text-sm outline-none" />
          {loading && <span className="text-muted-foreground text-xs">Searching…</span>}
        </div>
        <ul id="palette-list" ref={listRef} role="listbox" className="max-h-96 overflow-y-auto p-2">
          {commands.length === 0 && (
            <li className="text-muted-foreground px-3 py-6 text-center text-sm">
              {searching ? 'No matches' : 'Type at least 2 characters to search records'}
            </li>
          )}
          {commands.map((c, i) => {
            const header = c.group !== lastGroup
            lastGroup = c.group
            return (
              <li key={c.id}>
                {header && <p className="text-muted-foreground px-3 pt-2 pb-1 text-xs font-semibold uppercase">{c.group}</p>}
                <button type="button" id={`palette-${c.id}`} role="option" aria-selected={i === active} data-index={i}
                        onMouseMove={() => setActive(i)} onClick={c.run}
                        className={cn('flex w-full items-center gap-3 rounded-md px-3 py-2 text-left text-sm',
                          i === active ? 'bg-accent text-accent-foreground' : '')}>
                  <c.icon className="text-muted-foreground size-4 shrink-0" />
                  <span className="min-w-0 flex-1 truncate">{c.label}</span>
                  {c.hint && <span className="text-muted-foreground shrink-0 truncate text-xs">{c.hint}</span>}
                  {i === active && <CornerDownLeft className="text-muted-foreground size-3.5 shrink-0" />}
                </button>
              </li>
            )
          })}
        </ul>
        <div className="text-muted-foreground flex gap-4 border-t px-4 py-2 text-xs">
          <span><kbd className="font-sans">↑↓</kbd> move</span><span><kbd className="font-sans">Enter</kbd> open</span>
          <span><kbd className="font-sans">Esc</kbd> close</span>
        </div>
      </DialogContent>
    </Dialog>
  )
}
