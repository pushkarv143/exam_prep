import axios, { AxiosError, type AxiosAdapter, type AxiosResponse, type InternalAxiosRequestConfig } from 'axios'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { api, apiGet, setSessionExpiredHandler } from '@/lib/api'
import { AUTH_STORAGE_KEY, useAuthStore } from '@/store/auth'
import type { User } from '@/types/domain'

const user: User = {
  id: 'u1', email: 'a@b.com', fullName: 'A B', roles: ['STUDENT'], status: 'ACTIVE', emailVerified: true,
  createdAt: '2026-01-01T00:00:00Z',
}

function ok(config: InternalAxiosRequestConfig, data: unknown, status = 200): AxiosResponse {
  return { data: { success: true, data, timestamp: '' }, status, statusText: 'OK', headers: {}, config }
}

function fail(config: InternalAxiosRequestConfig, status: number, code: string): never {
  const response = {
    data: { success: false, error: { code, message: code }, timestamp: '' },
    status, statusText: 'ERR', headers: {}, config,
  } as AxiosResponse
  throw new AxiosError(code, 'ERR_BAD_REQUEST', config, null, response)
}

function authResponse(access: string, refresh: string) {
  return { accessToken: access, refreshToken: refresh, tokenType: 'Bearer', accessTokenExpiresAt: '',
    refreshTokenExpiresAt: '', user }
}

/** A fake backend: /users/me needs token "fresh"; /auth/refresh rotates "r1" to "fresh"/"r2" once. */
function installBackend(opts: { refreshBehaviour?: 'ok' | 'reject' | 'network' } = {}) {
  const calls = { refresh: 0, me: [] as string[] }
  const adapter: AxiosAdapter = async (config) => {
    if (config.url?.endsWith('/auth/refresh')) {
      calls.refresh++
      if (opts.refreshBehaviour === 'reject') fail(config, 401, 'TOKEN_INVALID')
      if (opts.refreshBehaviour === 'network') throw new AxiosError('Network Error', 'ERR_NETWORK', config)
      return ok(config, authResponse('fresh', 'r2'))
    }
    const auth = String(config.headers?.Authorization ?? '')
    calls.me.push(auth)
    if (auth !== 'Bearer fresh') fail(config, 401, 'TOKEN_EXPIRED')
    return ok(config, user)
  }
  api.defaults.adapter = adapter
  axios.defaults.adapter = adapter   // the refresh call uses plain axios, without our interceptors
  return calls
}

describe('api client token refresh', () => {
  const expired = vi.fn()

  beforeEach(() => {
    expired.mockReset()
    setSessionExpiredHandler(expired)
    useAuthStore.setState({ accessToken: 'stale', refreshToken: 'r1', user })
  })

  it('refreshes once for concurrent 401s and retries every request with the new token', async () => {
    const calls = installBackend()
    const results = await Promise.all([apiGet<User>('/users/me'), apiGet<User>('/users/me'), apiGet<User>('/users/me')])

    expect(results.every((u) => u.id === 'u1')).toBe(true)
    expect(calls.refresh).toBe(1)
    expect(useAuthStore.getState().accessToken).toBe('fresh')
    expect(useAuthStore.getState().refreshToken).toBe('r2')
    expect(calls.me.filter((a) => a === 'Bearer fresh')).toHaveLength(3)
  })

  it('adopts tokens another tab already rotated instead of spending the stale refresh token', async () => {
    const calls = installBackend()
    // Simulate another tab having refreshed and persisted new tokens.
    localStorage.setItem(AUTH_STORAGE_KEY, JSON.stringify({
      state: { accessToken: 'fresh', refreshToken: 'r2', user }, version: 0,
    }))

    await apiGet<User>('/users/me')
    expect(calls.refresh).toBe(0)
    expect(useAuthStore.getState().refreshToken).toBe('r2')
  })

  it('ends the session when the server rejects the refresh token', async () => {
    installBackend({ refreshBehaviour: 'reject' })
    await expect(apiGet('/users/me')).rejects.toMatchObject({ code: 'TOKEN_EXPIRED' })
    expect(useAuthStore.getState().accessToken).toBeNull()
    expect(expired).toHaveBeenCalledOnce()
  })

  it('keeps the session on a network failure during refresh (no logout mid-exam)', async () => {
    installBackend({ refreshBehaviour: 'network' })
    await expect(apiGet('/users/me')).rejects.toMatchObject({ code: 'NETWORK_ERROR' })
    expect(useAuthStore.getState().refreshToken).toBe('r1')
    expect(expired).not.toHaveBeenCalled()
  })

  it('logs out on SESSION_REVOKED (e.g. logged in on another device)', async () => {
    api.defaults.adapter = async (config) => fail(config, 401, 'SESSION_REVOKED')
    await expect(apiGet('/users/me')).rejects.toMatchObject({ code: 'SESSION_REVOKED' })
    expect(expired).toHaveBeenCalledOnce()
  })
})
