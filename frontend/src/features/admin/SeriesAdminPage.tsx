import { useEffect, useState } from 'react'
import { useForm } from 'react-hook-form'
import { zodResolver } from '@hookform/resolvers/zod'
import { z } from 'zod'
import { keepPreviousData, useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { Link } from 'react-router-dom'
import { ExternalLink, Pencil, Plus } from 'lucide-react'
import { toast } from 'sonner'
import { adminApi, adminKeys, useAdminExams } from '@/api/admin'
import { FormField } from '@/components/common/FormField'
import { Pagination } from '@/components/common/Pagination'
import { EmptyState, ErrorState } from '@/components/common/States'
import { PageHeader } from '@/components/layout/Layouts'
import { Badge } from '@/components/ui/badge'
import { Button } from '@/components/ui/button'
import { Card, CardContent } from '@/components/ui/card'
import { Checkbox } from '@/components/ui/checkbox'
import {
  Dialog, DialogContent, DialogFooter, DialogHeader, DialogTitle,
} from '@/components/ui/dialog'
import { Input } from '@/components/ui/input'
import { NativeSelect } from '@/components/ui/native-select'
import { Skeleton } from '@/components/ui/skeleton'
import { Textarea } from '@/components/ui/textarea'
import { errorMessage } from '@/lib/errors'
import { formatDate, formatNumber, formatPrice } from '@/lib/format'
import type { AdminSeries, SeriesStatus } from '@/types/admin'
import { useImageUpload } from './components/ImageUpload'
import { SERIES_STATUS, titleCase } from './labels'
import { toLocalInput } from './components/TestSettingsDialog'

export default function SeriesAdminPage() {
  const qc = useQueryClient()
  const exams = useAdminExams()
  const [status, setStatus] = useState<SeriesStatus | ''>('')
  const [q, setQ] = useState('')
  const [page, setPage] = useState(0)
  const [editing, setEditing] = useState<AdminSeries | 'new' | null>(null)
  const params = { status: status || undefined, q: q.trim() || undefined, page, size: 20 }
  const series = useQuery({ queryKey: adminKeys.series(params), queryFn: () => adminApi.series(params), placeholderData: keepPreviousData })
  const examName = (id: string) => exams.data?.find((e) => e.id === id)?.name ?? ''

  const action = useMutation({
    mutationFn: ({ id, a }: { id: string; a: 'publish' | 'unpublish' | 'archive' }) => adminApi.seriesAction(id, a),
    onSuccess: () => { toast.success('Series updated'); void qc.invalidateQueries({ queryKey: ['admin', 'series'] }) },
    onError: (e) => toast.error(errorMessage(e)),
  })

  return (
    <>
      <PageHeader title="Test series" description="Packages students enroll in or buy"
                  actions={<Button onClick={() => setEditing('new')}><Plus /> New series</Button>} />
      <div className="mb-4 grid gap-2 sm:grid-cols-[1fr_180px]">
        <Input placeholder="Search by name…" value={q} onChange={(e) => { setQ(e.target.value); setPage(0) }} aria-label="Search series" />
        <NativeSelect aria-label="Status" value={status} onChange={(e) => { setStatus(e.target.value as SeriesStatus | ''); setPage(0) }}>
          <option value="">All statuses</option>
          {(Object.keys(SERIES_STATUS) as SeriesStatus[]).map((s) => <option key={s} value={s}>{titleCase(s)}</option>)}
        </NativeSelect>
      </div>

      {series.isError ? <ErrorState error={series.error} onRetry={() => series.refetch()} /> :
        series.isPending ? <Skeleton className="h-72 rounded-xl" /> :
          series.data.content.length === 0 ? <EmptyState title="No series" description="Create a series and add tests to it." /> : (
            <>
              <Card className="py-0">
                <CardContent className="overflow-x-auto px-0">
                  <table className="w-full text-sm">
                    <thead>
                      <tr className="text-muted-foreground border-b text-left">
                        <th className="px-4 py-3 font-medium">Series</th>
                        <th className="px-3 py-3 font-medium">Price</th>
                        <th className="px-3 py-3 font-medium">Tests</th>
                        <th className="px-3 py-3 font-medium">Enrolled</th>
                        <th className="px-3 py-3 font-medium">Status</th>
                        <th className="px-4 py-3" />
                      </tr>
                    </thead>
                    <tbody>
                      {series.data.content.map((s) => (
                        <tr key={s.id} className="hover:bg-muted/40 border-b last:border-0">
                          <td className="px-4 py-2.5">
                            <p className="font-medium">{s.name}</p>
                            <p className="text-muted-foreground text-xs">
                              {examName(s.examId)}{s.batchRestricted ? ' · batch only' : ''} · created {formatDate(s.createdAt)}
                            </p>
                          </td>
                          <td className="px-3 py-2.5 tabular-nums">{s.free ? <Badge variant="success">Free</Badge> : formatPrice(s.price)}</td>
                          <td className="px-3 py-2.5 tabular-nums">{s.testCount}</td>
                          <td className="px-3 py-2.5 tabular-nums">{formatNumber(s.enrollmentCount, 0)}</td>
                          <td className="px-3 py-2.5"><Badge variant={SERIES_STATUS[s.status]}>{titleCase(s.status)}</Badge></td>
                          <td className="px-4 py-2.5">
                            <div className="flex justify-end gap-1">
                              {s.status === 'PUBLISHED' && (
                                <Button variant="ghost" size="icon" asChild aria-label="View public page">
                                  <Link to={`/series/${s.slug}`}><ExternalLink /></Link>
                                </Button>
                              )}
                              <Button variant="ghost" size="icon" aria-label="Edit" onClick={() => setEditing(s)}><Pencil /></Button>
                              {s.status === 'DRAFT' && <Button size="sm" variant="outline" disabled={action.isPending} onClick={() => action.mutate({ id: s.id, a: 'publish' })}>Publish</Button>}
                              {s.status === 'PUBLISHED' && <Button size="sm" variant="outline" disabled={action.isPending} onClick={() => action.mutate({ id: s.id, a: 'unpublish' })}>Unpublish</Button>}
                              {s.status !== 'ARCHIVED' && (
                                <Button size="sm" variant="ghost" disabled={action.isPending}
                                        onClick={() => { if (confirm('Archive this series? Enrolled students keep access.')) action.mutate({ id: s.id, a: 'archive' }) }}>Archive</Button>
                              )}
                            </div>
                          </td>
                        </tr>
                      ))}
                    </tbody>
                  </table>
                </CardContent>
              </Card>
              <div className="mt-4"><Pagination page={series.data.page} totalPages={series.data.totalPages} onChange={setPage} /></div>
            </>
          )}
      <SeriesDialog series={editing === 'new' ? undefined : editing ?? undefined} open={editing !== null} onOpenChange={(o) => !o && setEditing(null)} />
    </>
  )
}

const schema = z.object({
  examId: z.string().min(1, 'Choose an exam'),
  name: z.string().trim().min(3, 'Enter a name').max(200),
  description: z.string().max(10_000),
  thumbnailUrl: z.string().max(500),
  free: z.boolean(),
  price: z.string().trim(),
  validityDays: z.string().trim().refine((v) => v === '' || (/^\d+$/.test(v) && +v >= 1 && +v <= 3650), '1 to 3650 days'),
  validUntil: z.string(),
  batchRestricted: z.boolean(),
  batchIds: z.array(z.string()),
}).superRefine((v, ctx) => {
  if (!v.free && !(/^\d+(\.\d{1,2})?$/.test(v.price) && +v.price > 0 && +v.price <= 100_000)) {
    ctx.addIssue({ code: 'custom', path: ['price'], message: 'Enter a price from ₹1 to ₹1,00,000' })
  }
  if (v.batchRestricted && v.batchIds.length === 0) {
    ctx.addIssue({ code: 'custom', path: ['batchIds'], message: 'Choose at least one batch' })
  }
})
type Values = z.infer<typeof schema>

function SeriesDialog({ series, open, onOpenChange }: { series?: AdminSeries; open: boolean; onOpenChange: (o: boolean) => void }) {
  const qc = useQueryClient()
  const exams = useAdminExams()
  const batches = useQuery({ queryKey: ['admin', 'batches'], queryFn: adminApi.batches, enabled: open })
  const { upload, uploading } = useImageUpload()
  const values = (): Values => ({
    examId: series?.examId ?? '', name: series?.name ?? '', description: series?.description ?? '',
    thumbnailUrl: series?.thumbnailUrl ?? '', free: series?.free ?? false, price: series ? String(series.price) : '',
    validityDays: series?.validityDays ? String(series.validityDays) : '', validUntil: toLocalInput(series?.validUntil),
    batchRestricted: series?.batchRestricted ?? false, batchIds: series?.batchIds ?? [],
  })
  const form = useForm<Values>({ resolver: zodResolver(schema), defaultValues: values() })
  const { register, handleSubmit, reset, watch, setValue, formState: { errors } } = form
  useEffect(() => { if (open) reset(values()) }, [open, series?.id])
  const free = watch('free')
  const restricted = watch('batchRestricted')
  const batchIds = watch('batchIds')
  const thumbnail = watch('thumbnailUrl')

  const save = useMutation({
    mutationFn: (v: Values) => {
      const body = {
        examId: v.examId, name: v.name, description: v.description || undefined, thumbnailUrl: v.thumbnailUrl || undefined,
        free: v.free, price: v.free ? 0 : Number(v.price), validityDays: v.validityDays ? Number(v.validityDays) : null,
        validUntil: v.validUntil ? new Date(v.validUntil).toISOString() : null,
        batchRestricted: v.batchRestricted, batchIds: v.batchRestricted ? v.batchIds : [],
      }
      return series ? adminApi.updateSeries(series.id, body) : adminApi.createSeries(body)
    },
    onSuccess: () => {
      toast.success(series ? 'Series saved' : 'Series created')
      void qc.invalidateQueries({ queryKey: ['admin', 'series'] })
      onOpenChange(false)
    },
    onError: (e) => toast.error(errorMessage(e)),
  })

  return (
    <Dialog open={open} onOpenChange={(o) => !save.isPending && onOpenChange(o)}>
      <DialogContent className="max-h-[90vh] overflow-y-auto sm:max-w-xl">
        <DialogHeader><DialogTitle>{series ? 'Edit series' : 'New series'}</DialogTitle></DialogHeader>
        <form id="series-form" className="grid gap-4" onSubmit={handleSubmit((v) => save.mutate(v))} noValidate>
          <FormField id="sr-exam" label="Exam" error={errors.examId}>
            <NativeSelect id="sr-exam" {...register('examId')} aria-invalid={!!errors.examId}>
              <option value="">Select exam</option>
              {exams.data?.map((e) => <option key={e.id} value={e.id}>{e.name}</option>)}
            </NativeSelect>
          </FormField>
          <FormField id="sr-name" label="Name" error={errors.name}>
            <Input id="sr-name" {...register('name')} aria-invalid={!!errors.name} placeholder="JEE Main 2027 Full Test Series" />
          </FormField>
          <FormField id="sr-desc" label="Description">
            <Textarea id="sr-desc" rows={3} {...register('description')} />
          </FormField>
          <div className="grid gap-2">
            <span className="text-sm font-medium">Thumbnail</span>
            <div className="flex items-center gap-3">
              {thumbnail && <img src={thumbnail} alt="" className="h-16 w-28 rounded border object-cover" />}
              <label className="border-input hover:bg-accent inline-flex h-8 cursor-pointer items-center rounded-md border px-3 text-sm">
                {uploading ? 'Uploading…' : thumbnail ? 'Replace' : 'Upload image'}
                <input type="file" accept="image/png,image/jpeg,image/webp" className="sr-only" disabled={uploading}
                       onChange={async (e) => {
                         const f = e.target.files?.[0]
                         e.target.value = ''
                         if (!f) return
                         const url = await upload(f)
                         if (url) setValue('thumbnailUrl', url, { shouldDirty: true })
                       }} />
              </label>
              {thumbnail && <Button type="button" variant="ghost" size="sm" onClick={() => setValue('thumbnailUrl', '')}>Remove</Button>}
            </div>
          </div>
          <label className="flex items-center gap-2 text-sm"><Checkbox {...register('free')} /> Free series</label>
          <div className="grid gap-4 sm:grid-cols-3">
            {!free && (
              <FormField id="sr-price" label="Price (₹)" error={errors.price}>
                <Input id="sr-price" inputMode="decimal" {...register('price')} aria-invalid={!!errors.price} />
              </FormField>
            )}
            <FormField id="sr-days" label="Access (days)" error={errors.validityDays} hint="Blank = no expiry">
              <Input id="sr-days" inputMode="numeric" {...register('validityDays')} />
            </FormField>
            <FormField id="sr-until" label="Or access until">
              <Input id="sr-until" type="datetime-local" {...register('validUntil')} />
            </FormField>
          </div>
          <label className="flex items-center gap-2 text-sm"><Checkbox {...register('batchRestricted')} /> Only for students in selected batches</label>
          {restricted && (
            <div className="grid gap-2">
              <div className="grid max-h-40 gap-1 overflow-y-auto rounded-md border p-2 text-sm">
                {batches.data?.content.length ? batches.data.content.map((b) => (
                  <label key={b.id} className="flex items-center gap-2">
                    <Checkbox checked={batchIds.includes(b.id)}
                              onCheckedChange={(v) => setValue('batchIds', v ? [...batchIds, b.id] : batchIds.filter((x) => x !== b.id), { shouldDirty: true, shouldValidate: true })} />
                    {b.name} <span className="text-muted-foreground text-xs">({b.code} · {b.memberCount} students)</span>
                  </label>
                )) : <p className="text-muted-foreground">No batches yet.</p>}
              </div>
              {errors.batchIds && <p className="text-destructive text-sm">{errors.batchIds.message}</p>}
            </div>
          )}
        </form>
        <DialogFooter>
          <Button variant="outline" onClick={() => onOpenChange(false)} disabled={save.isPending}>Cancel</Button>
          <Button type="submit" form="series-form" loading={save.isPending}>Save</Button>
        </DialogFooter>
      </DialogContent>
    </Dialog>
  )
}
