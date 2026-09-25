import { useCallback, useEffect, useRef, useState } from 'react'
import type { UseFormReturn } from 'react-hook-form'
import { useBlocker } from 'react-router-dom'
import { useConfirm } from '@/components/common/ConfirmDialog'
import type { QuestionFormValues } from '../questionForm'

interface StoredDraft {
  values: QuestionFormValues
  baseVersion: number | null
  savedAt: string
}

const key = (userId: string, questionId?: string) => `examprep-studio:${userId}:${questionId ?? 'new'}`

/**
 * Autosaves unsaved editor changes to this browser (every second while dirty) and offers
 * them back on the next visit. Server versions are created only by an explicit save, so
 * the version history stays meaningful.
 */
export function useStudioDraft(form: UseFormReturn<QuestionFormValues>, userId: string | undefined,
                               questionId: string | undefined, baseVersion: number | null) {
  const storageKey = userId ? key(userId, questionId) : null
  const [pending, setPending] = useState<StoredDraft | null>(() => {
    if (!storageKey) return null
    try {
      const raw = localStorage.getItem(storageKey)
      return raw ? (JSON.parse(raw) as StoredDraft) : null
    } catch {
      return null
    }
  })
  const offered = useRef(pending !== null)

  const timer = useRef<number | undefined>(undefined)
  useEffect(() => {
    if (!storageKey) return
    const write = () => {
      try {
        const draft: StoredDraft = { values: form.getValues(), baseVersion, savedAt: new Date().toISOString() }
        localStorage.setItem(storageKey, JSON.stringify(draft))
      } catch {
        // storage full or blocked: the unsaved-changes guard still protects the user
      }
    }
    // While an older draft is on offer, do not overwrite it.
    const sub = form.watch(() => {
      if (offered.current || !form.formState.isDirty) return
      window.clearTimeout(timer.current)
      timer.current = window.setTimeout(write, 1000)
    })
    return () => {
      sub.unsubscribe()
      window.clearTimeout(timer.current)
    }
  }, [storageKey, form, baseVersion])

  const clear = useCallback(() => {
    if (storageKey) {
      try {
        localStorage.removeItem(storageKey)
      } catch {
        // ignore
      }
    }
    offered.current = false
    setPending(null)
  }, [storageKey])

  const restore = useCallback(() => {
    if (!pending) return
    form.reset(pending.values, { keepDefaultValues: true })
    offered.current = false
    setPending(null)
  }, [pending, form])

  /** The stored draft was made from an older version than the one now on the server. */
  const outdated = !!pending && pending.baseVersion != null && baseVersion != null && pending.baseVersion !== baseVersion
  return { pending, outdated, restore, discard: clear, clear }
}

/**
 * Asks before leaving the editor with unsaved changes (in-app navigation and tab close).
 * Set `allowNext.current = true` right before a navigation that follows a save.
 */
export function useUnsavedGuard(dirty: boolean) {
  const confirm = useConfirm()
  const allowNext = useRef(false)
  const blocker = useBlocker(({ currentLocation, nextLocation }) => {
    if (allowNext.current) {
      allowNext.current = false
      return false
    }
    return dirty && currentLocation.pathname !== nextLocation.pathname
  })
  useEffect(() => {
    if (blocker.state !== 'blocked') return
    void confirm({
      title: 'Leave without saving?',
      description: 'Your changes are kept as an unsaved draft in this browser, and offered again when you come back.',
      confirmText: 'Leave', reason: false,
    }).then((ok) => (ok ? blocker.proceed() : blocker.reset()))
  }, [blocker, confirm])
  useEffect(() => {
    const warn = (e: BeforeUnloadEvent) => { if (dirty) e.preventDefault() }
    window.addEventListener('beforeunload', warn)
    return () => window.removeEventListener('beforeunload', warn)
  }, [dirty])
  return allowNext
}
