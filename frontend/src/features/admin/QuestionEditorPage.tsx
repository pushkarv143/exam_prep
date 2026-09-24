import { useEffect } from 'react'
import { Controller, useFieldArray, useForm, useWatch, type Control } from 'react-hook-form'
import { zodResolver } from '@hookform/resolvers/zod'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { Link, useNavigate, useParams } from 'react-router-dom'
import { ArrowLeft, Lock, Plus, Trash2, X } from 'lucide-react'
import { toast } from 'sonner'
import { adminApi, adminKeys } from '@/api/admin'
import { FormField } from '@/components/common/FormField'
import { MathText } from '@/components/common/MathText'
import { ErrorState, PageLoader } from '@/components/common/States'
import { PageHeader } from '@/components/layout/Layouts'
import { Alert, AlertDescription, AlertTitle } from '@/components/ui/alert'
import { Button } from '@/components/ui/button'
import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/card'
import { Input } from '@/components/ui/input'
import { NativeSelect } from '@/components/ui/native-select'
import { Textarea } from '@/components/ui/textarea'
import { errorMessage } from '@/lib/errors'
import { cn } from '@/lib/utils'
import type { Question } from '@/types/admin'
import { CatalogPicker } from './components/CatalogPicker'
import { ImageList, UploadButton } from './components/ImageUpload'
import { QUESTION_TYPES } from './labels'
import { emptyQuestion, OPTION_IDS, questionSchema, toFormValues, toRequest, type QuestionFormValues } from './questionForm'

const LATEX_HINT = 'Use $...$ for inline maths and $$...$$ for display maths, e.g. $\\frac{1}{2}mv^2$'

export default function QuestionEditorPage() {
  const { id } = useParams()
  const existing = useQuery({ queryKey: adminKeys.question(id ?? ''), queryFn: () => adminApi.question(id!), enabled: !!id })
  if (id && existing.isError) return <ErrorState error={existing.error} onRetry={() => existing.refetch()} />
  if (id && existing.isPending) return <PageLoader />
  return <Editor key={id ?? 'new'} question={existing.data} />
}

