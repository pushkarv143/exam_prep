import { create } from 'zustand'
import { createJSONStorage, persist } from 'zustand/middleware'

export const THEME_STORAGE_KEY = 'examprep-theme'

export type ThemeMode = 'light' | 'dark' | 'system'

/** Brand colour presets. The CSS for each lives in index.css under [data-accent=…]. */
export const ACCENTS = [
  { id: 'indigo', label: 'Indigo', swatch: 'oklch(0.457 0.24 277)' },
  { id: 'blue', label: 'Blue', swatch: 'oklch(0.488 0.217 264)' },
  { id: 'violet', label: 'Violet', swatch: 'oklch(0.491 0.27 292)' },
  { id: 'teal', label: 'Teal', swatch: 'oklch(0.511 0.096 186)' },
  { id: 'emerald', label: 'Emerald', swatch: 'oklch(0.508 0.118 165.6)' },
  { id: 'rose', label: 'Rose', swatch: 'oklch(0.514 0.222 16.9)' },
  { id: 'orange', label: 'Orange', swatch: 'oklch(0.553 0.195 38.4)' },
] as const

export type Accent = (typeof ACCENTS)[number]['id']

interface ThemeState {
  mode: ThemeMode
  accent: Accent
  setMode: (mode: ThemeMode) => void
  setAccent: (accent: Accent) => void
}

/** Per-device appearance preference (localStorage), shared by all tabs. */
export const useThemeStore = create<ThemeState>()(
  persist(
    (set) => ({
      mode: 'system',
      accent: 'indigo',
      setMode: (mode) => set({ mode }),
      setAccent: (accent) => set({ accent }),
    }),
    { name: THEME_STORAGE_KEY, storage: createJSONStorage(() => localStorage) },
  ),
)

const darkQuery = () => window.matchMedia('(prefers-color-scheme: dark)')

export function resolvedDark(mode: ThemeMode): boolean {
  return mode === 'dark' || (mode === 'system' && darkQuery().matches)
}

/** Writes the preference to <html>: the `dark` class, `data-accent` and the native color-scheme. */
export function applyTheme({ mode, accent }: { mode: ThemeMode; accent: Accent }) {
  const root = document.documentElement
  const dark = resolvedDark(mode)
  root.classList.toggle('dark', dark)
  root.dataset.accent = accent
  root.style.colorScheme = dark ? 'dark' : 'light'
  const meta = document.querySelector('meta[name="theme-color"]')
  const bar = getComputedStyle(root).getPropertyValue('--primary').trim()
  if (meta && bar) meta.setAttribute('content', bar)
}

/**
 * Keeps <html> in sync with the store: on change, when the OS switches between light and
 * dark (system mode), and when another tab changes the preference.
 */
export function initTheme() {
  applyTheme(useThemeStore.getState())
  useThemeStore.subscribe((s) => applyTheme(s))
  darkQuery().addEventListener('change', () => applyTheme(useThemeStore.getState()))
  window.addEventListener('storage', (e) => {
    if (e.key === THEME_STORAGE_KEY) void useThemeStore.persist.rehydrate()
  })
}
