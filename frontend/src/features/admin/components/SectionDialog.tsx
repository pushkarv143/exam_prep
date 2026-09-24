import { useEffect } from 'react'
import { useForm } from 'react-hook-form'
import { zodResolver } from '@hookform/resolvers/zod'
import { z } from 'zod'
import { useMutation, useQueryClient } from '@tanstack/react-query'
import { toast } from 'sonner'
import { adminApi, useCatalogTree } from '@/api/admin'
import { FormField } from '@/components/common/FormField'
import { Button } from '@/components/ui/button'
import {
  Dialog, DialogContent, DialogFooter, DialogHeader, DialogTitle,
} from '@/components/ui/dialog'
import { Input } from '@/components/ui/input'
import { NativeSelect } from '@/components/ui/native-select'
import { errorMessage } from '@/lib/errors'
import type { AdminTest, TestSection } from '@/types/admin'
import { QUESTION_TYPES } from '../labels'

const optionalNumber = (max: number, int = false) => z.string().trim().refine(
  (v) => v === '' || ((int ? /^\d+$/ : /^\d+(\.\d+)?$/).test(v) && +v <= max), `Enter a number up to ${max}`)

const schema = z.object({
  name: z.string().trim().min(1, 'Enter a name').max(150),
  subjectId: z.string(),
  questionType: z.string(),
  defaultMarks: optionalNumber(100),
  defaultNegativeMarks: optionalNumber(100),
  maxQuestionsToAttempt: optionalNumber(500, true),
  targetCount: optionalNumber(500, true),
  displayOrder: optionalNumber(1000, true),
})
type Values = z.infer<typeof schema>

const num = (v: string) => (v === '' ? null : Number(v))

export function SectionDialog({ test, section, nextOrder, open, onOpenChange }: {
  test: AdminTest; section?: TestSection; nextOrder: number; open: boolean; onOpenChange: (o: boolean) => void
}) {
  const qc = useQueryClient()
  const tree = useCatalogTree(test.examId)
  const values = (): Values => ({
    name: section?.name ?? '', subjectId: section?.subjectId ?? '', questionType: section?.questionType ?? '',
    defaultMarks: section?.defaultMarks != null ? String(section.defaultMarks) : '',
    defaultNegativeMarks: section?.defaultNegativeMarks != null ? String(section.defaultNegativeMarks) : '',
    maxQuestionsToAttempt: section?.maxQuestionsToAttempt != null ? String(section.maxQuestionsToAttempt) : '',
    targetCount: section?.targetCount != null ? String(section.targetCount) : '',
    displayOrder: String(section?.displayOrder ?? nextOrder),
  })
  const form = useForm<Values>({ resolver: zodResolver(schema), defaultValues: values() })
  const { register, handleSubmit, reset, formState: { errors } } = form
  // Reset whenever the dialog opens, possibly for a different section.
  useEffect(() => { if (open) reset(values()) }, [open, section?.id])

  const save = useMutation({
    mutationFn: (v: Values) => {
      const body = {
        name: v.name, subjectId: v.subjectId || null, questionType: (v.questionType || null) as never,
        defaultMarks: num(v.defaultMarks), defaultNegativeMarks: num(v.defaultNegativeMarks),
        maxQuestionsToAttempt: num(v.maxQuestionsToAttempt) || null, targetCount: num(v.targetCount) || null,
        displayOrder: num(v.displayOrder) ?? nextOrder,
      }
      return section ? adminApi.updateSection(test.id, section.id, body) : adminApi.addSection(test.id, body)
    },
    onSuccess: () => {
      void qc.invalidateQueries({ queryKey: ['admin', 'test', test.id] })
      toast.success(section ? 'Section saved' : 'Section added')
      onOpenChange(false)
    },
    onError: (e) => toast.error(errorMessage(e)),
  })

  return (
    <Dialog open={open} onOpenChange={(o) => !save.isPending && onOpenChange(o)}>
      <DialogContent>
        <DialogHeader><DialogTitle>{section ? 'Edit section' : 'Add section'}</DialogTitle></DialogHeader>
        <form id="section-form" className="grid gap-4" onSubmit={handleSubmit((v) => save.mutate(v))} noValidate>
          <FormField id="sec-name" label="Name" error={errors.name}>
            <Input id="sec-name" {...register('name')} placeholder="Physics – Section A" aria-invalid={!!errors.name} />
          </FormField>
          <div className="grid gap-4 sm:grid-cols-2">
            <FormField id="sec-subject" label="Subject">
              <NativeSelect id="sec-subject" {...register('subjectId')}>
                <option value="">Any</option>
                {tree.data?.subjects.map((s) => <option key={s.id} value={s.id}>{s.name}</option>)}
              </NativeSelect>
            </FormField>
            <FormField id="sec-type" label="Question type">
              <NativeSelect id="sec-type" {...register('questionType')}>
                <option value="">Any</option>
                {QUESTION_TYPES.filter((t) => t.value !== 'PARAGRAPH').map((t) => <option key={t.value} value={t.value}>{t.label}</option>)}
              </NativeSelect>
            </FormField>
            <FormField id="sec-marks" label="Marks per question" error={errors.defaultMarks} hint="Blank = question's own marks">
              <Input id="sec-marks" inputMode="decimal" {...register('defaultMarks')} />
            </FormField>
            <FormField id="sec-neg" label="Negative marks" error={errors.defaultNegativeMarks}>
              <Input id="sec-neg" inputMode="decimal" {...register('defaultNegativeMarks')} />
            </FormField>
            <FormField id="sec-target" label="Target question count" error={errors.targetCount}>
              <Input id="sec-target" inputMode="numeric" {...register('targetCount')} />
            </FormField>
            <FormField id="sec-max" label="Attempt any (optional)" error={errors.maxQuestionsToAttempt} hint="e.g. 5 of 10 in JEE Main section B">
              <Input id="sec-max" inputMode="numeric" {...register('maxQuestionsToAttempt')} />
            </FormField>
            <FormField id="sec-order" label="Order" error={errors.displayOrder}>
              <Input id="sec-order" inputMode="numeric" {...register('displayOrder')} />
            </FormField>
          </div>
        </form>
        <DialogFooter>
          <Button variant="outline" onClick={() => onOpenChange(false)} disabled={save.isPending}>Cancel</Button>
          <Button type="submit" form="section-form" loading={save.isPending}>Save</Button>
        </DialogFooter>
      </DialogContent>
    </Dialog>
  )
}
