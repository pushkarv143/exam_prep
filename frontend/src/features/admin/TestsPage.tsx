import { useState } from 'react'
import { useForm } from 'react-hook-form'
import { zodResolver } from '@hookform/resolvers/zod'
import { z } from 'zod'
import { keepPreviousData, useMutation, useQuery } from '@tanstack/react-query'
import { Link, useNavigate } from 'react-router-dom'
import { BarChart3, Plus, Wrench } from 'lucide-react'
import { toast } from 'sonner'
import { adminApi, adminKeys, useAdminExams, useAllSeries, usePatterns } from '@/api/admin'
import { FormField } from '@/components/common/FormField'
import { Pagination } from '@/components/common/Pagination'
import { EmptyState, ErrorState } from '@/components/common/States'
import { PageHeader } from '@/components/layout/Layouts'
import { Badge } from '@/components/ui/badge'
import { Button } from '@/components/ui/button'
import { Card, CardContent } from '@/components/ui/card'
import { Checkbox } from '@/components/ui/checkbox'
import {
  Dialog, DialogContent, DialogDescription, DialogFooter, DialogHeader, DialogTitle,
} from '@/components/ui/dialog'
import { Input } from '@/components/ui/input'
import { NativeSelect } from '@/components/ui/native-select'
import { Skeleton } from '@/components/ui/skeleton'
import { errorMessage } from '@/lib/errors'
import { formatDateTime, formatDuration, formatNumber } from '@/lib/format'
import type { TestStatus } from '@/types/admin'
import { TEST_STATUS, titleCase } from './labels'

export default function TestsPage() {
  const [status, setStatus] = useState<TestStatus | ''>('')
  const [seriesId, setSeriesId] = useState('')
  const [q, setQ] = useState('')
  const [page, setPage] = useState(0)
  const [creating, setCreating] = useState(false)
  const series = useAllSeries()
  const params = { status: status || undefined, seriesId: seriesId || undefined, q: q.trim() || undefined, page, size: 20 }
  const tests = useQuery({ queryKey: adminKeys.tests(params), queryFn: () => adminApi.tests(params), placeholderData: keepPreviousData })
  const seriesName = (id?: string) => series.data?.content.find((s) => s.id === id)?.name

  return (
    <>
      <PageHeader title="Tests" description="Build, publish and analyse tests"
                  actions={<Button onClick={() => setCreating(true)}><Plus /> New test</Button>} />
      <div className="mb-4 grid gap-2 sm:grid-cols-[1fr_220px_180px]">
        <Input placeholder="Search by title…" value={q} onChange={(e) => { setQ(e.target.value); setPage(0) }} aria-label="Search tests" />
        <NativeSelect aria-label="Series" value={seriesId} onChange={(e) => { setSeriesId(e.target.value); setPage(0) }}>
          <option value="">All series</option>
          {series.data?.content.map((s) => <option key={s.id} value={s.id}>{s.name}</option>)}
        </NativeSelect>
        <NativeSelect aria-label="Status" value={status} onChange={(e) => { setStatus(e.target.value as TestStatus | ''); setPage(0) }}>
          <option value="">All statuses</option>
          {(Object.keys(TEST_STATUS) as TestStatus[]).map((s) => <option key={s} value={s}>{titleCase(s)}</option>)}
        </NativeSelect>
      </div>

      {tests.isError ? <ErrorState error={tests.error} onRetry={() => tests.refetch()} /> :
        tests.isPending ? <Skeleton className="h-80 rounded-xl" /> :
          tests.data.content.length === 0 ? <EmptyState title="No tests yet" description="Create a test to get started." /> : (
            <>
              <Card className="py-0">
                <CardContent className="overflow-x-auto px-0">
                  <table className="w-full text-sm">
                    <thead>
                      <tr className="text-muted-foreground border-b text-left">
                        <th className="px-4 py-3 font-medium">Test</th>
                        <th className="px-3 py-3 font-medium">Pattern</th>
                        <th className="px-3 py-3 font-medium">Questions</th>
                        <th className="px-3 py-3 font-medium">Window</th>
                        <th className="px-3 py-3 font-medium">Status</th>
                        <th className="px-4 py-3" />
                      </tr>
                    </thead>
                    <tbody>
                      {tests.data.content.map((t) => (
                        <tr key={t.id} className="hover:bg-muted/40 border-b last:border-0">
                          <td className="px-4 py-2.5">
                            <Link to={`/admin/tests/${t.id}`} className="font-medium hover:underline">{t.title}</Link>
                            <p className="text-muted-foreground text-xs">{seriesName(t.seriesId) ?? 'No series'}{t.free ? ' · free' : ''}</p>
                          </td>
                          <td className="px-3 py-2.5 whitespace-nowrap">{titleCase(t.pattern)}<p className="text-muted-foreground text-xs">{formatDuration(t.durationMinutes)}</p></td>
                          <td className="px-3 py-2.5 tabular-nums">{t.totalQuestions} · {formatNumber(t.totalMarks)} marks</td>
                          <td className="text-muted-foreground px-3 py-2.5 text-xs">
                            {t.startAt || t.endAt ? `${t.startAt ? formatDateTime(t.startAt) : 'now'} – ${t.endAt ? formatDateTime(t.endAt) : 'open'}` : 'Always open'}
                          </td>
                          <td className="px-3 py-2.5"><Badge variant={TEST_STATUS[t.status]}>{titleCase(t.status)}</Badge></td>
                          <td className="px-4 py-2.5">
                            <div className="flex justify-end gap-1">
                              <Button variant="ghost" size="sm" asChild><Link to={`/admin/tests/${t.id}`}><Wrench /> Build</Link></Button>
                              {t.status !== 'DRAFT' && <Button variant="ghost" size="sm" asChild><Link to={`/admin/tests/${t.id}/stats`}><BarChart3 /> Stats</Link></Button>}
                            </div>
                          </td>
                        </tr>
                      ))}
                    </tbody>
                  </table>
                </CardContent>
              </Card>
              <div className="mt-4"><Pagination page={tests.data.page} totalPages={tests.data.totalPages} onChange={setPage} /></div>
            </>
          )}
      <CreateTestDialog open={creating} onOpenChange={setCreating} />
    </>
  )
}

