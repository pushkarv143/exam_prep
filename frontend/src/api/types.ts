import type { components } from './schema'

/**
 * Types generated from the backend's OpenAPI document (`npm run gen:api`, into schema.d.ts).
 * Java records carry no "required" metadata, so the generator marks every field optional.
 * `Api<'Name'>` makes them required. Fields that really can be absent (decidedAt, error...)
 * are still handled with `??` where they are rendered.
 */
type Schemas = components['schemas']
export type Api<K extends keyof Schemas> = Required<Schemas[K]>
export type ApiPage<K extends keyof Schemas> = {
  content: Api<K>[]
  page: number
  size: number
  totalElements: number
  totalPages: number
  last: boolean
}

export type MyAccess = Api<'AccessDto'>
export type Role = Api<'RoleDto'>
export type PermissionGroup = { module: string; permissions: PermissionInfo[] }
export type PermissionInfo = Api<'PermissionDto'>
export type AuditEntry = Omit<Api<'AuditEntryDto'>, 'changes' | 'before' | 'after' | 'metadata'> & {
  changes: AuditChange[] | null
  before: unknown
  after: unknown
  metadata: Record<string, unknown> | null
}
export type AuditChange = { path: string; op: 'added' | 'removed' | 'changed' | 'reordered'; from?: unknown; to?: unknown }
export type AuditFilterInput = Partial<Api<'AuditFilter'>>
export type ApprovalRequest = Omit<Api<'ApprovalRequestDto'>, 'payload' | 'result'> & {
  payload: Record<string, unknown> | null
  result: Record<string, unknown> | null
}
export type ApprovalStatus = ApprovalRequest['status']
export type ApprovalPolicy = Api<'PolicyDto'>
export type ApprovalSummary = Api<'ApprovalSummary'>
export type Job = Omit<Api<'JobDto'>, 'params' | 'result' | 'artifact'> & {
  params: Record<string, unknown> | null
  result: Record<string, unknown> | null
  artifact: Api<'ArtifactDto'> | null
}
export type JobStatus = Job['status']
export type MfaStatus = Api<'MfaStatus'>
export type MfaSetup = Api<'MfaSetup'>
export type Session = Api<'SessionDto'>
export type IpAllowlistState = Omit<Api<'IpAllowlistState'>, 'entries'> & { entries: Api<'IpAllowlistEntry'>[] }
export type UserAccess = Api<'UserAccess'>

// ---- content studio (A2) -------------------------------------------------------------
export type QuestionActivity = Omit<Api<'ActivityDto'>, 'meta'> & { meta: Record<string, unknown> | null }
export type QuestionVersion = Api<'VersionDto'>
export type QueueCounts = Api<'QueueCounts'>
export type Reviewer = Api<'ReviewerDto'>
export type BulkResult = Omit<Api<'BulkResult'>, 'failed'> & { failed: Api<'BulkFailure'>[] }
export type BulkAction = Api<'BulkRequest'>['action']
export type ContentSettings = Api<'ContentSettings'>
export type PublishResult = Api<'PublishResult'>
