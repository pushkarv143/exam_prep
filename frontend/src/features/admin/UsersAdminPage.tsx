import { useEffect, useState } from 'react'
import { useForm } from 'react-hook-form'
import { zodResolver } from '@hookform/resolvers/zod'
import { z } from 'zod'
import { keepPreviousData, useMutation, useQueries, useQuery, useQueryClient } from '@tanstack/react-query'
import { useSearchParams } from 'react-router-dom'
import { KeyRound, LogOut, Plus, ShieldCheck, ShieldOff } from 'lucide-react'
import { toast } from 'sonner'
import { adminApi, adminKeys, useAdminExams } from '@/api/admin'
import { portalApi, portalKeys, usePermissions } from '@/api/portal'
import type { Role as RoleInfo } from '@/api/types'
import { useConfirm } from '@/components/common/ConfirmDialog'
import { FormField } from '@/components/common/FormField'
import { Pagination } from '@/components/common/Pagination'
import { EmptyState, ErrorState } from '@/components/common/States'
import { PageHeader } from '@/components/layout/Layouts'
import { Badge } from '@/components/ui/badge'
import { Button } from '@/components/ui/button'
import { Card, CardContent } from '@/components/ui/card'
import { Checkbox } from '@/components/ui/checkbox'
import {
  Dialog, DialogContent, DialogDescription, DialogFooter, DialogHeader, DialogTitle,
} from '@/components/ui/dialog'
import { Input } from '@/components/ui/input'
import { NativeSelect } from '@/components/ui/native-select'
import { Sheet, SheetContent, SheetDescription, SheetHeader, SheetTitle } from '@/components/ui/sheet'
import { Skeleton } from '@/components/ui/skeleton'
import { errorMessage } from '@/lib/errors'
import { formatDate, formatDateTime } from '@/lib/format'
import { useAuthStore } from '@/store/auth'
import type { AdminUser } from '@/types/admin'
import type { UserStatus } from '@/types/domain'
import { titleCase } from './labels'

const STATUSES: UserStatus[] = ['ACTIVE', 'INACTIVE', 'LOCKED']

function useRoles() {
  return useQuery({ queryKey: portalKeys.roles, queryFn: portalApi.roles, staleTime: 60_000 })
}