const createSchema = z.object({
  title: z.string().trim().min(3, 'Enter a title').max(250),
  examId: z.string().min(1, 'Choose an exam'),
  seriesId: z.string(),
  pattern: z.enum(['JEE_MAIN', 'JEE_ADVANCED', 'NEET', 'CUSTOM']),
  durationMinutes: z.string().trim().refine((v) => v === '' || (/^\d+$/.test(v) && +v >= 1 && +v <= 600), '1 to 600 minutes'),
  free: z.boolean(),
})
type CreateValues = z.infer<typeof createSchema>

function CreateTestDialog({ open, onOpenChange }: { open: boolean; onOpenChange: (o: boolean) => void }) {
  const navigate = useNavigate()
  const exams = useAdminExams()
  const series = useAllSeries()
  const patterns = usePatterns()
  const form = useForm<CreateValues>({
    resolver: zodResolver(createSchema),
    defaultValues: { title: '', examId: '', seriesId: '', pattern: 'JEE_MAIN', durationMinutes: '', free: false },
  })
  const { register, handleSubmit, watch, formState: { errors } } = form
  const examId = watch('examId')
  const pattern = patterns.data?.find((p) => p.code === watch('pattern'))

  const create = useMutation({
    mutationFn: (v: CreateValues) => adminApi.createTest({
      title: v.title, examId: v.examId, seriesId: v.seriesId || null, pattern: v.pattern,
      durationMinutes: v.durationMinutes ? Number(v.durationMinutes) : null, free: v.free,
      shuffleQuestions: false, shuffleOptions: false, displayOrder: 0,
    }),
    onSuccess: (d) => {
      toast.success('Test created')
      onOpenChange(false)
      form.reset()
      navigate(`/admin/tests/${d.test.id}`)
    },
    onError: (e) => toast.error(errorMessage(e)),
  })

  return (
    <Dialog open={open} onOpenChange={(o) => !create.isPending && onOpenChange(o)}>
      <DialogContent>
        <DialogHeader>
          <DialogTitle>New test</DialogTitle>
          <DialogDescription>Standard patterns create their sections automatically. You add questions next.</DialogDescription>
        </DialogHeader>
        <form id="create-test" className="grid gap-4" onSubmit={handleSubmit((v) => create.mutate(v))} noValidate>
          <FormField id="t-title" label="Title" error={errors.title}>
            <Input id="t-title" {...register('title')} aria-invalid={!!errors.title} placeholder="JEE Main Full Mock 1" />
          </FormField>
          <div className="grid gap-4 sm:grid-cols-2">
            <FormField id="t-exam" label="Exam" error={errors.examId}>
              <NativeSelect id="t-exam" {...register('examId')} aria-invalid={!!errors.examId}>
                <option value="">Select exam</option>
                {exams.data?.map((e) => <option key={e.id} value={e.id}>{e.name}</option>)}
              </NativeSelect>
            </FormField>
            <FormField id="t-pattern" label="Pattern">
              <NativeSelect id="t-pattern" {...register('pattern')}>
                {(patterns.data ?? []).map((p) => <option key={p.code} value={p.code}>{p.displayName}</option>)}
              </NativeSelect>
            </FormField>
          </div>
          {pattern && pattern.code !== 'CUSTOM' && (
            <p className="text-muted-foreground -mt-2 text-xs">
              {pattern.totalQuestions} questions · {formatNumber(pattern.totalMarks)} marks · {pattern.sections.length} sections
            </p>
          )}
          <div className="grid gap-4 sm:grid-cols-2">
            <FormField id="t-series" label="Series (optional)">
              <NativeSelect id="t-series" {...register('seriesId')}>
                <option value="">None</option>
                {series.data?.content.filter((s) => !examId || s.examId === examId).map((s) => <option key={s.id} value={s.id}>{s.name}</option>)}
              </NativeSelect>
            </FormField>
            <FormField id="t-duration" label="Duration (minutes)" error={errors.durationMinutes}
                       hint={pattern?.defaultDurationMinutes ? `Blank = ${pattern.defaultDurationMinutes}` : undefined}>
              <Input id="t-duration" inputMode="numeric" {...register('durationMinutes')} aria-invalid={!!errors.durationMinutes} />
            </FormField>
          </div>
          <label className="flex items-center gap-2 text-sm"><Checkbox {...register('free')} /> Free sample test (open to everyone)</label>
        </form>
        <DialogFooter>
          <Button variant="outline" onClick={() => onOpenChange(false)} disabled={create.isPending}>Cancel</Button>
          <Button type="submit" form="create-test" loading={create.isPending}>Create</Button>
        </DialogFooter>
      </DialogContent>
    </Dialog>
  )
}
