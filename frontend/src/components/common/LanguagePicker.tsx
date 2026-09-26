import { Languages } from 'lucide-react'
import { cn } from '@/lib/utils'
import { useQuestionLanguage } from '@/store/language'
import type { Language } from '@/types/exam'

const OPTIONS: { value: Language; label: string; hint: string }[] = [
  { value: 'EN', label: 'English', hint: 'English' },
  { value: 'HI', label: 'हिन्दी', hint: 'Hindi' },
]

/**
 * "Test language: English | हिन्दी" as a segmented control. Always offers both languages, because a
 * student picks before seeing a paper. The choice is the same per-device setting the exam and solutions
 * use, so it carries into every test; untranslated questions fall back to their primary text.
 */
export function LanguagePicker({ className, showHelp = true }: { className?: string; showHelp?: boolean }) {
  const { language, setLanguage } = useQuestionLanguage()
  return (
    <div className={cn('space-y-2', className)}>
      <p id="test-language-label" className="flex items-center gap-1.5 text-sm font-medium">
        <Languages className="size-4" aria-hidden /> Test language
      </p>
      <div role="radiogroup" aria-labelledby="test-language-label" className="bg-muted inline-flex rounded-lg p-1">
        {OPTIONS.map((o) => {
          const active = language === o.value
          return (
            <button key={o.value} type="button" role="radio" aria-checked={active} aria-label={o.hint}
                    onClick={() => setLanguage(o.value)}
                    className={cn('min-w-24 rounded-md px-4 py-1.5 text-sm font-medium transition-colors',
                      'focus-visible:ring-ring focus-visible:ring-2 focus-visible:outline-none',
                      active ? 'bg-primary text-primary-foreground shadow-sm' : 'text-muted-foreground hover:text-foreground')}>
              {o.label}
            </button>
          )
        })}
      </div>
      {showHelp && (
        <p className="text-muted-foreground text-xs">
          Questions and solutions are shown in this language. You can switch during the test too. A question
          that isn't translated yet is shown in its original language.
        </p>
      )}
    </div>
  )
}
