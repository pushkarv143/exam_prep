import { keepPreviousData, useQuery } from '@tanstack/react-query'
import { api, apiGet } from '@/lib/api'
import { ApprovalPendingError } from './admin'
import type { ApiEnvelope, Page } from '@/types/api'
import { useAuthStore } from '@/store/auth'
import type {
  ApprovalPolicy, ApprovalRequest, ApprovalStatus, ApprovalSummary, AuditEntry, AuditFilterInput,
  IpAllowlistState, Job, JobStatus, MfaSetup, MfaStatus, MyAccess, PermissionGroup, Role, Session, UserAccess,
} from './types'

/**
 * Admin Portal 2.0 API: access, roles, audit, approvals, jobs, security.
 * Destructive calls take an optional {@code reason} (sent as X-Reason, stored in the audit log)
 * and money/rank-changing calls send an Idempotency-Key.
 */
export interface WriteOptions {
  reason?: string
  idempotent?: boolean
}

function headers(o?: WriteOptions): Record<string, string> {
  const h: Record<string, string> = {}
  if (o?.reason?.trim()) h['X-Reason'] = encodeURIComponent(o.reason.trim())
  if (o?.idempotent) h['Idempotency-Key'] = crypto.randomUUID()
  return h
}

async function send<T>(method: 'post' | 'put' | 'delete', url: string, body?: unknown, o?: WriteOptions): Promise<T> {
  const res = method === 'delete'
    ? await api.delete<ApiEnvelope<T>>(url, { headers: headers(o) })
    : await api[method]<ApiEnvelope<T>>(url, body, { headers: headers(o) })
  const data = res.data.data as { approvalRequired?: boolean; request?: { id: string }; message?: string }
  if (res.status === 202 && data?.approvalRequired) {
    throw new ApprovalPendingError(data.request?.id ?? '', data.message ?? 'Sent for approval')
  }
  return res.data.data as T
}

export const portalApi = {
  // access
  myAccess: () => apiGet<MyAccess>('/me/access'),

  // roles & permissions
  permissions: () => apiGet<PermissionGroup[]>('/admin/permissions'),
  roles: () => apiGet<Role[]>('/admin/roles'),
  createRole: (body: { name: string; displayName: string; description?: string; subjectScoped: boolean;
    permissions: string[]; copyFrom?: string }) => send<Role>('post', '/admin/roles', body),
  updateRole: (name: string, body: { displayName: string; description?: string; subjectScoped?: boolean;
    permissions?: string[] }, o?: WriteOptions) => send<Role>('put', `/admin/roles/${name}`, body, o),
  deleteRole: (name: string, o?: WriteOptions) => send<void>('delete', `/admin/roles/${name}`, undefined, o),
  userAccess: (userId: string) => apiGet<UserAccess>(`/admin/users/${userId}/access`),

  // audit
  audit: (f: AuditFilterInput, cursor?: string | null, size = 50) =>
    apiGet<{ items: AuditEntry[]; nextCursor: string | null }>('/admin/audit', clean({ ...f, cursor, size })),
  auditEntry: (id: string) => apiGet<AuditEntry>(`/admin/audit/${id}`),
  exportAudit: (f: AuditFilterInput) => send<Job>('post', '/admin/audit/export', clean(f)),

  // approvals
  approvals: (view: 'to-decide' | 'mine' | 'all', status: ApprovalStatus | '' = '', page = 0) =>
    apiGet<Page<ApprovalRequest>>('/admin/approvals', clean({ view, status, page, size: 20 })),
  approval: (id: string) => apiGet<ApprovalRequest>(`/admin/approvals/${id}`),
  approvalSummary: () => apiGet<ApprovalSummary>('/admin/approvals/summary'),
  approve: (id: string, comment?: string) =>
    send<ApprovalRequest>('post', `/admin/approvals/${id}/approve`, { comment }, { idempotent: true }),
  reject: (id: string, comment: string) =>
    send<ApprovalRequest>('post', `/admin/approvals/${id}/reject`, { comment }, { idempotent: true }),
  cancelApproval: (id: string) => send<ApprovalRequest>('post', `/admin/approvals/${id}/cancel`),
  policies: () => apiGet<ApprovalPolicy[]>('/admin/approval-policies'),
  updatePolicy: (action: string, body: { enabled: boolean; threshold: number | null; expiryHours: number }) =>
    send<ApprovalPolicy>('put', `/admin/approval-policies/${action}`, body),

  // jobs
  jobs: (p: { status?: JobStatus | ''; type?: string; mine?: boolean; page?: number }) =>
    apiGet<Page<Job>>('/admin/jobs', clean({ ...p, size: 25 })),
  job: (id: string) => apiGet<Job>(`/admin/jobs/${id}`),
  cancelJob: (id: string) => send<Job>('post', `/admin/jobs/${id}/cancel`),
  retryJob: (id: string) => send<Job>('post', `/admin/jobs/${id}/retry`),
  downloadArtifact: async (id: string) => {
    const res = await api.get<Blob>(`/admin/jobs/${id}/artifact`, { responseType: 'blob' })
    const disposition = String(res.headers['content-disposition'] ?? '')
    const match = /filename\*?=(?:UTF-8'')?"?([^";]+)"?/i.exec(disposition)
    return { blob: res.data, filename: match ? decodeURIComponent(match[1]) : `job-${id}` }
  },

  // my security
  mfaStatus: () => apiGet<MfaStatus>('/me/mfa'),
  mfaSetup: () => send<MfaSetup>('post', '/me/mfa/setup'),
  mfaConfirm: (code: string) => send<{ recoveryCodes: string[] }>('post', '/me/mfa/confirm', { code }),
  mfaRecoveryCodes: (code: string) => send<{ recoveryCodes: string[] }>('post', '/me/mfa/recovery-codes', { code }),
  mfaDisable: (code: string) => send<void>('post', '/me/mfa/disable', { code }),
  sessions: () => apiGet<Session[]>('/me/sessions'),
  revokeSession: (id: string) => send<void>('delete', `/me/sessions/${id}`),
  revokeAllSessions: (keepCurrent: boolean) =>
    send<{ revoked: number }>('post', `/me/sessions/revoke-all?keepCurrent=${keepCurrent}`),

  // organisation security
  ipAllowlist: () => apiGet<IpAllowlistState>('/admin/security/ip-allowlist'),
  addIp: (cidr: string, label: string) => send<unknown>('post', '/admin/security/ip-allowlist', { cidr, label }),
  removeIp: (id: string, o?: WriteOptions) => send<void>('delete', `/admin/security/ip-allowlist/${id}`, undefined, o),
  setIpAllowlist: (enabled: boolean, o?: WriteOptions) =>
    send<IpAllowlistState>('put', '/admin/security/ip-allowlist/enabled', { enabled }, o),
  resetUserMfa: (userId: string, o?: WriteOptions) =>
    send<void>('post', `/admin/security/users/${userId}/mfa/reset`, undefined, o),
  revokeUserSessions: (userId: string, o?: WriteOptions) =>
    send<{ revoked: number }>('post', `/admin/security/users/${userId}/sessions/revoke-all`, undefined, o),
  userSecurity: (userId: string) =>
    apiGet<{ mfa: MfaStatus; sessions: Session[] }>(`/admin/security/users/${userId}`),
}

