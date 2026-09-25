import { useEffect, useMemo, useState } from 'react'
import { Controller, useFieldArray, useForm, useWatch, type Control, type Path, type UseFormReturn } from 'react-hook-form'
import { zodResolver } from '@hookform/resolvers/zod'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { Link, useNavigate, useParams } from 'react-router-dom'
import { ArrowLeft, History, Languages, MessageSquare, Pin, PinOff, Plus, Save, Trash2, X } from 'lucide-react'
import { toast } from 'sonner'
import { adminApi, adminKeys } from '@/api/admin'
import { contentApi, contentKeys } from '@/api/content'
import { FormField } from '@/components/common/FormField'
import { MathText } from '@/components/common/MathText'
import { ErrorState, PageLoader } from '@/components/common/States'
import { PageHeader } from '@/components/layout/Layouts'
import { Alert, AlertDescription, AlertTitle } from '@/components/ui/alert'
import { Badge } from '@/components/ui/badge'
import { Button } from '@/components/ui/button'
import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/card'
import { Checkbox } from '@/components/ui/checkbox'
import { Input } from '@/components/ui/input'
import { NativeSelect } from '@/components/ui/native-select'
import { Textarea } from '@/components/ui/textarea'
import { errorMessage, toApiError } from '@/lib/errors'
import { formatDateTime, formatRelative } from '@/lib/format'
import { cn } from '@/lib/utils'
import { useAuthStore } from '@/store/auth'
import type { Question } from '@/types/admin'
import type { Language } from '@/types/exam'
import { CatalogPicker } from './components/CatalogPicker'
import { ImageList, UploadButton } from './components/ImageUpload'
import {
  COGNITIVE_LEVELS, LANGUAGE_LABEL, PARTIAL_RULES, QUESTION_STATUS, QUESTION_STATUS_LABEL, QUESTION_TYPES, SOURCE_TYPES,
} from './labels'
import {
  emptyQuestion, missingTranslation, OPTION_IDS, questionSchema, secondLanguage, toFormValues, toRequest,
  type QuestionFormValues,
} from './questionForm'
import { HistoryPanel } from './studio/HistoryPanel'
import { ReviewPanel } from './studio/ReviewPanel'
import { useStudioDraft, useUnsavedGuard } from './studio/useStudioDraft'
import { WorkflowActions } from './studio/WorkflowActions'

const LATEX_HINT = 'Markdown with $...$ for inline and $$...$$ for display maths, e.g. $\\frac{1}{2}mv^2$'

/** How the content fields are laid out when the question has a second language. */
type LangMode = 'primary' | 'second' | 'both'
type FormApi = UseFormReturn<QuestionFormValues>

export default function QuestionEditorPage() {
  const { id } = useParams()
  const existing = useQuery({ queryKey: adminKeys.question(id ?? ''), queryFn: () => adminApi.question(id!), enabled: !!id })
  if (id && existing.isError) return <ErrorState error={existing.error} onRetry={() => existing.refetch()} />
  if (id && existing.isPending) return <PageLoader />
  return <Studio key={id ?? 'new'} question={existing.data} />
}

