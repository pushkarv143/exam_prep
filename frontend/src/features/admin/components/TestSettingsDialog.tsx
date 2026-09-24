import { useEffect } from 'react'
import { useForm } from 'react-hook-form'
import { zodResolver } from '@hookform/resolvers/zod'
import { z } from 'zod'
import { useMutation, useQueryClient } from '@tanstack/react-query'
import { toast } from 'sonner'
import { adminApi, useAllSeries } from '@/api/admin'
import { FormField } from '@/components/common/FormField'
import { Button } from '@/components/ui/button'
import { Checkbox } from '@/components/ui/checkbox'
import {
  Dialog, DialogContent, DialogFooter, DialogHeader, DialogTitle,
} from '@/components/ui/dialog'
import { Input } from '@/components/ui/input'
import { NativeSelect } from '@/components/ui/native-select'
import { Textarea } from '@/components/ui/textarea'
import { errorMessage } from '@/lib/errors'
import type { AdminTest } from '@/types/admin'

const int = (min: number, max: number) =>
  z.string().trim().refine((v) => /^\d+$/.test(v) && +v >= min && +v <= max, `Enter ${min} to ${max}`)

const schema = z.object({
  title: z.string().trim().min(3, 'Enter a title').max(250),
  description: z.string().max(10_000),
  instructions: z.string().max(20_000),
  seriesId: z.string(),
  durationMinutes: int(1, 600),
  maxAttempts: int(1, 50),
  displayOrder: int(0, 10_000),
  startAt: z.string(),
  endAt: z.string(),
  free: z.boolean(),
  shuffleQuestions: z.boolean(),
  shuffleOptions: z.boolean(),
  showResultImmediately: z.boolean(),
}).refine((v) => !v.startAt || !v.endAt || new Date(v.startAt) < new Date(v.endAt),
  { path: ['endAt'], message: 'End must be after start' })
type Values = z.infer<typeof schema>

/** <input type="datetime-local"> works in local time without a zone; convert both ways. */
export function toLocalInput(iso?: string) {
  if (!iso) return ''
  const d = new Date(iso)
  const pad = (n: number) => String(n).padStart(2, '0')
  return `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())}T${pad(d.getHours())}:${pad(d.getMinutes())}`
}
const fromLocalInput = (v: string) => (v ? new Date(v).toISOString() : null)

function defaults(t: AdminTest): Values {
  return {
    title: t.title, description: t.description ?? '', instructions: t.instructions ?? '', seriesId: t.seriesId ?? '',
    durationMinutes: String(t.durationMinutes), maxAttempts: String(t.maxAttempts), displayOrder: String(t.displayOrder),
    startAt: toLocalInput(t.startAt), endAt: toLocalInput(t.endAt), free: t.free, shuffleQuestions: t.shuffleQuestions,
    shuffleOptions: t.shuffleOptions, showResultImmediately: t.showResultImmediately,
  }
}

export function TestSettingsDialog({ test, open, onOpenChange }: { test: AdminTest; open: boolean; onOpenChange: (o: boolean) => void }) {
  const qc = useQueryClient()
  const series = useAllSeries()
  const form = useForm<Values>({ resolver: zodResolver(schema), defaultValues: defaults(test) })
  const { register, handleSubmit, reset, formState: { errors } } = form
  useEffect(() => { if (open) reset(defaults(test)) }, [open, test, reset])

  const save = useMutation({
    mutationFn: (v: Values) => adminApi.updateTest(test.id, {
      title: v.title, description: v.description || undefined, instructions: v.instructions || undefined,
      seriesId: v.seriesId || null, durationMinutes: Number(v.durationMinutes), maxAttempts: Number(v.maxAttempts),
      displayOrder: Number(v.displayOrder), startAt: fromLocalInput(v.startAt), endAt: fromLocalInput(v.endAt),
      free: v.free, shuffleQuestions: v.shuffleQuestions, shuffleOptions: v.shuffleOptions,
      showResultImmediately: v.showResultImmediately,
    }),
    onSuccess: (d) => {
      qc.setQueryData(['admin', 'test', test.id], d)
      void qc.invalidateQueries({ queryKey: ['admin', 'test', test.id] })
      void qc.invalidateQueries({ queryKey: ['admin', 'tests'] })
      toast.success('Settings saved')
      onOpenChange(false)
    },
    onError: (e) => toast.error(errorMessage(e)),
  })

  return (
    <Dialog open={open} onOpenChange={(o) => !save.isPending && onOpenChange(o)}>
      <DialogContent className="max-h-[90vh] overflow-y-auto sm:max-w-2xl">
        <DialogHeader><DialogTitle>Test settings</DialogTitle></DialogHeader>
        <form id="test-settings" className="grid gap-4" onSubmit={handleSubmit((v) => save.mutate(v))} noValidate>
          <FormField id="s-title" label="Title" error={errors.title}>
            <Input id="s-title" {...register('title')} aria-invalid={!!errors.title} />
          </FormField>
          <FormField id="s-desc" label="Description">
            <Textarea id="s-desc" rows={2} {...register('description')} />
          </FormField>
          <FormField id="s-instr" label="Instructions" hint="Shown on the instructions page before the test starts">
            <Textarea id="s-instr" rows={5} {...register('instructions')} />
          </FormField>
          <div className="grid gap-4 sm:grid-cols-2">
            <FormField id="s-series" label="Series">
              <NativeSelect id="s-series" {...register('seriesId')}>
                <option value="">None</option>
                {series.data?.content.filter((s) => s.examId === test.examId).map((s) => <option key={s.id} value={s.id}>{s.name}</option>)}
              </NativeSelect>
            </FormField>
            <FormField id="s-order" label="Order in series" error={errors.displayOrder}>
              <Input id="s-order" inputMode="numeric" {...register('displayOrder')} aria-invalid={!!errors.displayOrder} />
            </FormField>
            <FormField id="s-duration" label="Duration (minutes)" error={errors.durationMinutes}>
              <Input id="s-duration" inputMode="numeric" {...register('durationMinutes')} aria-invalid={!!errors.durationMinutes} />
            </FormField>
            <FormField id="s-attempts" label="Attempts allowed" error={errors.maxAttempts}>
              <Input id="s-attempts" inputMode="numeric" {...register('maxAttempts')} aria-invalid={!!errors.maxAttempts} />
            </FormField>
            <FormField id="s-start" label="Opens at (optional)" error={errors.startAt}>
              <Input id="s-start" type="datetime-local" {...register('startAt')} />
            </FormField>
            <FormField id="s-end" label="Closes at (optional)" error={errors.endAt}>
              <Input id="s-end" type="datetime-local" {...register('endAt')} aria-invalid={!!errors.endAt} />
            </FormField>
          </div>
          <div className="grid gap-2 text-sm sm:grid-cols-2">
            <label className="flex items-center gap-2"><Checkbox {...register('free')} /> Free sample test</label>
            <label className="flex items-center gap-2"><Checkbox {...register('shuffleQuestions')} /> Shuffle questions within sections</label>
            <label className="flex items-center gap-2"><Checkbox {...register('shuffleOptions')} /> Shuffle options</label>
            <label className="flex items-center gap-2"><Checkbox {...register('showResultImmediately')} /> Show result right after submission</label>
          </div>
        </form>
        <DialogFooter>
          <Button variant="outline" onClick={() => onOpenChange(false)} disabled={save.isPending}>Cancel</Button>
          <Button type="submit" form="test-settings" loading={save.isPending}>Save</Button>
        </DialogFooter>
      </DialogContent>
    </Dialog>
  )
}