function Editor({ question }: { question?: Question }) {
  const navigate = useNavigate()
  const qc = useQueryClient()
  const frozen = !!question?.usedInPublishedTest
  const form = useForm<QuestionFormValues>({
    resolver: zodResolver(questionSchema),
    defaultValues: question ? toFormValues(question) : emptyQuestion(),
  })
  const { register, control, handleSubmit, setValue, formState: { errors, isDirty } } = form
  const type = useWatch({ control, name: 'type' })
  const choice = type === 'SINGLE_CORRECT' || type === 'MULTIPLE_CORRECT'
  const [examId, subjectId, chapterId, topicId] = useWatch({ control, name: ['examId', 'subjectId', 'chapterId', 'topicId'] })

  const save = useMutation({
    mutationFn: (v: QuestionFormValues) => question ? adminApi.updateQuestion(question.id, toRequest(v)) : adminApi.createQuestion(toRequest(v)),
    onSuccess: (saved) => {
      toast.success(question ? 'Question saved' : 'Question created')
      void qc.invalidateQueries({ queryKey: ['admin', 'questions'] })
      qc.setQueryData(adminKeys.question(saved.id), saved)
      if (question) form.reset(toFormValues(saved))
      else navigate(`/admin/questions/${saved.id}`, { replace: true })
    },
    onError: (e) => {
      toast.error(errorMessage(e), { duration: 8000 })
    },
  })

  // Warn before leaving with unsaved changes.
  useEffect(() => {
    const warn = (e: BeforeUnloadEvent) => { if (isDirty) e.preventDefault() }
    window.addEventListener('beforeunload', warn)
    return () => window.removeEventListener('beforeunload', warn)
  }, [isDirty])

  return (
    <form onSubmit={handleSubmit((v) => save.mutate(v))} noValidate>
      <PageHeader title={question ? 'Edit question' : 'New question'}
                  description={question ? `ID ${question.id}` : undefined}
                  actions={
                    <div className="flex gap-2">
                      <Button type="button" variant="outline" asChild><Link to="/admin/questions"><ArrowLeft /> Back</Link></Button>
                      <Button type="submit" loading={save.isPending}>{question ? 'Save changes' : 'Create question'}</Button>
                    </div>
                  } />

      {frozen && (
        <Alert className="mb-4">
          <Lock className="size-4" />
          <AlertTitle>Used in a published test</AlertTitle>
          <AlertDescription>
            Students have attempted this question, so its type and answer key are locked. You can still fix typos,
            the solution, tags and the topic. To change the answer, create a new question.
          </AlertDescription>
        </Alert>
      )}

      <div className="grid gap-6 xl:grid-cols-[1fr_420px]">
        <div className="space-y-6">
          <Card>
            <CardHeader><CardTitle>Classification</CardTitle></CardHeader>
            <CardContent className="space-y-4">
              <div className="grid gap-4 sm:grid-cols-4">
                <FormField id="type" label="Type">
                  <NativeSelect id="type" disabled={frozen} {...register('type')}>
                    {QUESTION_TYPES.map((t) => <option key={t.value} value={t.value}>{t.label}</option>)}
                  </NativeSelect>
                </FormField>
                <FormField id="difficulty" label="Difficulty">
                  <NativeSelect id="difficulty" {...register('difficulty')}>
                    <option value="">Not set</option><option value="EASY">Easy</option>
                    <option value="MEDIUM">Medium</option><option value="HARD">Hard</option>
                  </NativeSelect>
                </FormField>
                <FormField id="language" label="Language">
                  <NativeSelect id="language" {...register('language')}>
                    <option value="EN">English</option><option value="HI">Hindi</option>
                  </NativeSelect>
                </FormField>
                <FormField id="status" label="Status">
                  <NativeSelect id="status" {...register('status')}>
                    <option value="DRAFT">Draft</option><option value="ACTIVE">Active</option><option value="ARCHIVED">Archived</option>
                  </NativeSelect>
                </FormField>
              </div>
              <div className="grid gap-2">
                <span className="text-sm font-medium">Topic</span>
                <Controller control={control} name="topicId" render={({ fieldState }) => (
                  <>
                    <CatalogPicker anyLabel="Select" invalid={!!fieldState.error}
                                   value={{
                                     examId: examId || undefined, subjectId: subjectId || undefined,
                                     chapterId: chapterId || undefined, topicId: topicId || undefined,
                                   }}
                                   onChange={(v) => {
                                     const opts = { shouldDirty: true }
                                     setValue('examId', v.examId ?? '', opts)
                                     setValue('subjectId', v.subjectId ?? '', opts)
                                     setValue('chapterId', v.chapterId ?? '', opts)
                                     setValue('topicId', v.topicId ?? '', { ...opts, shouldValidate: form.formState.isSubmitted })
                                   }} />
                    {fieldState.error && <p className="text-destructive text-sm" role="alert">{fieldState.error.message}</p>}
                  </>
                )} />
              </div>
              {type !== 'PARAGRAPH' && (
                <FormField id="parentId" label="Paragraph (optional)" error={errors.parentId}
                           hint="To attach this question to a passage, paste the passage question's ID.">
                  <Input id="parentId" placeholder="e.g. 0199…" {...register('parentId')} aria-invalid={!!errors.parentId} />
                </FormField>
              )}
            </CardContent>
          </Card>

          <Card>
            <CardHeader><CardTitle>{type === 'PARAGRAPH' ? 'Passage' : 'Question'}</CardTitle></CardHeader>
            <CardContent className="space-y-4">
              {type === 'PARAGRAPH' ? (
                <FormField id="paragraph" label="Passage text" error={errors.paragraph} hint={LATEX_HINT}>
                  <Textarea id="paragraph" rows={8} {...register('paragraph')} aria-invalid={!!errors.paragraph} />
                </FormField>
              ) : (
                <FormField id="text" label="Question text" error={errors.text} hint={LATEX_HINT}>
                  <Textarea id="text" rows={6} {...register('text')} aria-invalid={!!errors.text} />
                </FormField>
              )}
              <div className="grid gap-2">
                <span className="text-sm font-medium">Figures</span>
                <Controller control={control} name="images" render={({ field }) => <ImageList images={field.value} onChange={field.onChange} />} />
              </div>
            </CardContent>
          </Card>

          {choice && <OptionsEditor control={control} frozen={frozen} form={form} />}
          {type === 'NUMERICAL' && (
            <Card>
              <CardHeader><CardTitle>Answer</CardTitle></CardHeader>
              <CardContent className="grid gap-4 sm:grid-cols-2">
                <FormField id="numValue" label="Correct value" error={errors.numValue}>
                  <Input id="numValue" inputMode="decimal" disabled={frozen} {...register('numValue')} aria-invalid={!!errors.numValue} />
                </FormField>
                <FormField id="tolerance" label="Tolerance (optional)" error={errors.tolerance} hint="Accepts value ± tolerance, e.g. 0.01">
                  <Input id="tolerance" inputMode="decimal" disabled={frozen} {...register('tolerance')} aria-invalid={!!errors.tolerance} />
                </FormField>
              </CardContent>
            </Card>
          )}
          {type === 'MATCH' && <MatchEditor control={control} frozen={frozen} form={form} />}

          {type !== 'PARAGRAPH' && (
            <Card>
              <CardHeader><CardTitle>Solution</CardTitle></CardHeader>
              <CardContent className="space-y-4">
                <FormField id="solutionText" label="Explanation" hint={LATEX_HINT}>
                  <Textarea id="solutionText" rows={5} {...register('solutionText')} />
                </FormField>
                <FormField id="solutionVideo" label="Video URL (optional)" error={errors.solutionVideo}>
                  <Input id="solutionVideo" placeholder="https://…" {...register('solutionVideo')} aria-invalid={!!errors.solutionVideo} />
                </FormField>
                <Controller control={control} name="solutionImages" render={({ field }) => <ImageList images={field.value} onChange={field.onChange} />} />
              </CardContent>
            </Card>
          )}

          <Card>
            <CardHeader><CardTitle>Marks &amp; metadata</CardTitle></CardHeader>
            <CardContent className="grid gap-4 sm:grid-cols-2">
              <FormField id="marks" label="Marks" error={errors.marks} hint="Blank uses the type's default (+4)">
                <Input id="marks" inputMode="decimal" {...register('marks')} aria-invalid={!!errors.marks} />
              </FormField>
              <FormField id="negativeMarks" label="Negative marks" error={errors.negativeMarks} hint="Blank uses the type's default">
                <Input id="negativeMarks" inputMode="decimal" {...register('negativeMarks')} aria-invalid={!!errors.negativeMarks} />
              </FormField>
              <FormField id="source" label="Source" error={errors.source} hint="e.g. JEE Main 2024 (Shift 1)">
                <Input id="source" {...register('source')} />
              </FormField>
              <FormField id="year" label="Year" error={errors.year}>
                <Input id="year" inputMode="numeric" {...register('year')} aria-invalid={!!errors.year} />
              </FormField>
              <div className="sm:col-span-2">
                <FormField id="tags" label="Tags" error={errors.tags} hint="Comma separated, e.g. pyq, important">
                  <Input id="tags" {...register('tags')} aria-invalid={!!errors.tags} />
                </FormField>
              </div>
            </CardContent>
          </Card>
        </div>

        <div className="xl:sticky xl:top-24 xl:self-start">
          <Preview control={control} />
        </div>
      </div>
    </form>
  )
}