export default function UsersAdminPage() {
  const me = useAuthStore((s) => s.user)
  const qc = useQueryClient()
  const confirm = useConfirm()
  const { can } = usePermissions()
  const [params] = useSearchParams()
  const [text, setText] = useState(params.get('q') ?? '')
  const [q, setQ] = useState(params.get('q') ?? '')
  const [role, setRole] = useState('')
  const [status, setStatus] = useState<UserStatus | ''>('')
  const [page, setPage] = useState(0)
  const [creating, setCreating] = useState(false)
  const [accessFor, setAccessFor] = useState<AdminUser | null>(null)
  const [securityFor, setSecurityFor] = useState<AdminUser | null>(null)
  const roles = useRoles()
  useEffect(() => {
    const t = setTimeout(() => { setQ(text.trim()); setPage(0) }, 350)
    return () => clearTimeout(t)
  }, [text])
  const query = { q: q || undefined, role: role || undefined, status: status || undefined, page }
  const users = useQuery({ queryKey: adminKeys.users(query), queryFn: () => adminApi.users(query), placeholderData: keepPreviousData })
  const roleName = (r: string) => roles.data?.find((x) => x.name === r)?.displayName ?? titleCase(r)

  const setUserStatus = useMutation({
    mutationFn: ({ id, s, reason }: { id: string; s: UserStatus; reason?: string }) => adminApi.setUserStatus(id, s, reason),
    onSuccess: () => { toast.success('Status updated'); void qc.invalidateQueries({ queryKey: ['admin', 'users'] }) },
    onError: (e) => toast.error(errorMessage(e)),
  })
  const changeStatus = async (u: AdminUser, s: UserStatus) => {
    if (s === 'ACTIVE') {
      setUserStatus.mutate({ id: u.id, s })
      return
    }
    const r = await confirm({ title: `${s === 'LOCKED' ? 'Lock' : 'Deactivate'} ${u.fullName}?`, destructive: true, reason: 'required',
      description: 'They are signed out of every device immediately and cannot sign in until reactivated.',
      confirmText: s === 'LOCKED' ? 'Lock account' : 'Deactivate' })
    if (r) setUserStatus.mutate({ id: u.id, s, reason: r.reason })
  }

  return (
    <>
      <PageHeader title="Users" description="Students and staff: roles, subject scopes, status and sign-in security"
                  actions={can('user.create') && <Button onClick={() => setCreating(true)}><Plus /> New user</Button>} />
      <div className="mb-4 grid gap-2 sm:grid-cols-[1fr_200px_160px]">
        <Input placeholder="Search name, email or phone…" value={text} onChange={(e) => setText(e.target.value)} aria-label="Search users" />
        <NativeSelect aria-label="Role" value={role} onChange={(e) => { setRole(e.target.value); setPage(0) }}>
          <option value="">All roles</option>
          {roles.data?.map((r) => <option key={r.name} value={r.name}>{r.displayName ?? r.name}</option>)}
        </NativeSelect>
        <NativeSelect aria-label="Status" value={status} onChange={(e) => { setStatus(e.target.value as UserStatus | ''); setPage(0) }}>
          <option value="">All statuses</option>
          {STATUSES.map((s) => <option key={s} value={s}>{titleCase(s)}</option>)}
        </NativeSelect>
      </div>

      {users.isError ? <ErrorState error={users.error} onRetry={() => users.refetch()} /> :
        users.isPending ? <Skeleton className="h-80 rounded-xl" /> :
          users.data.content.length === 0 ? <EmptyState title="No users found" /> : (
            <>
              <Card className="py-0">
                <CardContent className="overflow-x-auto px-0">
                  <table className="w-full text-sm">
                    <thead>
                      <tr className="text-muted-foreground border-b text-left">
                        <th className="px-4 py-3 font-medium">User</th>
                        <th className="px-3 py-3 font-medium">Roles</th>
                        <th className="px-3 py-3 font-medium">Status</th>
                        <th className="px-3 py-3 font-medium">Last login</th>
                        <th className="px-3 py-3 font-medium">Joined</th>
                        <th className="px-4 py-3" />
                      </tr>
                    </thead>
                    <tbody>
                      {users.data.content.map((u) => {
                        const self = u.id === me?.id
                        return (
                          <tr key={u.id} className="border-b last:border-0">
                            <td className="px-4 py-2.5">
                              <p className="font-medium">{u.fullName}{self && <span className="text-muted-foreground ml-1 text-xs">(you)</span>}</p>
                              <p className="text-muted-foreground text-xs">{u.email}{u.phone ? ` · ${u.phone}` : ''}</p>
                            </td>
                            <td className="px-3 py-2.5">
                              <div className="flex flex-wrap gap-1">{u.roles.map((r) => (
                                <Badge key={r} variant={r === 'STUDENT' ? 'outline' : r === 'SUPER_ADMIN' ? 'destructive' : 'default'}>{roleName(r)}</Badge>))}</div>
                            </td>
                            <td className="px-3 py-2.5">
                              <NativeSelect aria-label={`Status of ${u.fullName}`} value={u.status}
                                            disabled={self || setUserStatus.isPending || !can('user.status')}
                                            className="h-8 w-32" onChange={(e) => void changeStatus(u, e.target.value as UserStatus)}>
                                {STATUSES.map((s) => <option key={s} value={s}>{titleCase(s)}</option>)}
                              </NativeSelect>
                            </td>
                            <td className="text-muted-foreground px-3 py-2.5 text-xs">{u.lastLoginAt ? formatDateTime(u.lastLoginAt) : 'Never'}</td>
                            <td className="text-muted-foreground px-3 py-2.5 text-xs">{formatDate(u.createdAt)}</td>
                            <td className="px-4 py-2.5">
                              <div className="flex justify-end gap-1">
                                {can('user.roles') && !self && (
                                  <Button size="sm" variant="ghost" onClick={() => setAccessFor(u)}><KeyRound /> Access</Button>)}
                                {(can('security.manage') || can('user.view')) && (
                                  <Button size="sm" variant="ghost" onClick={() => setSecurityFor(u)}><ShieldCheck /> Security</Button>)}
                              </div>
                            </td>
                          </tr>
                        )
                      })}
                    </tbody>
                  </table>
                </CardContent>
              </Card>
              <div className="mt-4"><Pagination page={users.data.page} totalPages={users.data.totalPages} onChange={setPage} /></div>
            </>
          )}
      <CreateUserDialog open={creating} onOpenChange={setCreating} roles={roles.data ?? []} />
      <AccessDialog user={accessFor} roles={roles.data ?? []} onClose={() => setAccessFor(null)} />
      <SecuritySheet user={securityFor} onClose={() => setSecurityFor(null)} />
    </>
  )
}

