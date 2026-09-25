import { useEffect, useRef, useState } from 'react'
import { create } from 'zustand'
import { AlertTriangle } from 'lucide-react'
import { Button } from '@/components/ui/button'
import {
  Dialog, DialogContent, DialogDescription, DialogFooter, DialogHeader, DialogTitle,
} from '@/components/ui/dialog'
import { Label } from '@/components/ui/label'
import { Textarea } from '@/components/ui/textarea'

export interface ConfirmOptions {
  title: string
  description?: string
  confirmText?: string
  destructive?: boolean
  /** 'required' blocks the button until a reason is typed; it is stored in the audit log. */
  reason?: 'required' | 'optional' | false
  reasonPlaceholder?: string
}

export interface ConfirmResult {
  reason: string
}

interface State {
  open: boolean
  options: ConfirmOptions | null
  resolve: ((r: ConfirmResult | null) => void) | null
  ask: (o: ConfirmOptions) => Promise<ConfirmResult | null>
  close: (r: ConfirmResult | null) => void
}

const useConfirmStore = create<State>((set, get) => ({
  open: false,
  options: null,
  resolve: null,
  ask: (options) => new Promise((resolve) => {
    get().resolve?.(null)   // an older dialog still open is treated as cancelled
    set({ open: true, options, resolve })
  }),
  close: (r) => {
    get().resolve?.(r)
    set({ open: false, resolve: null })
  },
}))

/**
 * Promise-based confirmation, used before every destructive admin action:
 * <pre>
 * const confirm = useConfirm()
 * const ok = await confirm({ title: 'Archive test?', destructive: true, reason: 'required' })
 * if (ok) mutate({ reason: ok.reason })
 * </pre>
 * Resolves to null when the user cancels.
 */
export function useConfirm() {
  return useConfirmStore((s) => s.ask)
}

/** Mount once near the root. */
export function ConfirmHost() {
  const { open, options, close } = useConfirmStore()
  const [reason, setReason] = useState('')
  const ref = useRef<HTMLTextAreaElement>(null)
  useEffect(() => {
    if (open) setReason('')
  }, [open, options])
  if (!options) return null
  const mode = options.reason ?? 'optional'
  const blocked = mode === 'required' && reason.trim().length < 3

  return (
    <Dialog open={open} onOpenChange={(o) => !o && close(null)}>
      <DialogContent className="sm:max-w-md" onOpenAutoFocus={(e) => {
        if (mode) {
          e.preventDefault()
          ref.current?.focus()
        }
      }}>
        <DialogHeader>
          <DialogTitle className="flex items-center gap-2">
            {options.destructive && <AlertTriangle className="text-destructive size-5" />}
            {options.title}
          </DialogTitle>
          {options.description && <DialogDescription>{options.description}</DialogDescription>}
        </DialogHeader>
        {mode && (
          <div className="grid gap-2">
            <Label htmlFor="confirm-reason">
              Reason {mode === 'required' ? <span className="text-destructive">*</span> : <span className="text-muted-foreground">(optional)</span>}
            </Label>
            <Textarea id="confirm-reason" ref={ref} rows={3} value={reason} maxLength={500}
                      placeholder={options.reasonPlaceholder ?? 'Why? This is saved in the audit log.'}
                      onChange={(e) => setReason(e.target.value)}
                      onKeyDown={(e) => {
                        if (e.key === 'Enter' && (e.ctrlKey || e.metaKey) && !blocked) close({ reason: reason.trim() })
                      }} />
          </div>
        )}
        <DialogFooter>
          <Button variant="outline" onClick={() => close(null)}>Cancel</Button>
          <Button variant={options.destructive ? 'destructive' : 'default'} disabled={blocked}
                  onClick={() => close({ reason: reason.trim() })}>
            {options.confirmText ?? 'Confirm'}
          </Button>
        </DialogFooter>
      </DialogContent>
    </Dialog>
  )
}