type FormApi = ReturnType<typeof useForm<QuestionFormValues>>

function OptionsEditor({ control, frozen, form }: { control: Control<QuestionFormValues>; frozen: boolean; form: FormApi }) {
  const { fields, append, remove } = useFieldArray({ control, name: 'options' })
  const type = useWatch({ control, name: 'type' })
  const correct = useWatch({ control, name: 'correct' })
  const options = useWatch({ control, name: 'options' })
  const errors = form.formState.errors
  const multi = type === 'MULTIPLE_CORRECT'

  const toggle = (id: string) => {
    const next = multi ? (correct.includes(id) ? correct.filter((c) => c !== id) : [...correct, id]) : [id]
    form.setValue('correct', next, { shouldDirty: true, shouldValidate: form.formState.isSubmitted })
  }
  /** Option ids stay A, B, C… in order after a removal; the answer key follows. */
  const removeAt = (i: number) => {
    const removed = options[i].id
    remove(i)
    const remaining = options.filter((_, j) => j !== i)
    const remap = Object.fromEntries(remaining.map((o, j) => [o.id, OPTION_IDS[j]]))
    remaining.forEach((_, j) => form.setValue(`options.${j}.id`, OPTION_IDS[j]))
    form.setValue('correct', correct.filter((c) => c !== removed).map((c) => remap[c]), { shouldDirty: true })
  }

  return (
    <Card>
      <CardHeader><CardTitle>Options</CardTitle></CardHeader>
      <CardContent className="space-y-3">
        <p className="text-muted-foreground text-sm">
          {multi ? 'Tick every correct option.' : 'Select the one correct option.'}
        </p>
        {fields.map((f, i) => (
          <div key={f.id} className={cn('flex items-start gap-3 rounded-lg border p-3', correct.includes(options[i]?.id) && 'border-success bg-success/5')}>
            <label className="flex shrink-0 flex-col items-center gap-1 pt-1.5 text-xs">
              <input type={multi ? 'checkbox' : 'radio'} name="correct-option" disabled={frozen}
                     checked={correct.includes(options[i]?.id)} onChange={() => toggle(options[i].id)}
                     className="accent-success size-4" aria-label={`Option ${options[i]?.id} is correct`} />
              <span className="font-semibold">{options[i]?.id}</span>
            </label>
            <div className="min-w-0 flex-1 space-y-2">
              <Textarea rows={2} placeholder={`Option ${options[i]?.id}`} {...form.register(`options.${i}.text`)}
                        aria-invalid={!!errors.options?.[i]?.text} />
              {errors.options?.[i]?.text && <p className="text-destructive text-xs">{errors.options[i]?.text?.message}</p>}
              {options[i]?.image ? (
                <div className="relative inline-block">
                  <img src={options[i].image} alt="" className="h-20 rounded border bg-white" />
                  <button type="button" aria-label="Remove image" onClick={() => form.setValue(`options.${i}.image`, '', { shouldDirty: true })}
                          className="bg-destructive absolute -top-2 -right-2 rounded-full p-0.5 text-white"><X className="size-3.5" /></button>
                </div>
              ) : (
                <UploadButton label="Image" onUploaded={(url) => form.setValue(`options.${i}.image`, url, { shouldDirty: true })} />
              )}
            </div>
            <Button type="button" variant="ghost" size="icon" aria-label={`Remove option ${options[i]?.id}`}
                    disabled={frozen || fields.length <= 2} onClick={() => removeAt(i)}><Trash2 /></Button>
          </div>
        ))}
        {(errors.options?.message || errors.options?.root?.message) && <p className="text-destructive text-sm">{errors.options?.message ?? errors.options?.root?.message}</p>}
        {errors.correct && <p className="text-destructive text-sm" role="alert">{errors.correct.message}</p>}
        {fields.length < OPTION_IDS.length && !frozen && (
          <Button type="button" variant="outline" size="sm" onClick={() => append({ id: OPTION_IDS[fields.length], text: '', image: '' })}>
            <Plus /> Add option
          </Button>
        )}
      </CardContent>
    </Card>
  )
}

