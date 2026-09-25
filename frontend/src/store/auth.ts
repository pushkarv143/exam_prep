import { create } from 'zustand'
import { createJSONStorage, persist } from 'zustand/middleware'
import type { AuthResponse, Role, User } from '@/types/domain'

export const AUTH_STORAGE_KEY = 'examprep-auth'

interface AuthState {
  accessToken: string | null
  refreshToken: string | null
  user: User | null
  setSession: (auth: AuthResponse) => void
  setTokens: (accessToken: string, refreshToken: string) => void
  setUser: (user: User) => void
  clear: () => void
}

/**
 * Session state persisted to localStorage, so it survives reloads and is shared by tabs.
 *
 * Security note: tokens in localStorage are readable by any script running on the page,
 * so XSS is the main threat. React escapes output by default, and we never inject
 * unsanitised HTML (KaTeX output is generated, not user HTML). Moving the refresh token
 * to an httpOnly cookie is a possible hardening step that needs a backend change.
 */
export const useAuthStore = create<AuthState>()(
  persist(
    (set) => ({
      accessToken: null,
      refreshToken: null,
      user: null,
      setSession: (auth) => set({ accessToken: auth.accessToken, refreshToken: auth.refreshToken, user: auth.user }),
      setTokens: (accessToken, refreshToken) => set({ accessToken, refreshToken }),
      setUser: (user) => set({ user }),
      clear: () => set({ accessToken: null, refreshToken: null, user: null }),
    }),
    { name: AUTH_STORAGE_KEY, storage: createJSONStorage(() => localStorage) },
  ),
)

/** What another tab most recently wrote (bypasses this tab's in-memory copy). */
export function readPersistedTokens(): { accessToken: string | null; refreshToken: string | null } {
  try {
    const raw = localStorage.getItem(AUTH_STORAGE_KEY)
    const state = raw ? JSON.parse(raw).state : null
    return { accessToken: state?.accessToken ?? null, refreshToken: state?.refreshToken ?? null }
  } catch {
    return { accessToken: null, refreshToken: null }
  }
}

// Cross-tab sync: logging in or out in one tab updates every other open tab.
if (typeof window !== 'undefined') {
  window.addEventListener('storage', (e) => {
    if (e.key === AUTH_STORAGE_KEY) {
      void useAuthStore.persist.rehydrate()
    }
  })
}

export const useIsAuthenticated = () => useAuthStore((s) => s.accessToken !== null)

export function hasRole(user: User | null, ...roles: Role[]): boolean {
  return !!user && user.roles.some((r) => roles.includes(r))
}

/** Any role other than STUDENT is a staff role. What staff may do is decided by permissions (GET /me/access). */
export const isStaff = (user: User | null) => !!user && user.roles.some((r) => r !== 'STUDENT')

export const isSuperAdmin = (user: User | null) => hasRole(user, 'SUPER_ADMIN')