const createSchema = z.object({
  fullName: z.string().trim().min(2, 'Enter the full name').max(150),
  email: z.email('Enter a valid email address'),
  phone: z.string().trim().refine((v) => v === '' || /^[6-9]\d{9}$/.test(v), 'Enter a valid 10-digit mobile number'),
  password: z.string().min(8, 'At least 8 characters').max(72).regex(/[A-Za-z]/, 'Include a letter').regex(/\d/, 'Include a digit'),
  roles: z.array(z.string()).min(1, 'Choose at least one role'),
})
type CreateValues = z.infer<typeof createSchema>

function CreateUserDialog({ open, onOpenChange, roles }: { open: boolean; onOpenChange: (o: boolean) => void; roles: RoleInfo[] }) {
  const qc = useQueryClient()
  const form = useForm<CreateValues>({
    resolver: zodResolver(createSchema),
    defaultValues: { fullName: '', email: '', phone: '', password: '', roles: ['TEACHER'] },
  })
  const { register, handleSubmit, watch, setValue, reset, formState: { errors } } = form
  const selected = watch('roles')
  const create = useMutation({
    mutationFn: (v: CreateValues) => adminApi.createUser({ ...v, phone: v.phone || undefined }),
    onSuccess: () => { toast.success('User created'); void qc.invalidateQueries({ queryKey: ['admin', 'users'] }); reset(); onOpenChange(false) },
    onError: (e) => toast.error(errorMessage(e), { duration: 8000 }),
  })
  return (
    <Dialog open={open} onOpenChange={(o) => !create.isPending && onOpenChange(o)}>
      <DialogContent>
        <DialogHeader><DialogTitle>New user</DialogTitle></DialogHeader>
        <form id="user-form" className="grid gap-4" onSubmit={handleSubmit((v) => create.mutate(v))} noValidate>
          <FormField id="u-name" label="Full name" error={errors.fullName}><Input id="u-name" {...register('fullName')} aria-invalid={!!errors.fullName} /></FormField>
          <div className="grid gap-4 sm:grid-cols-2">
            <FormField id="u-email" label="Email" error={errors.email}><Input id="u-email" type="email" {...register('email')} aria-invalid={!!errors.email} /></FormField>
            <FormField id="u-phone" label="Mobile (optional)" error={errors.phone}><Input id="u-phone" inputMode="numeric" {...register('phone')} aria-invalid={!!errors.phone} /></FormField>
          </div>
          <FormField id="u-pass" label="Temporary password" error={errors.password} hint="Share it securely; they can change it after signing in.">
            <Input id="u-pass" type="password" autoComplete="new-password" {...register('password')} aria-invalid={!!errors.password} />
          </FormField>
          <div className="grid gap-2">
            <span className="text-sm font-medium">Roles</span>
            <div className="grid grid-cols-2 gap-1.5 text-sm">
              {roles.map((r) => (
                <label key={r.name} className="flex items-center gap-2">
                  <Checkbox checked={selected.includes(r.name)}
                            onCheckedChange={(v) => setValue('roles', v ? [...selected, r.name] : selected.filter((x) => x !== r.name), { shouldValidate: true })} />
                  {r.displayName ?? r.name}
                </label>
              ))}
            </div>
            {errors.roles && <p className="text-destructive text-sm">{errors.roles.message}</p>}
          </div>
        </form>
        <DialogFooter>
          <Button variant="outline" onClick={() => onOpenChange(false)} disabled={create.isPending}>Cancel</Button>
          <Button type="submit" form="user-form" loading={create.isPending}>Create</Button>
        </DialogFooter>
      </DialogContent>
    </Dialog>
  )
}

