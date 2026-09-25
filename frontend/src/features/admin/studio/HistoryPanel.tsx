import { useState } from 'react'
import { useMutation, useQueries, useQuery, useQueryClient } from '@tanstack/react-query'
import { GitCompare, RotateCcw } from 'lucide-react'
import { toast } from 'sonner'
import { adminKeys } from '@/api/admin'
import { contentApi, contentKeys } from '@/api/content'
import { useConfirm } from '@/components/common/ConfirmDialog'
import { MathText } from '@/components/common/MathText'
import { Badge } from '@/components/ui/badge'
import { Button } from '@/components/ui/button'
import { Checkbox } from '@/components/ui/checkbox'
import { Dialog, DialogContent, DialogDescription, DialogHeader, DialogTitle } from '@/components/ui/dialog'
import { Skeleton } from '@/components/ui/skeleton'
import { errorMessage } from '@/lib/errors'
import { formatDateTime, formatRelative } from '@/lib/format'
import { cn } from '@/lib/utils'
import { wordDiff } from '@/lib/wordDiff'
import type { Question } from '@/types/admin'
import { compareVersions } from '../components/versionFields'

const FIELD_LABEL: Record<string, string> = {
  'content.text': 'question', 'content.options': 'options', 'content.paragraph': 'passage', 'content.solution': 'solution',
  'content.images': 'figures', 'content.matchLeft': 'column I', 'content.matchRight': 'column II',
  'content.shuffleOptions': 'shuffling', 'content.numericFormat': 'answer format', answerKey: 'answer key',
  'translations.HI': 'Hindi', 'translations.EN': 'English', type: 'type', difficulty: 'difficulty', marks: 'marks',
  negativeMarks: 'negative marks', topicId: 'topic', subTopic: 'sub-topic', tags: 'tags', concepts: 'concepts',
  sourceType: 'source', source: 'source', year: 'year', pyqShift: 'shift', expectedTimeSec: 'expected time',
  cognitiveLevel: 'cognitive level', language: 'language',
}

/** Version list with "compare" and "restore". Tests pin versions, so this is also the audit trail of content. */
export function HistoryPanel({ question, onRestored }: { question: Question; onRestored: (q: Question) => void }) {
  const qc = useQueryClient()
  const confirm = useConfirm()
  const versions = useQuery({ queryKey: contentKeys.versions(question.id), queryFn: () => contentApi.versions(question.id) })
  const [picked, setPicked] = useState<number[]>([])
  const [compare, setCompare] = useState<[number, number] | null>(null)
  const canRestore = question.actions.includes('EDIT')

  const restore = useMutation({
    mutationFn: ({ v, note }: { v: number; note: string }) => contentApi.restoreVersion(question.id, v, note),
    onSuccess: (q) => {
      qc.setQueryData(adminKeys.question(q.id), q)
      void qc.invalidateQueries({ queryKey: contentKeys.versions(q.id) })
      void qc.invalidateQueries({ queryKey: contentKeys.activity(q.id) })
      toast.success(`Saved as v${q.currentVersion}. Publish it to make it live.`)
      onRestored(q)
    },
    onError: (e) => toast.error(errorMessage(e)),
  })
  const toggle = (v: number) => setPicked((p) => (p.includes(v) ? p.filter((x) => x !== v) : [...p.slice(-1), v]))

  if (versions.isPending) return <Skeleton className="h-40" />
  return (
    <div className="space-y-3">
      <div className="flex items-center justify-between gap-2">
        <p className="text-muted-foreground text-xs">Tick two versions to compare them.</p>
        <Button size="sm" variant="outline" disabled={picked.length !== 2}
                onClick={() => setCompare([Math.min(...picked), Math.max(...picked)] as [number, number])}>
          <GitCompare /> Compare
        </Button>
      </div>
      <ol className="space-y-2">
        {versions.data?.map((v) => (
          <li key={v.version} className={cn('rounded-lg border p-3 text-sm', v.current && 'border-primary/50')}>
            <div className="flex items-start gap-3">
              <Checkbox className="mt-0.5" checked={picked.includes(v.version)} onCheckedChange={() => toggle(v.version)}
                        aria-label={`Select v${v.version}`} />
              <div className="min-w-0 flex-1">
                <p className="flex flex-wrap items-center gap-1.5">
                  <strong>v{v.version}</strong>
                  {v.live && <Badge variant="success">Live</Badge>}
                  {v.current && !v.live && <Badge variant="muted">Working copy</Badge>}
                  {v.restoredFrom != null && <Badge variant="outline">from v{v.restoredFrom}</Badge>}
                  <span className="text-muted-foreground" title={formatDateTime(v.createdAt)}>
                    {v.createdByName ?? 'Someone'} · {formatRelative(v.createdAt)}
                  </span>
                </p>
                {v.changeNote && <p className="mt-0.5">{v.changeNote}</p>}
                {v.changedFields.length > 0 && (
                  <p className="text-muted-foreground mt-1 text-xs">Changed: {v.changedFields.map((f) => FIELD_LABEL[f] ?? f).join(', ')}</p>
                )}
                {v.publishedAt && (
                  <p className="text-muted-foreground text-xs">Published {formatDateTime(v.publishedAt)}{v.publishedByName ? ` by ${v.publishedByName}` : ''}</p>
                )}
              </div>
              <div className="flex shrink-0 flex-col gap-1">
                {!v.current && (
                  <Button size="sm" variant="ghost" onClick={() => setCompare([v.version, question.currentVersion])}>
                    Diff
                  </Button>
                )}
                {!v.current && canRestore && (
                  <Button size="sm" variant="ghost" disabled={restore.isPending}
                          onClick={async () => {
                            const r = await confirm({ title: `Restore v${v.version}?`, reason: 'optional', confirmText: 'Restore',
                              description: `Its content is saved as a new version (v${question.currentVersion + 1}). Nothing is deleted, and tests keep their versions.`,
                              reasonPlaceholder: 'Why? (saved as the change note)' })
                            if (r) restore.mutate({ v: v.version, note: r.reason })
                          }}>
                    <RotateCcw /> Restore
                  </Button>
                )}
              </div>
            </div>
          </li>
        ))}
      </ol>
      {compare && <VersionCompare questionId={question.id} from={compare[0]} to={compare[1]} onClose={() => setCompare(null)} />}
    </div>
  )
}

