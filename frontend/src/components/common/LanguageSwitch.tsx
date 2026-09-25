import { Languages } from 'lucide-react'
import { cn } from '@/lib/utils'
import { useQuestionLanguage } from '@/store/language'
import type { Language } from '@/types/exam'

const LABEL: Record<Language, string> = { EN: 'English', HI: 'हिन्दी' }

/** "View in: English | हिन्दी". Renders nothing unless the paper offers more than one language. */
export function LanguageSwitch({ languages, className, tone = 'default' }: {
  languages: Language[]; className?: string; tone?: 'default' | 'onPrimary'
}) {
  const { language, setLanguage } = useQuestionLanguage()
  if (languages.length < 2) return null
  return (
    <div className={cn('flex items-center gap-1.5 text-sm', className)}>
      <Languages className="size-4 shrink-0 opacity-80" aria-hidden />
      <label htmlFor="question-language" className="sr-only">View questions in</label>
      <select id="question-language" value={languages.includes(language) ? language : languages[0]}
              onChange={(e) => setLanguage(e.target.value as Language)}
              className={cn('h-8 rounded-md border px-2 text-sm',
                tone === 'onPrimary' ? 'bg-primary-foreground/10 border-primary-foreground/30 text-primary-foreground' : 'bg-background')}>
        {languages.map((l) => <option key={l} value={l} className="text-foreground">{LABEL[l]}</option>)}
      </select>
    </div>
  )
}