/** Roles plus, for subject-scoped roles such as TEACHER, the subjects the user may author. */
function AccessDialog({ user, roles, onClose }: { user: AdminUser | null; roles: RoleInfo[]; onClose: () => void }) {
  const qc = useQueryClient()
  const confirm = useConfirm()
  const [selected, setSelected] = useState<string[]>([])
  const [subjects, setSubjects] = useState<string[]>([])
  const access = useQuery({ queryKey: ['admin', 'user-access', user?.id], queryFn: () => portalApi.userAccess(user!.id), enabled: !!user })
  const exams = useAdminExams()
  const trees = useQueries({
    queries: (exams.data ?? []).map((e) => ({ queryKey: adminKeys.tree(e.id), queryFn: () => adminApi.tree(e.id),
      enabled: !!user, staleTime: 5 * 60_000 })),
  })
  useEffect(() => {
    if (user) setSelected(user.roles)
  }, [user])
  useEffect(() => {
    if (access.data) setSubjects(access.data.subjectIds)
  }, [access.data])
  const scoped = selected.some((r) => roles.find((x) => x.name === r)?.subjectScoped)
  const save = useMutation({
    mutationFn: (reason?: string) => adminApi.setUserRoles(user!.id, selected, scoped ? subjects : null, reason),
    onSuccess: () => {
      toast.success('Access updated. They must sign in again.')
      void qc.invalidateQueries({ queryKey: ['admin', 'users'] })
      void qc.invalidateQueries({ queryKey: ['admin', 'user-access'] })
      onClose()
    },
    onError: (e) => toast.error(errorMessage(e), { duration: 8000 }),
  })
  const onSave = async () => {
    const r = await confirm({ title: `Change access for ${user?.fullName}?`, reason: 'optional', confirmText: 'Save',
      description: 'Their sessions end, so the new roles apply at their next sign-in.' })
    if (r) save.mutate(r.reason)
  }
  return (
    <Dialog open={!!user} onOpenChange={(o) => !o && !save.isPending && onClose()}>
      <DialogContent className="max-h-[90vh] overflow-y-auto sm:max-w-lg">
        <DialogHeader>
          <DialogTitle>Access for {user?.fullName}</DialogTitle>
          <DialogDescription>You can grant only roles whose permissions you hold yourself.</DialogDescription>
        </DialogHeader>
        <div className="grid gap-1.5 text-sm">
          {roles.map((r) => (
            <label key={r.name} className="hover:bg-muted/50 flex items-start gap-2 rounded-md p-1.5">
              <Checkbox className="mt-0.5" checked={selected.includes(r.name)}
                        onCheckedChange={(v) => setSelected(v ? [...selected, r.name] : selected.filter((x) => x !== r.name))} />
              <span><span className="font-medium">{r.displayName ?? r.name}</span>
                {r.subjectScoped && <Badge variant="outline" className="ml-2">subject-scoped</Badge>}
                <span className="text-muted-foreground block text-xs">{r.description}</span></span>
            </label>
          ))}
        </div>
        {scoped && (
          <div className="grid gap-2">
            <p className="text-sm font-medium">Subjects they may author</p>
            {trees.some((t) => t.isPending) ? <Skeleton className="h-20" /> : (
              <div className="grid max-h-56 gap-3 overflow-y-auto rounded-md border p-3 text-sm">
                {trees.map((t) => t.data && (
                  <div key={t.data.exam.id}>
                    <p className="text-muted-foreground mb-1 text-xs font-semibold uppercase">{t.data.exam.name}</p>
                    <div className="grid grid-cols-2 gap-1">
                      {t.data.subjects.map((s) => (
                        <label key={s.id} className="flex items-center gap-2">
                          <Checkbox checked={subjects.includes(s.id)}
                                    onCheckedChange={(v) => setSubjects(v ? [...subjects, s.id] : subjects.filter((x) => x !== s.id))} />
                          {s.name}
                        </label>
                      ))}
                    </div>
                  </div>
                ))}
              </div>
            )}
            {subjects.length === 0 && <p className="text-warning text-xs">With no subjects, a teacher cannot author any content.</p>}
          </div>
        )}
        <DialogFooter>
          <Button variant="outline" onClick={onClose} disabled={save.isPending}>Cancel</Button>
          <Button disabled={selected.length === 0} loading={save.isPending} onClick={() => void onSave()}>Save</Button>
        </DialogFooter>
      </DialogContent>
    </Dialog>
  )
}

