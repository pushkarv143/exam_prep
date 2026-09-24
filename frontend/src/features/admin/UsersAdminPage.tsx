import { useEffect, useState } from 'react'
import { useForm } from 'react-hook-form'
import { zodResolver } from '@hookform/resolvers/zod'
import { z } from 'zod'
import { keepPreviousData, useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { Plus } from 'lucide-react'
import { toast } from 'sonner'
import { adminApi, adminKeys } from '@/api/admin'
import { FormField } from '@/components/common/FormField'
import { Pagination } from '@/components/common/Pagination'
import { EmptyState, ErrorState } from '@/components/common/States'
import { PageHeader } from '@/components/layout/Layouts'
import { Badge } from '@/components/ui/badge'
import { Button } from '@/components/ui/button'
import { Card, CardContent } from '@/components/ui/card'
import { Checkbox } from '@/components/ui/checkbox'
import {
  Dialog, DialogContent, DialogFooter, DialogHeader, DialogTitle,
} from '@/components/ui/dialog'
import { Input } from '@/components/ui/input'
import { NativeSelect } from '@/components/ui/native-select'
import { Skeleton } from '@/components/ui/skeleton'
import { errorMessage } from '@/lib/errors'
import { formatDate, formatDateTime } from '@/lib/format'
import { useAuthStore } from '@/store/auth'
import type { AdminUser } from '@/types/admin'
import type { Role, UserStatus } from '@/types/domain'
import { titleCase } from './labels'

const ROLES: Role[] = ['STUDENT', 'TEACHER', 'ADMIN']
const STATUSES: UserStatus[] = ['ACTIVE', 'INACTIVE', 'LOCKED']

export default function UsersAdminPage() {
  const me = useAuthStore((s) => s.user)
  const qc = useQueryClient()
  const [text, setText] = useState('')
  const [q, setQ] = useState('')
  const [role, setRole] = useState<Role | ''>('')
  const [status, setStatus] = useState<UserStatus | ''>('')
  const [page, setPage] = useState(0)
  const [creating, setCreating] = useState(false)
  const [rolesFor, setRolesFor] = useState<AdminUser | null>(null)
  useEffect(() => {
    const t = setTimeout(() => { setQ(text.trim()); setPage(0) }, 350)
    return () => clearTimeout(t)
  }, [text])
  const params = { q: q || undefined, role: role || undefined, status: status || undefined, page }
  const users = useQuery({ queryKey: adminKeys.users(params), queryFn: () => adminApi.users(params), placeholderData: keepPreviousData })

  const setUserStatus = useMutation({
    mutationFn: ({ id, s }: { id: string; s: UserStatus }) => adminApi.setUserStatus(id, s),
    onSuccess: () => { toast.success('Status updated'); void qc.invalidateQueries({ queryKey: ['admin', 'users'] }) },
    onError: (e) => toast.error(errorMessage(e)),
  })

  return (
    <>
      <PageHeader title="Users" description="Students, teachers and admins"
                  actions={<Button onClick={() => setCreating(true)}><Plus /> New user</Button>} />
      <div className="mb-4 grid gap-2 sm:grid-cols-[1fr_160px_160px]">
        <Input placeholder="Search name, email or phone…" value={text} onChange={(e) => setText(e.target.value)} aria-label="Search users" />
        <NativeSelect aria-label="Role" value={role} onChange={(e) => { setRole(e.target.value as Role | ''); setPage(0) }}>
          <option value="">All roles</option>
          {ROLES.map((r) => <option key={r} value={r}>{titleCase(r)}</option>)}
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
                              <div className="flex flex-wrap gap-1">{u.roles.map((r) => <Badge key={r} variant={r === 'STUDENT' ? 'outline' : 'default'}>{titleCase(r)}</Badge>)}</div>
                            </td>
                            <td className="px-3 py-2.5">
                              <NativeSelect aria-label={`Status of ${u.fullName}`} value={u.status} disabled={self || setUserStatus.isPending}
                                            className="h-8 w-32" onChange={(e) => setUserStatus.mutate({ id: u.id, s: e.target.value as UserStatus })}>
                                {STATUSES.map((s) => <option key={s} value={s}>{titleCase(s)}</option>)}
                              </NativeSelect>
                            </td>
                            <td className="text-muted-foreground px-3 py-2.5 text-xs">{u.lastLoginAt ? formatDateTime(u.lastLoginAt) : 'Never'}</td>
                            <td className="text-muted-foreground px-3 py-2.5 text-xs">{formatDate(u.createdAt)}</td>
                            <td className="px-4 py-2.5 text-right">
                              <Button size="sm" variant="ghost" disabled={self} onClick={() => setRolesFor(u)}>Roles</Button>
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
      <CreateUserDialog open={creating} onOpenChange={setCreating} />
      <RolesDialog user={rolesFor} onClose={() => setRolesFor(null)} />
    </>
  )
}

const createSchema = z.object({
  fullName: z.string().trim().min(2, 'Enter the full name').max(150),
  email: z.email('Enter a valid email address'),
  phone: z.string().trim().refine((v) => v === '' || /^[6-9]\d{9}$/.test(v), 'Enter a valid 10-digit mobile number'),
  password: z.string().min(8, 'At least 8 characters').max(72).regex(/[A-Za-z]/, 'Include a letter').regex(/\d/, 'Include a digit'),
  roles: z.array(z.enum(['STUDENT', 'TEACHER', 'ADMIN'])).min(1, 'Choose at least one role'),
})
type CreateValues = z.infer<typeof createSchema>

function CreateUserDialog({ open, onOpenChange }: { open: boolean; onOpenChange: (o: boolean) => void }) {
  const qc = useQueryClient()
  const form = useForm<CreateValues>({
    resolver: zodResolver(createSchema),
    defaultValues: { fullName: '', email: '', phone: '', password: '', roles: ['TEACHER'] },
  })
  const { register, handleSubmit, watch, setValue, reset, formState: { errors } } = form
  const roles = watch('roles')
  const create = useMutation({
    mutationFn: (v: CreateValues) => adminApi.createUser({ ...v, phone: v.phone || undefined }),
    onSuccess: () => { toast.success('User created'); void qc.invalidateQueries({ queryKey: ['admin', 'users'] }); reset(); onOpenChange(false) },
    onError: (e) => toast.error(errorMessage(e)),
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
          <FormField id="u-pass" label="Temporary password" error={errors.password} hint="Share it securely; the user can change it after logging in.">
            <Input id="u-pass" type="password" autoComplete="new-password" {...register('password')} aria-invalid={!!errors.password} />
          </FormField>
          <div className="grid gap-2">
            <span className="text-sm font-medium">Roles</span>
            <div className="flex gap-4 text-sm">
              {ROLES.map((r) => (
                <label key={r} className="flex items-center gap-2">
                  <Checkbox checked={roles.includes(r)}
                            onCheckedChange={(v) => setValue('roles', v ? [...roles, r] : roles.filter((x) => x !== r), { shouldValidate: true })} />
                  {titleCase(r)}
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

function RolesDialog({ user, onClose }: { user: AdminUser | null; onClose: () => void }) {
  const qc = useQueryClient()
  const [roles, setRoles] = useState<Role[]>([])
  useEffect(() => { if (user) setRoles(user.roles) }, [user])
  const save = useMutation({
    mutationFn: () => adminApi.setUserRoles(user!.id, roles),
    onSuccess: () => { toast.success('Roles updated. The user must sign in again.'); void qc.invalidateQueries({ queryKey: ['admin', 'users'] }); onClose() },
    onError: (e) => toast.error(errorMessage(e)),
  })
  return (
    <Dialog open={!!user} onOpenChange={(o) => !o && !save.isPending && onClose()}>
      <DialogContent className="sm:max-w-sm">
        <DialogHeader><DialogTitle>Roles for {user?.fullName}</DialogTitle></DialogHeader>
        <div className="grid gap-2 text-sm">
          {ROLES.map((r) => (
            <label key={r} className="flex items-center gap-2">
              <Checkbox checked={roles.includes(r)} onCheckedChange={(v) => setRoles(v ? [...roles, r] : roles.filter((x) => x !== r))} />
              {titleCase(r)}
            </label>
          ))}
        </div>
        <DialogFooter>
          <Button variant="outline" onClick={onClose} disabled={save.isPending}>Cancel</Button>
          <Button disabled={roles.length === 0} loading={save.isPending} onClick={() => save.mutate()}>Save</Button>
        </DialogFooter>
      </DialogContent>
    </Dialog>
  )
}
