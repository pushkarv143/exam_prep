import { useState } from 'react'
import { Check, Copy, MessageCircleQuestion } from 'lucide-react'
import { toast } from 'sonner'
import { Button } from '@/components/ui/button'
import {
  Dialog, DialogContent, DialogDescription, DialogFooter, DialogHeader, DialogTitle,
} from '@/components/ui/dialog'
import { Textarea } from '@/components/ui/textarea'

/**
 * "Ask a doubt on this question" — pre-fills the question context so a student only types the
 * doubt. NOTE: there is no doubts endpoint yet, so this copies the message for the student to
 * send to their mentor. Wire the `onSubmit` to a real API when it exists (see backend gaps).
 */
export function AskDoubtButton({ testTitle, questionNumber, questionText }:
  { testTitle: string; questionNumber: number; questionText?: string }) {
  const [open, setOpen] = useState(false)
  const [copied, setCopied] = useState(false)
  const [doubt, setDoubt] = useState('')

  const context = `Doubt · ${testTitle} · Q${questionNumber}\n${questionText ? `Question: ${questionText.slice(0, 240)}\n` : ''}\nMy doubt: `

  const copy = async () => {
    try {
      await navigator.clipboard.writeText(context + doubt)
      setCopied(true)
      toast.success('Doubt copied — paste it to your mentor or the doubts group.')
      setTimeout(() => setCopied(false), 2000)
    } catch {
      toast.error('Could not copy. Select the text manually.')
    }
  }

  return (
    <>
      <Button variant="ghost" size="sm" className="h-7 px-2 text-xs" onClick={() => setOpen(true)}>
        <MessageCircleQuestion className="size-3.5" /> Ask a doubt
      </Button>
      <Dialog open={open} onOpenChange={setOpen}>
        <DialogContent>
          <DialogHeader>
            <DialogTitle>Ask a doubt · Q{questionNumber}</DialogTitle>
            <DialogDescription>Describe what confused you. We include the question context automatically.</DialogDescription>
          </DialogHeader>
          <Textarea rows={4} autoFocus placeholder="e.g. Why is option B wrong? I got confused between the two formulae…"
                    value={doubt} onChange={(e) => setDoubt(e.target.value)} />
          <DialogFooter>
            <Button variant="outline" onClick={() => setOpen(false)}>Close</Button>
            <Button onClick={() => void copy()} disabled={!doubt.trim()}>
              {copied ? <Check /> : <Copy />} Copy with context
            </Button>
          </DialogFooter>
        </DialogContent>
      </Dialog>
    </>
  )
}