function SecuritySheet({ user, onClose }: { user: AdminUser | null; onClose: () => void }) {
  const qc = useQueryClient()
  const confirm = useConfirm()
  const { can } = usePermissions()
  const info = useQuery({ queryKey: ['admin', 'user-security', user?.id], queryFn: () => portalApi.userSecurity(user!.id), enabled: !!user })
  const refresh = () => qc.invalidateQueries({ queryKey: ['admin', 'user-security', user?.id] })
  const resetMfa = useMutation({ mutationFn: (reason: string) => portalApi.resetUserMfa(user!.id, { reason }),
    onSuccess: () => { toast.success('2FA reset: they must set it up again'); void refresh() }, onError: (e) => toast.error(errorMessage(e)) })
  const signOut = useMutation({ mutationFn: (reason: string) => portalApi.revokeUserSessions(user!.id, { reason }),
    onSuccess: (r) => { toast.success(`Signed out ${r.revoked} session(s)`); void refresh() }, onError: (e) => toast.error(errorMessage(e)) })
  return (
    <Sheet open={!!user} onOpenChange={(o) => !o && onClose()}>
      <SheetContent className="sm:max-w-lg">
        <SheetHeader>
          <SheetTitle>Security: {user?.fullName}</SheetTitle>
          <SheetDescription>{user?.email}</SheetDescription>
        </SheetHeader>
        {info.isPending ? <Skeleton className="h-40" /> : info.isError ? <ErrorState error={info.error} /> : (
          <>
            <section className="space-y-2 text-sm">
              <h3 className="font-semibold">Two-factor authentication</h3>
              <p>{info.data.mfa.enabled ? <Badge variant="success">On</Badge> : <Badge variant="muted">Off</Badge>}
                {info.data.mfa.enabled && <span className="text-muted-foreground ml-2">{info.data.mfa.recoveryCodesLeft} recovery codes left</span>}</p>
              {info.data.mfa.enabled && can('security.manage') && (
                <Button variant="outline" size="sm" loading={resetMfa.isPending} onClick={async () => {
                  const r = await confirm({ title: 'Reset their 2FA?', destructive: true, reason: 'required', confirmText: 'Reset 2FA',
                    description: 'Use this when they lost their phone. Their sessions end and they must set 2FA up again.' })
                  if (r) resetMfa.mutate(r.reason)
                }}><ShieldOff /> Reset 2FA</Button>
              )}
            </section>
            <section className="space-y-2 text-sm">
              <h3 className="font-semibold">Active sessions ({info.data.sessions.length})</h3>
              <ul className="divide-y rounded-lg border">
                {info.data.sessions.length === 0 && <li className="text-muted-foreground px-3 py-2">No active sessions</li>}
                {info.data.sessions.map((s) => (
                  <li key={s.id} className="px-3 py-2">
                    <p>{s.device}{s.mfaVerified && <Badge variant="outline" className="ml-2">2FA</Badge>}</p>
                    <p className="text-muted-foreground text-xs">{s.ip ?? 'unknown IP'} · last active {formatDateTime(s.lastSeenAt)}</p>
                  </li>
                ))}
              </ul>
              {(can('security.manage') || can('user.status')) && info.data.sessions.length > 0 && (
                <Button variant="outline" size="sm" loading={signOut.isPending} onClick={async () => {
                  const r = await confirm({ title: 'Sign them out everywhere?', destructive: true, reason: 'required', confirmText: 'Sign out' })
                  if (r) signOut.mutate(r.reason)
                }}><LogOut /> Sign out of all devices</Button>
              )}
            </section>
          </>
        )}
      </SheetContent>
    </Sheet>
  )
}