function Studio({ question }: { question?: Question }) {
  const navigate = useNavigate()
  const qc = useQueryClient()
  const userId = useAuthStore((s) => s.user?.id)
  const form = useForm<QuestionFormValues>({
    resolver: zodResolver(questionSchema),
    defaultValues: question ? toFormValues(question) : emptyQuestion(),
  })
  const { register, control, handleSubmit, setValue, reset, formState: { errors, isDirty } } = form
  const [type, language, bilingual] = useWatch({ control, name: ['type', 'language', 'bilingual'] })
  const [examId, subjectId, chapterId, topicId] = useWatch({ control, name: ['examId', 'subjectId', 'chapterId', 'topicId'] })
  const choice = type === 'SINGLE_CORRECT' || type === 'MULTIPLE_CORRECT'
  const second = secondLanguage(language)
  const [mode, setMode] = useState<LangMode>('both')
  const effectiveMode: LangMode = bilingual ? mode : 'primary'
  const [panel, setPanel] = useState<'preview' | 'review' | 'history'>(question?.status === 'IN_REVIEW' ? 'review' : 'preview')
  const readOnly = !!question && !question.actions.includes('EDIT')

  const draft = useStudioDraft(form, userId, question?.id, question?.currentVersion ?? null)
  const allowNext = useUnsavedGuard(isDirty)

  // A newer server copy (after a workflow action or restore) replaces the form when nothing is pending locally.
  useEffect(() => {
    if (question && !form.formState.isDirty) reset(toFormValues(question))
  }, [question, reset, form])

  const save = useMutation({
    mutationFn: (v: QuestionFormValues) => question
      ? adminApi.updateQuestion(question.id, toRequest(v, question.currentVersion))
      : adminApi.createQuestion(toRequest(v)),
    onSuccess: (saved) => {
      draft.clear()
      const newVersion = !question || saved.currentVersion !== question.currentVersion
      toast.success(!question ? 'Draft created (v1)' : newVersion ? `Saved as v${saved.currentVersion}` : 'No changes to save')
      void qc.invalidateQueries({ queryKey: ['admin', 'questions'] })
      void qc.invalidateQueries({ queryKey: contentKeys.versions(saved.id) })
      void qc.invalidateQueries({ queryKey: contentKeys.activity(saved.id) })
      qc.setQueryData(adminKeys.question(saved.id), saved)
      reset(toFormValues(saved))
      if (!question) {
        allowNext.current = true
        navigate(`/admin/questions/${saved.id}`, { replace: true })
      }
    },
    onError: (e) => {
      const err = toApiError(e)
      if (err.code === 'QUESTION_VERSION_CONFLICT') {
        toast.error(err.message, {
          duration: 15_000,
          action: { label: 'Reload', onClick: () => { void qc.invalidateQueries({ queryKey: adminKeys.question(question!.id) }) } },
        })
      } else {
        toast.error(errorMessage(e), { duration: 8000 })
      }
    },
  })
  const submit = handleSubmit((v) => save.mutate(v), () => toast.error('Please fix the highlighted fields'))

  // Ctrl/⌘+S saves.
  useEffect(() => {
    const onKey = (e: KeyboardEvent) => {
      if ((e.ctrlKey || e.metaKey) && e.key.toLowerCase() === 's') {
        e.preventDefault()
        if (!readOnly) void submit()
      }
    }
    window.addEventListener('keydown', onKey)
    return () => window.removeEventListener('keydown', onKey)
  }, [submit, readOnly])

  const subTopics = useQuery({ queryKey: contentKeys.subTopics(topicId), queryFn: () => contentApi.subTopics(topicId), enabled: !!topicId })
  const values = useWatch({ control }) as QuestionFormValues
  const missing = useMemo(() => missingTranslation(values), [values])

  return (
    <form onSubmit={submit} noValidate>
      <PageHeader
        title={question ? 'Question studio' : 'New question'}
        description={question ? `ID ${question.id}` : 'Starts as a draft; submit it for review when it is ready.'}
        actions={
          <div className="flex flex-wrap items-center justify-end gap-2">
            <Button type="button" variant="outline" asChild><Link to="/admin/questions"><ArrowLeft /> Bank</Link></Button>
            {question && <WorkflowActions question={question} dirty={isDirty} />}
          </div>
        } />

      {question && <StatusStrip question={question} />}

      {draft.pending && (
        <Alert className="mb-4">
          <History className="size-4" />
          <AlertTitle>Unsaved changes from {formatRelative(draft.pending.savedAt)}</AlertTitle>
          <AlertDescription>
            <p>
              This browser kept edits you had not saved ({formatDateTime(draft.pending.savedAt)}).
              {draft.outdated && ` They were made on v${draft.pending.baseVersion}; the question is now at v${question?.currentVersion}, so check before saving.`}
            </p>
            <div className="mt-2 flex gap-2">
              <Button type="button" size="sm" onClick={draft.restore}>Restore my changes</Button>
              <Button type="button" size="sm" variant="outline" onClick={draft.discard}>Discard</Button>
            </div>
          </AlertDescription>
        </Alert>
      )}

      <div className="grid gap-6 xl:grid-cols-[1fr_440px]">
        <fieldset disabled={readOnly} className="min-w-0 space-y-6">
          {readOnly && question?.status !== 'ARCHIVED' && (
            <p className="text-muted-foreground text-sm">You can read this question and comment on it, but not edit it.</p>
          )}
          <Card>
            <CardHeader><CardTitle>Classification</CardTitle></CardHeader>
            <CardContent className="space-y-4">
              <div className="grid gap-4 sm:grid-cols-3">
                <FormField id="type" label="Type">
                  <NativeSelect id="type" {...register('type')}>
                    {QUESTION_TYPES.map((t) => <option key={t.value} value={t.value}>{t.label}</option>)}
                  </NativeSelect>
                </FormField>
                <FormField id="difficulty" label="Difficulty">
                  <NativeSelect id="difficulty" {...register('difficulty')}>
                    <option value="">Not set</option><option value="EASY">Easy</option>
                    <option value="MEDIUM">Medium</option><option value="HARD">Hard</option>
                  </NativeSelect>
                </FormField>
                <FormField id="language" label="Written in" hint="The primary language; the other one is the translation">
                  <NativeSelect id="language" {...register('language')}>
                    <option value="EN">English</option><option value="HI">हिन्दी (Hindi)</option>
                  </NativeSelect>
                </FormField>
              </div>
              <div className="grid gap-2">
                <span className="text-sm font-medium">Topic</span>
                <Controller control={control} name="topicId" render={({ fieldState }) => (
                  <>
                    <CatalogPicker anyLabel="Select" invalid={!!fieldState.error}
                                   value={{ examId: examId || undefined, subjectId: subjectId || undefined,
                                     chapterId: chapterId || undefined, topicId: topicId || undefined }}
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
              <div className="grid gap-4 sm:grid-cols-2">
                <FormField id="subTopic" label="Sub-topic (optional)" error={errors.subTopic} hint="Type a new one or pick one used before">
                  <Input id="subTopic" list="subtopic-options" {...register('subTopic')} />
                  <datalist id="subtopic-options">{subTopics.data?.map((s) => <option key={s} value={s} />)}</datalist>
                </FormField>
                {type !== 'PARAGRAPH' && (
                  <FormField id="parentId" label="Paragraph (optional)" error={errors.parentId}
                             hint="To attach this question to a passage, paste the passage question's ID.">
                    <Input id="parentId" placeholder="e.g. 0199…" {...register('parentId')} aria-invalid={!!errors.parentId} />
                  </FormField>
                )}
              </div>
            </CardContent>
          </Card>

          <Card>
            <CardHeader className="flex flex-row flex-wrap items-center justify-between gap-3">
              <CardTitle>{type === 'PARAGRAPH' ? 'Passage' : 'Question'}</CardTitle>
              <div className="flex flex-wrap items-center gap-3">
                <label className="flex items-center gap-2 text-sm">
                  <Controller control={control} name="bilingual" render={({ field }) => (
                    <Checkbox checked={field.value} onCheckedChange={(v) => field.onChange(v === true)} aria-label={`Also in ${LANGUAGE_LABEL[second]}`} />
                  )} />
                  <Languages className="size-4" /> Also in {LANGUAGE_LABEL[second]}
                </label>
                {bilingual && (
                  <div className="bg-muted inline-flex rounded-md p-0.5 text-xs" role="tablist" aria-label="Language layout">
                    {([['primary', LANGUAGE_LABEL[language]], ['second', LANGUAGE_LABEL[second]], ['both', 'Side by side']] as const).map(([m, l]) => (
                      <button key={m} type="button" role="tab" aria-selected={mode === m} onClick={() => setMode(m)}
                              className={cn('rounded px-2.5 py-1 font-medium', mode === m ? 'bg-background shadow-sm' : 'text-muted-foreground')}>{l}</button>
                    ))}
                  </div>
                )}
              </div>
            </CardHeader>
            <CardContent className="space-y-4">
              {bilingual && (
                <p className={cn('text-xs', missing.length ? 'text-warning' : 'text-success')}>
                  {missing.length
                    ? `${LANGUAGE_LABEL[second]}: ${missing.length} text${missing.length > 1 ? 's' : ''} not translated yet (${missing.join(', ')}). Students then see the ${LANGUAGE_LABEL[language]} text.`
                    : `${LANGUAGE_LABEL[second]} translation complete.`}
                </p>
              )}
              {type === 'PARAGRAPH' ? (
                <BiText form={form} id="paragraph" label="Passage text" name="paragraph" trName="tr.paragraph" rows={8}
                        mode={effectiveMode} language={language} hint={LATEX_HINT} error={errors.paragraph?.message} />
              ) : (
                <BiText form={form} id="text" label="Question text" name="text" trName="tr.text" rows={6}
                        mode={effectiveMode} language={language} hint={LATEX_HINT} error={errors.text?.message} />
              )}
              <div className="grid gap-2">
                <span className="text-sm font-medium">Figures</span>
                <Controller control={control} name="images" render={({ field }) => <ImageList images={field.value} onChange={field.onChange} />} />
                <p className="text-muted-foreground text-xs">Figures are shared by both languages.</p>
              </div>
            </CardContent>
          </Card>

          {choice && <OptionsEditor form={form} mode={effectiveMode} language={language} />}
          {type === 'NUMERICAL' && <NumericalAnswer form={form} />}
          {type === 'MATCH' && <MatchEditor control={control} form={form} mode={effectiveMode} language={language} />}

          {type !== 'PARAGRAPH' && (
            <Card>
              <CardHeader><CardTitle>Solution</CardTitle></CardHeader>
              <CardContent className="space-y-4">
                <BiText form={form} id="solutionText" label="Explanation" name="solutionText" trName="tr.solution" rows={5}
                        mode={effectiveMode} language={language} hint={LATEX_HINT} />
                <FormField id="solutionVideo" label="Video URL (optional)" error={errors.solutionVideo}>
                  <Input id="solutionVideo" placeholder="https://…" {...register('solutionVideo')} aria-invalid={!!errors.solutionVideo} />
                </FormField>
                <Controller control={control} name="solutionImages" render={({ field }) => <ImageList images={field.value} onChange={field.onChange} />} />
              </CardContent>
            </Card>
          )}

          <MetadataCard form={form} />
        </fieldset>

        <aside className="space-y-3 xl:sticky xl:top-24 xl:self-start">
          {question && (
            <div className="bg-muted inline-flex w-full rounded-lg p-1" role="tablist" aria-label="Side panel">
              {([['preview', 'Preview'], ['review', `Review${question.openComments ? ` (${question.openComments})` : ''}`],
                ['history', `History (v${question.currentVersion})`]] as const).map(([p, l]) => (
                <button key={p} type="button" role="tab" aria-selected={panel === p} onClick={() => setPanel(p)}
                        className={cn('flex-1 rounded-md px-3 py-1.5 text-sm font-medium', panel === p ? 'bg-background shadow-sm' : 'text-muted-foreground')}>
                  {l}
                </button>
              ))}
            </div>
          )}
          <Card>
            <CardContent className="pt-6">
              {(!question || panel === 'preview') && <Preview control={control} />}
              {question && panel === 'review' && <ReviewPanel question={question} />}
              {question && panel === 'history' && (
                <HistoryPanel question={question} onRestored={(q) => { draft.clear(); reset(toFormValues(q)) }} />
              )}
            </CardContent>
          </Card>
        </aside>
      </div>

      {!readOnly && (isDirty || !question) && (
        <div className="bg-background/95 sticky bottom-0 z-20 -mx-4 mt-6 border-t px-4 py-3 backdrop-blur sm:-mx-6 sm:px-6">
          <div className="flex flex-wrap items-center gap-3">
            <span className="text-sm font-medium">{question ? 'Unsaved changes' : 'New question'}</span>
            {question && (
              <Input className="h-9 max-w-md flex-1" placeholder="What changed? (optional, kept in the history)" maxLength={500}
                     aria-label="Change note" {...register('changeNote')} />
            )}
            <div className="ml-auto flex gap-2">
              {question && (
                <Button type="button" variant="outline" onClick={() => { reset(toFormValues(question)); draft.clear() }}>Discard</Button>
              )}
              <Button type="submit" loading={save.isPending}>
                <Save /> {question ? `Save as v${question.currentVersion + 1}` : 'Create draft'}
              </Button>
            </div>
          </div>
          <p className="text-muted-foreground mt-1 text-xs">Ctrl+S saves. Unsaved edits are also kept in this browser.</p>
        </div>
      )}
    </form>
  )
}

/** Status, versions and the review situation at a glance. */
function StatusStrip({ question: q }: { question: Question }) {
  const revision = q.publishedVersion != null && q.publishedVersion !== q.currentVersion
  return (
    <div className="mb-4 space-y-3">
      <div className="flex flex-wrap items-center gap-2 text-sm">
        <Badge variant={QUESTION_STATUS[q.status]}>{QUESTION_STATUS_LABEL[q.status]}</Badge>
        <Badge variant="outline">v{q.currentVersion}</Badge>
        {q.publishedVersion != null && <Badge variant="success">Live: v{q.publishedVersion}</Badge>}
        {q.usedInPublishedTests > 0 && <span className="text-muted-foreground">Used in {q.usedInPublishedTests} published test{q.usedInPublishedTests > 1 ? 's' : ''}</span>}
        {q.openComments > 0 && <span className="text-warning flex items-center gap-1"><MessageSquare className="size-3.5" /> {q.openComments} open comment{q.openComments > 1 ? 's' : ''}</span>}
        <span className="text-muted-foreground ml-auto">By {q.createdByName ?? 'unknown'} · updated {formatRelative(q.updatedAt)}</span>
      </div>
      {revision && (
        <Alert>
          <History className="size-4" />
          <AlertTitle>Tests use v{q.publishedVersion}; you are working on v{q.currentVersion}</AlertTitle>
          <AlertDescription>
            Nothing changes for students until this revision is reviewed and published. Then draft tests move to it, and
            published tests too if only the wording changed.
          </AlertDescription>
        </Alert>
      )}
      {q.status === 'CHANGES_REQUESTED' && (
        <Alert variant="destructive">
          <MessageSquare className="size-4" />
          <AlertTitle>The reviewer asked for changes</AlertTitle>
          <AlertDescription>See the comments in the Review tab, fix them, save and submit again.</AlertDescription>
        </Alert>
      )}
      {q.status === 'ARCHIVED' && (
        <Alert>
          <AlertTitle>Archived</AlertTitle>
          <AlertDescription>Hidden from the bank and pickers. Tests that use it keep working. Restore it to edit.</AlertDescription>
        </Alert>
      )}
    </div>
  )
}

/**
 * A text in the primary language and, when the question is bilingual, its translation:
 * one of them, or both side by side. In "second" mode the primary text is shown read-only
 * above the input so the translator sees what to translate.
 */
function BiText({ form, id, label, name, trName, rows, mode, language, hint, error }: {
  form: FormApi; id: string; label: string; name: Path<QuestionFormValues>; trName: Path<QuestionFormValues>; rows: number
  mode: LangMode; language: Language; hint?: string; error?: string
}) {
  const second = secondLanguage(language)
  const primaryValue = useWatch({ control: form.control, name }) as string
  const primary = (
    <div className="grid gap-1.5">
      {mode === 'both' && <span className="text-muted-foreground text-xs font-medium">{LANGUAGE_LABEL[language]}</span>}
      <Textarea id={id} rows={rows} {...form.register(name)} aria-invalid={!!error} aria-label={`${label} (${LANGUAGE_LABEL[language]})`} />
    </div>
  )
  const translated = (
    <div className="grid gap-1.5">
      {mode === 'both' && <span className="text-muted-foreground text-xs font-medium">{LANGUAGE_LABEL[second]}</span>}
      <Textarea id={`${id}-${second}`} rows={rows} lang={second.toLowerCase()} {...form.register(trName)}
                aria-label={`${label} (${LANGUAGE_LABEL[second]})`} placeholder={`${label} in ${LANGUAGE_LABEL[second]}`} />
    </div>
  )
  return (
    <div className="grid gap-2">
      <label htmlFor={mode === 'second' ? `${id}-${second}` : id} className="text-sm font-medium">{label}</label>
      {mode === 'primary' && primary}
      {mode === 'second' && (
        <>
          <div className="bg-muted/40 text-muted-foreground rounded-md border p-2 text-sm"><MathText text={primaryValue || '—'} /></div>
          {translated}
        </>
      )}
      {mode === 'both' && <div className="grid gap-3 md:grid-cols-2">{primary}{translated}</div>}
      {error ? <p className="text-destructive text-sm" role="alert">{error}</p> : hint ? <p className="text-muted-foreground text-xs">{hint}</p> : null}
    </div>
  )
}

function OptionsEditor({ form, mode, language }: { form: FormApi; mode: LangMode; language: Language }) {
  const { control } = form
  const { fields, append, remove } = useFieldArray({ control, name: 'options' })
  const [type, correct, options, tr, shuffle] = useWatch({ control, name: ['type', 'correct', 'options', 'tr.options', 'shuffleOptions'] })
  const errors = form.formState.errors
  const multi = type === 'MULTIPLE_CORRECT'
  const second = secondLanguage(language)

  const toggle = (id: string) => {
    const next = multi ? (correct.includes(id) ? correct.filter((c) => c !== id) : [...correct, id]) : [id]
    form.setValue('correct', next, { shouldDirty: true, shouldValidate: form.formState.isSubmitted })
  }
  /** Option ids stay A, B, C… in order after a removal; the answer key and the translation follow. */
  const removeAt = (i: number) => {
    const removed = options[i].id
    remove(i)
    const remaining = options.filter((_, j) => j !== i)
    const remap = Object.fromEntries(remaining.map((o, j) => [o.id, OPTION_IDS[j]]))
    remaining.forEach((_, j) => form.setValue(`options.${j}.id`, OPTION_IDS[j]))
    form.setValue('correct', correct.filter((c) => c !== removed).map((c) => remap[c]), { shouldDirty: true })
    form.setValue('tr.options', Object.fromEntries(remaining.map((o) => [remap[o.id], tr?.[o.id] ?? ''])), { shouldDirty: true })
  }

  return (
    <Card>
      <CardHeader className="flex flex-row flex-wrap items-center justify-between gap-3">
        <CardTitle>Options</CardTitle>
        <label className="flex items-center gap-2 text-sm">
          <Controller control={control} name="shuffleOptions" render={({ field }) => (
            <Checkbox checked={field.value} onCheckedChange={(v) => field.onChange(v === true)} />
          )} />
          Allow shuffling when the test shuffles options
        </label>
      </CardHeader>
      <CardContent className="space-y-3">
        <p className="text-muted-foreground text-sm">
          {multi ? 'Tick every correct option.' : 'Select the one correct option.'}
          {shuffle && ' Pin an option (e.g. "None of these") to keep it in place.'}
        </p>
        {fields.map((f, i) => {
          const id = options[i]?.id
          const pinned = options[i]?.pinned
          return (
            <div key={f.id} className={cn('flex items-start gap-3 rounded-lg border p-3', correct.includes(id) && 'border-success bg-success/5')}>
              <label className="flex shrink-0 flex-col items-center gap-1 pt-1.5 text-xs">
                <input type={multi ? 'checkbox' : 'radio'} name="correct-option" checked={correct.includes(id)}
                       onChange={() => toggle(id)} className="accent-success size-4" aria-label={`Option ${id} is correct`} />
                <span className="font-semibold">{id}</span>
              </label>
              <div className="min-w-0 flex-1 space-y-2">
                <div className={cn('grid gap-2', mode === 'both' && 'md:grid-cols-2')}>
                  {mode !== 'second' ? (
                    <Textarea rows={2} placeholder={`Option ${id}`} {...form.register(`options.${i}.text`)}
                              aria-label={`Option ${id} (${LANGUAGE_LABEL[language]})`} aria-invalid={!!errors.options?.[i]?.text} />
                  ) : (
                    <div className="bg-muted/40 text-muted-foreground rounded-md border p-2 text-sm"><MathText text={options[i]?.text || '—'} /></div>
                  )}
                  {mode !== 'primary' && (
                    <Textarea rows={2} lang={second.toLowerCase()} placeholder={`Option ${id} in ${LANGUAGE_LABEL[second]}`}
                              {...form.register(`tr.options.${id}` as Path<QuestionFormValues>)} aria-label={`Option ${id} (${LANGUAGE_LABEL[second]})`} />
                  )}
                </div>
                {errors.options?.[i]?.text && <p className="text-destructive text-xs">{errors.options[i]?.text?.message}</p>}
                <div className="flex flex-wrap items-center gap-2">
                  {options[i]?.image ? (
                    <div className="relative inline-block">
                      <img src={options[i].image} alt="" className="h-20 rounded border bg-white" />
                      <button type="button" aria-label="Remove image" onClick={() => form.setValue(`options.${i}.image`, '', { shouldDirty: true })}
                              className="bg-destructive absolute -top-2 -right-2 rounded-full p-0.5 text-white"><X className="size-3.5" /></button>
                    </div>
                  ) : (
                    <UploadButton label="Image" onUploaded={(url) => form.setValue(`options.${i}.image`, url, { shouldDirty: true })} />
                  )}
                  {shuffle && (
                    <Button type="button" size="sm" variant={pinned ? 'secondary' : 'ghost'} aria-pressed={pinned}
                            onClick={() => form.setValue(`options.${i}.pinned`, !pinned, { shouldDirty: true })}>
                      {pinned ? <><Pin /> Stays in place</> : <><PinOff /> Pin</>}
                    </Button>
                  )}
                </div>
              </div>
              <Button type="button" variant="ghost" size="icon" aria-label={`Remove option ${id}`}
                      disabled={fields.length <= 2} onClick={() => removeAt(i)}><Trash2 /></Button>
            </div>
          )
        })}
        {(errors.options?.message || errors.options?.root?.message) && <p className="text-destructive text-sm">{errors.options?.message ?? errors.options?.root?.message}</p>}
        {errors.correct && <p className="text-destructive text-sm" role="alert">{errors.correct.message}</p>}
        <div className="flex flex-wrap items-end justify-between gap-3">
          {fields.length < OPTION_IDS.length && (
            <Button type="button" variant="outline" size="sm" onClick={() => append({ id: OPTION_IDS[fields.length], text: '', image: '', pinned: false })}>
              <Plus /> Add option
            </Button>
          )}
          {multi && (
            <FormField id="partialRule" label="Partial marking (when the test allows it)"
                       hint={PARTIAL_RULES.find((p) => p.value === form.getValues('partialRule'))?.hint}>
              <NativeSelect id="partialRule" className="w-56" {...form.register('partialRule')}>
                {PARTIAL_RULES.map((p) => <option key={p.value} value={p.value}>{p.label}</option>)}
              </NativeSelect>
            </FormField>
          )}
        </div>
      </CardContent>
    </Card>
  )
}

function NumericalAnswer({ form }: { form: FormApi }) {
  const { register, control, formState: { errors } } = form
  const [format, numMode] = useWatch({ control, name: ['numericFormat', 'numMode'] })
  return (
    <Card>
      <CardHeader><CardTitle>Answer</CardTitle></CardHeader>
      <CardContent className="space-y-4">
        <div className="grid gap-4 sm:grid-cols-2">
          <FormField id="numericFormat" label="Answer format" hint={format === 'INTEGER' ? 'Students can type only whole numbers' : 'Decimals allowed'}>
            <NativeSelect id="numericFormat" {...register('numericFormat')}>
              <option value="DECIMAL">Decimal</option><option value="INTEGER">Integer</option>
            </NativeSelect>
          </FormField>
          <FormField id="numMode" label="Accept">
            <NativeSelect id="numMode" {...register('numMode')}>
              <option value="EXACT">One value (± tolerance)</option><option value="RANGE">Any value in a range</option>
            </NativeSelect>
          </FormField>
        </div>
        {numMode === 'EXACT' ? (
          <div className="grid gap-4 sm:grid-cols-2">
            <FormField id="numValue" label="Correct value" error={errors.numValue}>
              <Input id="numValue" inputMode="decimal" {...register('numValue')} aria-invalid={!!errors.numValue} />
            </FormField>
            {format !== 'INTEGER' && (
              <FormField id="tolerance" label="Tolerance (optional)" error={errors.tolerance} hint="Accepts value ± tolerance, e.g. 0.01">
                <Input id="tolerance" inputMode="decimal" {...register('tolerance')} aria-invalid={!!errors.tolerance} />
              </FormField>
            )}
          </div>
        ) : (
          <div className="grid gap-4 sm:grid-cols-2">
            <FormField id="numMin" label="From (inclusive)" error={errors.numMin}>
              <Input id="numMin" inputMode="decimal" {...register('numMin')} aria-invalid={!!errors.numMin} />
            </FormField>
            <FormField id="numMax" label="To (inclusive)" error={errors.numMax}>
              <Input id="numMax" inputMode="decimal" {...register('numMax')} aria-invalid={!!errors.numMax} />
            </FormField>
          </div>
        )}
      </CardContent>
    </Card>
  )
}

function MatchEditor({ control, form, mode, language }: { control: Control<QuestionFormValues>; form: FormApi; mode: LangMode; language: Language }) {
  const left = useFieldArray({ control, name: 'matchLeft' })
  const right = useFieldArray({ control, name: 'matchRight' })
  const leftValues = useWatch({ control, name: 'matchLeft' })
  const rightValues = useWatch({ control, name: 'matchRight' })
  const pairs = useWatch({ control, name: 'pairs' })
  const errors = form.formState.errors
  const leftIds = ['P', 'Q', 'R', 'S', 'T', 'U', 'V', 'W', 'X', 'Y']
  const second = secondLanguage(language)
  const trInput = (column: 'matchLeft' | 'matchRight', id: string) => mode !== 'primary' && (
    <Input lang={second.toLowerCase()} placeholder={LANGUAGE_LABEL[second]} aria-label={`${id} (${LANGUAGE_LABEL[second]})`}
           {...form.register(`tr.${column}.${id}` as Path<QuestionFormValues>)} />
  )

  return (
    <Card>
      <CardHeader><CardTitle>Match the columns</CardTitle></CardHeader>
      <CardContent className="space-y-4">
        <div className="grid gap-6 md:grid-cols-2">
          <div className="space-y-2">
            <p className="text-sm font-medium">Column I</p>
            {left.fields.map((f, i) => (
              <div key={f.id} className="flex items-start gap-2">
                <span className="w-6 pt-2 font-semibold">{leftValues[i]?.id}</span>
                <div className="grid flex-1 gap-1">
                  {mode !== 'second' && <Input {...form.register(`matchLeft.${i}.text`)} placeholder="Item text" aria-label={`Column I ${leftValues[i]?.id}`} />}
                  {trInput('matchLeft', leftValues[i]?.id)}
                </div>
                <NativeSelect aria-label={`Match for ${leftValues[i]?.id}`} className="w-20"
                              value={pairs[leftValues[i]?.id] ?? ''}
                              onChange={(e) => form.setValue('pairs', { ...pairs, [leftValues[i].id]: e.target.value }, { shouldDirty: true })}>
                  <option value="">→</option>
                  {rightValues.map((r) => <option key={r.id} value={r.id}>{r.id}</option>)}
                </NativeSelect>
                <Button type="button" variant="ghost" size="icon" aria-label="Remove" disabled={left.fields.length <= 1}
                        onClick={() => {
                          const id = leftValues[i].id
                          left.remove(i)
                          const { [id]: _removed, ...rest } = pairs
                          void _removed
                          form.setValue('pairs', rest, { shouldDirty: true })
                        }}><Trash2 /></Button>
              </div>
            ))}
            {left.fields.length < 10 && (
              <Button type="button" variant="outline" size="sm"
                      onClick={() => left.append({ id: leftIds.find((id) => !leftValues.some((l) => l.id === id)) ?? 'Z', text: '' })}>
                <Plus /> Add item
              </Button>
            )}
          </div>
          <div className="space-y-2">
            <p className="text-sm font-medium">Column II</p>
            {right.fields.map((f, i) => (
              <div key={f.id} className="flex items-start gap-2">
                <span className="w-6 pt-2 font-semibold">{rightValues[i]?.id}</span>
                <div className="grid flex-1 gap-1">
                  {mode !== 'second' && <Input {...form.register(`matchRight.${i}.text`)} placeholder="Item text" aria-label={`Column II ${rightValues[i]?.id}`} />}
                  {trInput('matchRight', rightValues[i]?.id)}
                </div>
                <Button type="button" variant="ghost" size="icon" aria-label="Remove" disabled={right.fields.length <= 1}
                        onClick={() => right.remove(i)}><Trash2 /></Button>
              </div>
            ))}
            {right.fields.length < 10 && (
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

function MetadataCard({ form }: { form: FormApi }) {
  const { register, control, formState: { errors } } = form
  const sourceType = useWatch({ control, name: 'sourceType' })
  return (
    <Card>
      <CardHeader><CardTitle>Marks &amp; metadata</CardTitle></CardHeader>
      <CardContent className="grid gap-4 sm:grid-cols-2">
        <FormField id="marks" label="Marks" error={errors.marks} hint="Blank uses the type's default (+4)">
          <Input id="marks" inputMode="decimal" {...register('marks')} aria-invalid={!!errors.marks} />
        </FormField>
        <FormField id="negativeMarks" label="Negative marks" error={errors.negativeMarks} hint="Blank uses the type's default">
          <Input id="negativeMarks" inputMode="decimal" {...register('negativeMarks')} aria-invalid={!!errors.negativeMarks} />
        </FormField>
        <FormField id="expectedTimeSec" label="Expected time (seconds)" error={errors.expectedTimeSec} hint="How long a well-prepared student needs">
          <Input id="expectedTimeSec" inputMode="numeric" {...register('expectedTimeSec')} aria-invalid={!!errors.expectedTimeSec} />
        </FormField>
        <FormField id="cognitiveLevel" label="Cognitive level"
                   hint={COGNITIVE_LEVELS.find((c) => c.value === form.getValues('cognitiveLevel'))?.hint}>
          <NativeSelect id="cognitiveLevel" {...register('cognitiveLevel')}>
            <option value="">Not set</option>
            {COGNITIVE_LEVELS.map((c) => <option key={c.value} value={c.value}>{c.label}</option>)}
          </NativeSelect>
        </FormField>
        <FormField id="sourceType" label="Source">
          <NativeSelect id="sourceType" {...register('sourceType')}>
            <option value="">Not set</option>
            {SOURCE_TYPES.map((s) => <option key={s.value} value={s.value}>{s.label}</option>)}
          </NativeSelect>
        </FormField>
        <FormField id="source" label={sourceType === 'PYQ' ? 'Exam' : 'Source details'} error={errors.source}
                   hint={sourceType === 'PYQ' ? 'e.g. JEE Main' : sourceType === 'COACHING' ? 'Institute or sheet name' : sourceType === 'BOOK' ? 'Book and author' : undefined}>
          <Input id="source" {...register('source')} />
        </FormField>
        <FormField id="year" label="Year" error={errors.year}>
          <Input id="year" inputMode="numeric" {...register('year')} aria-invalid={!!errors.year} />
        </FormField>
        {sourceType === 'PYQ' && (
          <FormField id="pyqShift" label="Date / shift" error={errors.pyqShift} hint="e.g. 27 Jan 2024, Shift 1">
            <Input id="pyqShift" {...register('pyqShift')} />
          </FormField>
        )}
        <div className="sm:col-span-2">
          <FormField id="concepts" label="Concepts" error={errors.concepts} hint="Comma separated, e.g. equations of motion, free fall. Used for weak-concept analysis.">
            <Input id="concepts" {...register('concepts')} aria-invalid={!!errors.concepts} />
          </FormField>
        </div>
        <div className="sm:col-span-2">
          <FormField id="tags" label="Tags" error={errors.tags} hint="Comma separated, e.g. important, revision">
            <Input id="tags" {...register('tags')} aria-invalid={!!errors.tags} />
          </FormField>
        </div>
      </CardContent>
    </Card>
  )
}

/** Live KaTeX preview, rendered the way students will see it, in either language. */
function Preview({ control }: { control: Control<QuestionFormValues> }) {
  const v = useWatch({ control }) as QuestionFormValues
  const [lang, setLang] = useState<'primary' | 'second'>('primary')
  const choice = v.type === 'SINGLE_CORRECT' || v.type === 'MULTIPLE_CORRECT'
  const tr = v.bilingual && lang === 'second' ? v.tr : null
  const pick = (primary: string | undefined, translated: string | undefined) => (tr && translated?.trim() ? translated : primary)
  return (
    <div className="space-y-4 text-sm">
      <div className="flex items-center justify-between">
        <p className="font-semibold">Preview</p>
        {v.bilingual && (
          <div className="bg-muted inline-flex rounded-md p-0.5 text-xs" role="tablist" aria-label="Preview language">
            {(['primary', 'second'] as const).map((l) => (
              <button key={l} type="button" role="tab" aria-selected={lang === l} onClick={() => setLang(l)}
                      className={cn('rounded px-2 py-0.5 font-medium', lang === l ? 'bg-background shadow-sm' : 'text-muted-foreground')}>
                {LANGUAGE_LABEL[l === 'primary' ? v.language : secondLanguage(v.language)]}
              </button>
            ))}
          </div>
        )}
      </div>
      {v.type === 'PARAGRAPH' ? <MathText text={pick(v.paragraph, v.tr?.paragraph) || '*Passage…*'} />
        : <MathText text={pick(v.text, v.tr?.text) || '*Question text…*'} />}
      {v.images?.map((img) => img?.url && <img key={img.url} src={img.url} alt="" className="max-h-48 rounded border bg-white" />)}
      {choice && (
        <ul className="space-y-2">
          {v.options?.map((o, i) => (
            <li key={i} className={cn('flex gap-2 rounded border p-2', o?.id && v.correct?.includes(o.id) && 'border-success bg-success/10')}>
              <span className="font-semibold">({i + 1})</span>
              <div className="min-w-0 flex-1">
                <MathText text={pick(o?.text, v.tr?.options?.[o?.id])} as="span" />
                {o?.image && <img src={o.image} alt="" className="mt-1 max-h-24 rounded border" />}
              </div>
              {o?.pinned && v.shuffleOptions && <Pin className="text-muted-foreground size-3.5" aria-label="Stays in place" />}
            </li>
          ))}
        </ul>
      )}
      {v.type === 'MATCH' && (
        <div className="grid grid-cols-2 gap-3">
          <ul className="space-y-1">{v.matchLeft?.map((l, i) => <li key={i}><strong>{l?.id}.</strong> <MathText text={pick(l?.text, v.tr?.matchLeft?.[l?.id])} as="span" /></li>)}</ul>
          <ul className="space-y-1">{v.matchRight?.map((r, i) => <li key={i}><strong>{r?.id}.</strong> <MathText text={pick(r?.text, v.tr?.matchRight?.[r?.id])} as="span" /></li>)}</ul>
        </div>
      )}
      {v.type === 'NUMERICAL' && (
        <p>
          Answer: <strong>{v.numMode === 'RANGE' ? `${v.numMin || '?'} to ${v.numMax || '?'}` : `${v.numValue || '?'}${v.tolerance ? ` ± ${v.tolerance}` : ''}`}</strong>
          <span className="text-muted-foreground"> ({v.numericFormat === 'INTEGER' ? 'integer' : 'decimal'})</span>
        </p>
      )}
      {(v.solutionText || (tr && v.tr?.solution)) && v.type !== 'PARAGRAPH' && (
        <div className="bg-muted/50 rounded border p-3">
          <p className="mb-1 font-semibold">Solution</p>
          <MathText text={pick(v.solutionText, v.tr?.solution)} />
        </div>
      )}
    </div>
  )
}
