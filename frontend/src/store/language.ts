import { create } from 'zustand'
import { createJSONStorage, persist } from 'zustand/middleware'
import type { Language } from '@/types/exam'

interface LanguageState {
  /** Language the student reads questions in (exam and solutions). */
  language: Language
  setLanguage: (language: Language) => void
}

/** Per-device question language, like the NTA "View in" switch. Falls back per question when a translation is missing. */
export const useQuestionLanguage = create<LanguageState>()(
  persist(
    (set) => ({
      language: 'EN',
      setLanguage: (language) => set({ language }),
    }),
    { name: 'examprep-question-language', storage: createJSONStorage(() => localStorage) },
  ),
)