function MatchEditor({ control, frozen, form }: { control: Control<QuestionFormValues>; frozen: boolean; form: FormApi }) {
  const left = useFieldArray({ control, name: 'matchLeft' })
  const right = useFieldArray({ control, name: 'matchRight' })
  const leftValues = useWatch({ control, name: 'matchLeft' })
  const rightValues = useWatch({ control, name: 'matchRight' })
  const pairs = useWatch({ control, name: 'pairs' })
  const errors = form.formState.errors
  const leftIds = ['P', 'Q', 'R', 'S', 'T', 'U', 'V', 'W', 'X', 'Y']

  return (
    <Card>
      <CardHeader><CardTitle>Match the columns</CardTitle></CardHeader>
      <CardContent className="space-y-4">
        <div className="grid gap-6 md:grid-cols-2">
          <div className="space-y-2">
            <p className="text-sm font-medium">Column I</p>
            {left.fields.map((f, i) => (
              <div key={f.id} className="flex items-center gap-2">
                <span className="w-6 font-semibold">{leftValues[i]?.id}</span>
                <Input {...form.register(`matchLeft.${i}.text`)} placeholder="Item text" />
                <NativeSelect aria-label={`Match for ${leftValues[i]?.id}`} disabled={frozen} className="w-20"
                              value={pairs[leftValues[i]?.id] ?? ''}
                              onChange={(e) => form.setValue('pairs', { ...pairs, [leftValues[i].id]: e.target.value }, { shouldDirty: true })}>
                  <option value="">→</option>
                  {rightValues.map((r) => <option key={r.id} value={r.id}>{r.id}</option>)}
                </NativeSelect>
                <Button type="button" variant="ghost" size="icon" aria-label="Remove" disabled={frozen || left.fields.length <= 1}
                        onClick={() => {
                          const id = leftValues[i].id
                          left.remove(i)
                          const { [id]: _removed, ...rest } = pairs
                          void _removed
                          form.setValue('pairs', rest, { shouldDirty: true })
                        }}><Trash2 /></Button>
              </div>
            ))}
            {left.fields.length < 10 && !frozen && (
              <Button type="button" variant="outline" size="sm"
                      onClick={() => left.append({ id: leftIds.find((id) => !leftValues.some((l) => l.id === id)) ?? 'Z', text: '' })}>
                <Plus /> Add item
              </Button>
            )}
          </div>
          <div className="space-y-2">
            <p className="text-sm font-medium">Column II</p>
            {right.fields.map((f, i) => (
              <div key={f.id} className="flex items-center gap-2">
                <span className="w-6 font-semibold">{rightValues[i]?.id}</span>
                <Input {...form.register(`matchRight.${i}.text`)} placeholder="Item text" />
                <Button type="button" variant="ghost" size="icon" aria-label="Remove" disabled={frozen || right.fields.length <= 1}
                        onClick={() => right.remove(i)}><Trash2 /></Button>
              </div>
            ))}
            {right.fields.length < 10 && !frozen && (
              <Button type="button" variant="outline" size="sm"
                      onClick={() => right.append({ id: String(Math.max(0, ...rightValues.map((r) => Number(r.id) || 0)) + 1), text: '' })}>
                <Plus /> Add item
              </Button>
            )}
          </div>
        </div>
        {(errors.matchLeft?.message || errors.matchLeft?.root?.message) && <p className="text-destructive text-sm">{errors.matchLeft?.message ?? errors.matchLeft?.root?.message}</p>}
        {errors.pairs && <p className="text-destructive text-sm">{(errors.pairs as { message?: string }).message}</p>}
      </CardContent>
    </Card>
  )
}

