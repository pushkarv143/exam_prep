import { useAdminExams, useCatalogTree } from '@/api/admin'
import { NativeSelect } from '@/components/ui/native-select'
import { cn } from '@/lib/utils'

export interface CatalogSelection {
  examId?: string
  subjectId?: string
  chapterId?: string
  topicId?: string
}

type Level = 'exam' | 'subject' | 'chapter' | 'topic'

/**
 * Cascading exam → subject → chapter → topic selects. Changing a level clears the levels
 * below it. `depth` limits how deep it goes (e.g. "subject" for a test section).
 */
export function CatalogPicker({ value, onChange, depth = 'topic', anyLabel = 'All', className, invalid, lockExam }: {
  value: CatalogSelection
  onChange: (v: CatalogSelection) => void
  depth?: Level
  anyLabel?: string
  className?: string
  invalid?: boolean
  lockExam?: boolean
}) {
  const exams = useAdminExams()
  const tree = useCatalogTree(value.examId)
  const subjects = tree.data?.subjects ?? []
  const subject = subjects.find((s) => s.id === value.subjectId)
  const chapter = subject?.chapters.find((c) => c.id === value.chapterId)
  const levels: Level[] = ['exam', 'subject', 'chapter', 'topic']
  const show = (l: Level) => levels.indexOf(l) <= levels.indexOf(depth)

  return (
    <div className={cn('grid gap-2 sm:grid-cols-2 lg:grid-cols-4', className)}>
      <NativeSelect aria-label="Exam" value={value.examId ?? ''} disabled={lockExam}
                    onChange={(e) => onChange({ examId: e.target.value || undefined })}>
        <option value="">{anyLabel === 'All' ? 'All exams' : 'Select exam'}</option>
        {exams.data?.map((e) => <option key={e.id} value={e.id}>{e.name}</option>)}
      </NativeSelect>
      {show('subject') && (
        <NativeSelect aria-label="Subject" value={value.subjectId ?? ''} disabled={!value.examId}
                      onChange={(e) => onChange({ examId: value.examId, subjectId: e.target.value || undefined })}>
          <option value="">{anyLabel === 'All' ? 'All subjects' : 'Select subject'}</option>
          {subjects.map((s) => <option key={s.id} value={s.id}>{s.name}</option>)}
        </NativeSelect>
      )}
      {show('chapter') && (
        <NativeSelect aria-label="Chapter" value={value.chapterId ?? ''} disabled={!subject}
                      onChange={(e) => onChange({ ...value, chapterId: e.target.value || undefined, topicId: undefined })}>
          <option value="">{anyLabel === 'All' ? 'All chapters' : 'Select chapter'}</option>
          {subject?.chapters.map((c) => <option key={c.id} value={c.id}>{c.name}</option>)}
        </NativeSelect>
      )}
      {show('topic') && (
        <NativeSelect aria-label="Topic" value={value.topicId ?? ''} disabled={!chapter} aria-invalid={invalid || undefined}
                      onChange={(e) => onChange({ ...value, topicId: e.target.value || undefined })}>
          <option value="">{anyLabel === 'All' ? 'All topics' : 'Select topic'}</option>
          {chapter?.topics.map((t) => <option key={t.id} value={t.id}>{t.name}</option>)}
        </NativeSelect>
      )}
    </div>
  )
}
