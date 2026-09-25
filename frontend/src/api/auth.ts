import { apiGet, apiPatch, apiPost } from '@/lib/api'
import type { AuthResponse, LoginResponse, User } from '@/types/domain'

export interface LoginInput {
  identifier: string
  password: string
}

export interface RegisterInput {
  fullName: string
  email: string
  phone?: string
  password: string
  targetExamCode?: string
}

export interface ProfileInput {
  fullName?: string
  phone?: string
  targetExamCode?: string
  city?: string
  state?: string
}

export const authApi = {
  login: (input: LoginInput) => apiPost<LoginResponse>('/auth/login', input),
  loginMfa: (mfaToken: string, code: string) => apiPost<AuthResponse>('/auth/login/mfa', { mfaToken, code }),
  register: (input: RegisterInput) => apiPost<AuthResponse>('/auth/register', input),
  logout: (refreshToken: string | null) => apiPost<void>('/auth/logout', { refreshToken }),
  forgotPassword: (email: string) => apiPost<void>('/auth/forgot-password', { email }),
  resetPassword: (token: string, newPassword: string) => apiPost<void>('/auth/reset-password', { token, newPassword }),
  me: () => apiGet<User>('/users/me'),
  updateMe: (input: ProfileInput) => apiPatch<User>('/users/me', input),
  changePassword: (currentPassword: string, newPassword: string) =>
    apiPost<void>('/users/me/password', { currentPassword, newPassword }),
}