function clean<T extends object>(params: T): Partial<T> {
  return Object.fromEntries(Object.entries(params).filter(([, v]) => v !== undefined && v !== null && v !== '')) as Partial<T>
}

export const portalKeys = {
  access: ['me', 'access'] as const,
  roles: ['admin', 'roles'] as const,
  permissions: ['admin', 'permissions'] as const,
  audit: (f: object) => ['admin', 'audit', f] as const,
  approvals: (view: string, status: string, page: number) => ['admin', 'approvals', view, status, page] as const,
  approval: (id: string) => ['admin', 'approval', id] as const,
  approvalSummary: ['admin', 'approvals', 'summary'] as const,
  policies: ['admin', 'approval-policies'] as const,
  jobs: (p: object) => ['admin', 'jobs', p] as const,
  job: (id: string) => ['admin', 'job', id] as const,
  mfa: ['me', 'mfa'] as const,
  sessions: ['me', 'sessions'] as const,
  ipAllowlist: ['admin', 'security', 'ip-allowlist'] as const,
}

/** What the signed-in user may do. Cached for a minute; refetched after 2FA changes. */
export function useMyAccess() {
  const token = useAuthStore((s) => s.accessToken)
  return useQuery({ queryKey: [...portalKeys.access, token], queryFn: portalApi.myAccess, enabled: !!token,
    staleTime: 60_000 })
}

/** Permission checks for the UI (the API enforces them again). */
export function usePermissions() {
  const access = useMyAccess()
  const perms = new Set(access.data?.permissions ?? [])
  return {
    access: access.data,
    loading: access.isPending,
    can: (p: string) => perms.has(p),
    any: (...ps: string[]) => ps.some((p) => perms.has(p)),
  }
}

export function useApprovalSummary(enabled: boolean) {
  return useQuery({ queryKey: portalKeys.approvalSummary, queryFn: portalApi.approvalSummary, enabled,
    refetchInterval: 60_000, placeholderData: keepPreviousData })
}