export function VersionCompare({ questionId, from, to, onClose }: { questionId: string; from: number; to: number; onClose: () => void }) {
  const [a, b] = useQueries({
    queries: [from, to].map((v) => ({ queryKey: contentKeys.version(questionId, v), queryFn: () => contentApi.version(questionId, v),
      staleTime: Infinity })),
  })
  const [onlyChanges, setOnlyChanges] = useState(true)
  const [rendered, setRendered] = useState(false)
  const rows = a.data && b.data ? compareVersions(a.data.snapshot, b.data.snapshot) : []
  const shown = onlyChanges ? rows.filter((r) => r.changed) : rows

  return (
    <Dialog open onOpenChange={(o) => !o && onClose()}>
      <DialogContent className="max-h-[90vh] overflow-y-auto sm:max-w-4xl">
        <DialogHeader>
          <DialogTitle>v{from} → v{to}</DialogTitle>
          <DialogDescription>
            <span className="bg-destructive/15 text-destructive rounded px-1 line-through">removed</span>{' '}
            <span className="bg-success/15 text-success rounded px-1">added</span>
          </DialogDescription>
        </DialogHeader>
        <div className="flex flex-wrap gap-4 text-sm">
          <label className="flex items-center gap-2"><Checkbox checked={onlyChanges} onCheckedChange={(v) => setOnlyChanges(v === true)} /> Only changed fields</label>
          <label className="flex items-center gap-2"><Checkbox checked={rendered} onCheckedChange={(v) => setRendered(v === true)} /> Show rendered maths</label>
        </div>
        {!a.data || !b.data ? <Skeleton className="h-60" /> : shown.length === 0 ? (
          <p className="text-muted-foreground py-6 text-center text-sm">No differences.</p>
        ) : (
          <table className="w-full text-sm">
            <tbody>
              {shown.map((r) => (
                <tr key={r.key} className="border-b align-top last:border-0">
                  <th className="text-muted-foreground w-40 py-2 pr-3 text-left font-medium">{r.label}</th>
                  {rendered && r.rich ? (
                    <>
                      <td className="w-1/2 py-2 pr-3"><MathText text={r.before || '—'} className="bg-destructive/5 rounded p-2" /></td>
                      <td className="w-1/2 py-2"><MathText text={r.after || '—'} className="bg-success/5 rounded p-2" /></td>
                    </>
                  ) : (
                    <td colSpan={2} className="py-2 font-mono text-[13px] break-words whitespace-pre-wrap">
                      {r.changed ? wordDiff(r.before, r.after).map((p, i) => (
                        <span key={i} className={cn(p.type === 'added' && 'bg-success/15 text-success',
                          p.type === 'removed' && 'bg-destructive/15 text-destructive line-through')}>{p.text}</span>
                      )) : r.after}
                    </td>
                  )}
                </tr>
              ))}
            </tbody>
          </table>
        )}
      </DialogContent>
    </Dialog>
  )
}
