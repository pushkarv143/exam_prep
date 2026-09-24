import { useCallback } from 'react'
import { useQueryClient } from '@tanstack/react-query'
import { useNavigate } from 'react-router-dom'
import { authApi } from '@/api/auth'
import { useAuthStore } from '@/store/auth'

/**
 * Revokes the session server-side (blacklists the access token, deletes the refresh
 * token), then clears local state and cached data. Local logout happens even if the call
 * fails (e.g. offline), since the tokens expire on their own.
 */
export function useLogout() {
  const qc = useQueryClient()
  const navigate = useNavigate()
  return useCallback(async (redirectTo = '/login') => {
    const { refreshToken, accessToken } = useAuthStore.getState()
    if (accessToken) {
      try {
        await authApi.logout(refreshToken)
      } catch {
        // best effort
      }
    }
    useAuthStore.getState().clear()
    qc.clear()
    navigate(redirectTo, { replace: true })
  }, [navigate, qc])
}
