import axios, { AxiosError, type AxiosRequestConfig, type InternalAxiosRequestConfig } from 'axios'
import { readPersistedTokens, useAuthStore } from '@/store/auth'
import type { ApiEnvelope } from '@/types/api'
import type { AuthResponse } from '@/types/domain'
import { toApiError } from './errors'

export const API_BASE_URL = import.meta.env.VITE_API_BASE_URL ?? '/api/v1'

export const api = axios.create({ baseURL: API_BASE_URL, timeout: 20_000 })

type RetriableConfig = InternalAxiosRequestConfig & { _retried?: boolean }

/** Codes that mean "this session is over" and should send the user to the login page. */
const SESSION_DEAD = new Set(['SESSION_REVOKED', 'TOKEN_INVALID'])

let onSessionExpired: () => void = () => {
  if (!window.location.pathname.startsWith('/login')) {
    window.location.assign('/login?reason=session&next=' + encodeURIComponent(window.location.pathname))
  }
}

/** Lets the app replace the redirect (e.g. to also clear the query cache). */
export function setSessionExpiredHandler(handler: () => void) {
  onSessionExpired = handler
}

api.interceptors.request.use((config) => {
  const token = useAuthStore.getState().accessToken
  if (token && !config.headers.Authorization) {
    config.headers.Authorization = `Bearer ${token}`
  }
  return config
})

api.interceptors.response.use(
  (response) => response,
  async (error: AxiosError<ApiEnvelope<unknown>>) => {
    const config = error.config as RetriableConfig | undefined
    const status = error.response?.status
    const code = error.response?.data?.error?.code

    if (status === 401 && code === 'TOKEN_EXPIRED' && config && !config._retried && !isAuthCall(config)) {
      config._retried = true
      try {
        const token = await refreshAccessToken()
        config.headers.Authorization = `Bearer ${token}`
        return api(config)
      } catch (refreshError) {
        const failure = toApiError(refreshError)
        // Only a definitive "no" from the server ends the session. A network blip must
        // not log a student out mid-exam; the next call simply tries again.
        if (failure.isNetwork || failure.status >= 500) {
          throw failure
        }
        expireSession()
        throw toApiError(error)
      }
    }
    if (status === 401 && code && SESSION_DEAD.has(code) && config?.headers?.Authorization) {
      expireSession()
    }
    throw toApiError(error)
  },
)

function isAuthCall(config: AxiosRequestConfig) {
  return !!config.url && /\/auth\/(login|register|refresh)/.test(config.url)
}

function expireSession() {
  useAuthStore.getState().clear()
  onSessionExpired()
}

// ---------------------------------------------------------------------------------------
// Token refresh
// ---------------------------------------------------------------------------------------

let inFlight: Promise<string> | null = null

/**
 * Returns a fresh access token. Refresh tokens are single-use (rotated server-side), so
 * two concurrent refreshes with the same token would make the second one fail and log the
 * user out. There are two layers of protection:
 *  1. Within a tab, concurrent 401s share one in-flight promise.
 *  2. Across tabs, the Web Locks API serialises refreshes. Inside the lock we re-read
 *     localStorage; if another tab already rotated the token, we adopt its result instead
 *     of spending our now-stale refresh token.
 */
export function refreshAccessToken(): Promise<string> {
  inFlight ??= runRefresh().finally(() => {
    inFlight = null
  })
  return inFlight
}

async function runRefresh(): Promise<string> {
  const usedRefreshToken = useAuthStore.getState().refreshToken
  const task = async (): Promise<string> => {
    const latest = readPersistedTokens()
    if (latest.refreshToken && latest.accessToken && latest.refreshToken !== usedRefreshToken) {
      useAuthStore.getState().setTokens(latest.accessToken, latest.refreshToken)   // another tab won
      return latest.accessToken
    }
    if (!usedRefreshToken) {
      throw new Error('No refresh token')
    }
    // Plain axios: the refresh call must not go through our own interceptors.
    const res = await axios.post<ApiEnvelope<AuthResponse>>(`${API_BASE_URL}/auth/refresh`,
      { refreshToken: usedRefreshToken }, { timeout: 15_000 })
    const auth = res.data.data!
    useAuthStore.getState().setSession(auth)
    return auth.accessToken
  }
  const locks = typeof navigator !== 'undefined' ? navigator.locks : undefined
  return locks ? locks.request('examprep-token-refresh', task) : task()
}

// ---------------------------------------------------------------------------------------
// Typed helpers: unwrap the { success, data } envelope
// ---------------------------------------------------------------------------------------

export async function apiGet<T>(url: string, params?: object): Promise<T> {
  const res = await api.get<ApiEnvelope<T>>(url, { params })
  return res.data.data as T
}

export async function apiPost<T>(url: string, body?: unknown): Promise<T> {
  const res = await api.post<ApiEnvelope<T>>(url, body)
  return res.data.data as T
}

export async function apiPut<T>(url: string, body?: unknown): Promise<T> {
  const res = await api.put<ApiEnvelope<T>>(url, body)
  return res.data.data as T
}

export async function apiPatch<T>(url: string, body?: unknown): Promise<T> {
  const res = await api.patch<ApiEnvelope<T>>(url, body)
  return res.data.data as T
}

export async function apiDelete<T>(url: string): Promise<T> {
  const res = await api.delete<ApiEnvelope<T>>(url)
  return res.data.data as T
}