/** Live KaTeX preview, rendered the way students will see it. */
function Preview({ control }: { control: Control<QuestionFormValues> }) {
  const v = useWatch({ control })
  const choice = v.type === 'SINGLE_CORRECT' || v.type === 'MULTIPLE_CORRECT'
  return (
    <Card>
      <CardHeader><CardTitle>Preview</CardTitle></CardHeader>
      <CardContent className="space-y-4 text-sm">
        {v.type === 'PARAGRAPH' ? <MathText text={v.paragraph} /> : <MathText text={v.text || '*Question text…*'} />}
        {v.images?.map((img) => img?.url && <img key={img.url} src={img.url} alt="" className="max-h-48 rounded border bg-white" />)}
        {choice && (
          <ul className="space-y-2">
            {v.options?.map((o, i) => (
              <li key={i} className={cn('flex gap-2 rounded border p-2', o?.id && v.correct?.includes(o.id) && 'border-success bg-success/10')}>
                <span className="font-semibold">({i + 1})</span>
                <div className="min-w-0">
                  <MathText text={o?.text} as="span" />
                  {o?.image && <img src={o.image} alt="" className="mt-1 max-h-24 rounded border" />}
                </div>
              </li>
            ))}
          </ul>
        )}
        {v.type === 'MATCH' && (
          <div className="grid grid-cols-2 gap-3">
            <ul className="space-y-1">{v.matchLeft?.map((l, i) => <li key={i}><strong>{l?.id}.</strong> <MathText text={l?.text} as="span" /></li>)}</ul>
            <ul className="space-y-1">{v.matchRight?.map((r, i) => <li key={i}><strong>{r?.id}.</strong> <MathText text={r?.text} as="span" /></li>)}</ul>
          </div>
        )}
        {v.type === 'NUMERICAL' && v.numValue && <p>Answer: <strong>{v.numValue}</strong>{v.tolerance ? ` ± ${v.tolerance}` : ''}</p>}
        {v.solutionText && (
          <div className="bg-muted/50 rounded border p-3">
            <p className="mb-1 font-semibold">Solution</p>
            <MathText text={v.solutionText} />
          </div>
        )}
      </CardContent>
    </Card>
  )
}
