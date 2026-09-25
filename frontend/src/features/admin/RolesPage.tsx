import { useEffect, useMemo, useState } from 'react'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { AlertTriangle, KeyRound, Lock, Plus, Search, Trash2 } from 'lucide-react'
import { toast } from 'sonner'
import { ApprovalPendingError } from '@/api/admin'
import { portalApi, portalKeys, usePermissions } from '@/api/portal'
import type { PermissionGroup, Role } from '@/api/types'
import { useConfirm } from '@/components/common/ConfirmDialog'
import { ErrorState, PageLoader } from '@/components/common/States'
import { PageHeader } from '@/components/layout/Layouts'
import { Badge } from '@/components/ui/badge'
import { Button } from '@/components/ui/button'
import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/card'
import { Checkbox } from '@/components/ui/checkbox'
import {
  Dialog, DialogContent, DialogDescription, DialogFooter, DialogHeader, DialogTitle,
} from '@/components/ui/dialog'
import { Input } from '@/components/ui/input'
import { Label } from '@/components/ui/label'
import { NativeSelect } from '@/components/ui/native-select'
import { errorMessage } from '@/lib/errors'
import { cn } from '@/lib/utils'

const MODULE_LABEL: Record<string, string> = {
  platform: 'Platform', people: 'People', security: 'Security & audit', content: 'Content', tests: 'Tests & live ops',
  results: 'Results', support: 'Support', commerce: 'Commerce', insights: 'Insights', marketing: 'Marketing',
}

export default function RolesPage() {
  const { can } = usePermissions()
  const roles = useQuery({ queryKey: portalKeys.roles, queryFn: portalApi.roles })
  const catalogue = useQuery({ queryKey: portalKeys.permissions, queryFn: portalApi.permissions, staleTime: Infinity })
  const [selected, setSelected] = useState<string | null>(null)
  const [creating, setCreating] = useState(false)

  useEffect(() => {
    if (!selected && roles.data?.length) setSelected(roles.data.find((r) => r.staff && !r.allPermissions)?.name ?? roles.data[0].name)
  }, [roles.data, selected])

  if (roles.isError) return <ErrorState error={roles.error} onRetry={() => roles.refetch()} />
  if (roles.isPending || catalogue.isPending) return <PageLoader />
  const role = roles.data.find((r) => r.name === selected) ?? null

  return (
    <>
      <PageHeader title="Roles & permissions" description="Changes apply on the next request; nobody has to sign in again."
                  actions={can('role.manage') && <Button onClick={() => setCreating(true)}><Plus /> New role</Button>} />
      <div className="grid gap-6 lg:grid-cols-[280px_1fr]">
        <Card className="h-fit py-2">
          <CardContent className="px-2">
            <ul className="space-y-0.5" role="listbox" aria-label="Roles">
              {roles.data.map((r) => (
                <li key={r.name}>
                  <button type="button" role="option" aria-selected={r.name === selected} onClick={() => setSelected(r.name)}
                          className={cn('flex w-full items-center gap-2 rounded-md px-3 py-2 text-left text-sm',
                            r.name === selected ? 'bg-accent text-accent-foreground' : 'hover:bg-muted')}>
                    <span className="min-w-0 flex-1">
                      <span className="block truncate font-medium">{r.displayName ?? r.name}</span>
                      <span className="text-muted-foreground block truncate font-mono text-[11px]">{r.name}</span>
                    </span>
                    <span className="text-muted-foreground text-xs tabular-nums" title="Users">{r.userCount}</span>
                  </button>
                </li>
              ))}
            </ul>
          </CardContent>
        </Card>
        {role && catalogue.data && (
          <RoleEditor key={`${role.name}-${role.updatedAt}`} role={role} catalogue={catalogue.data}
                      editable={can('role.manage')} onDeleted={() => setSelected(null)} />
        )}
      </div>
      <CreateRoleDialog open={creating} onOpenChange={setCreating} roles={roles.data} onCreated={setSelected} />
    </>
  )
}

function RoleEditor({ role, catalogue, editable, onDeleted }: {
  role: Role; catalogue: PermissionGroup[]; editable: boolean; onDeleted: () => void
}) {
  const qc = useQueryClient()
  const confirm = useConfirm()
  const [displayName, setDisplayName] = useState(role.displayName ?? role.name)
  const [description, setDescription] = useState(role.description ?? '')
  const [perms, setPerms] = useState<Set<string>>(new Set(role.permissions))
  const [filter, setFilter] = useState('')
  const fixed = role.allPermissions || role.name === 'STUDENT'
  const canEditPerms = editable && !fixed
  const dirty = displayName !== (role.displayName ?? role.name) || description !== (role.description ?? '')
    || perms.size !== role.permissions.length || role.permissions.some((p) => !perms.has(p))

  const save = useMutation({
    mutationFn: (reason?: string) => portalApi.updateRole(role.name, {
      displayName, description, permissions: canEditPerms ? [...perms] : undefined,
    }, { reason }),
    onSuccess: () => {
      toast.success('Role saved')
      void qc.invalidateQueries({ queryKey: portalKeys.roles })
      void qc.invalidateQueries({ queryKey: portalKeys.access })
    },
    onError: (e) => {
      if (e instanceof ApprovalPendingError) toast.info('Sent for approval', { description: 'Changing this role needs a second person.' })
      else toast.error(errorMessage(e), { duration: 8000 })
    },
  })
  const remove = useMutation({
    mutationFn: (reason: string) => portalApi.deleteRole(role.name, { reason }),
    onSuccess: () => { toast.success('Role deleted'); void qc.invalidateQueries({ queryKey: portalKeys.roles }); onDeleted() },
    onError: (e) => toast.error(errorMessage(e)),
  })

  const q = filter.trim().toLowerCase()
  const groups = useMemo(() => catalogue.map((g) => ({
    ...g, permissions: g.permissions.filter((p) => !q || p.code.includes(q) || p.description.toLowerCase().includes(q)),
  })).filter((g) => g.permissions.length > 0), [catalogue, q])

  const toggle = (code: string) => setPerms((s) => {
    const next = new Set(s)
    if (next.has(code)) next.delete(code)
    else next.add(code)
    return next
  })
  const toggleGroup = (g: PermissionGroup, on: boolean) => setPerms((s) => {
    const next = new Set(s)
    g.permissions.forEach((p) => (on ? next.add(p.code) : next.delete(p.code)))
    return next
  })
  const sensitiveAdded = [...perms].filter((p) => !role.permissions.includes(p))
    .filter((p) => catalogue.some((g) => g.permissions.some((x) => x.code === p && x.sensitive)))

  const onSave = async () => {
    const r = await confirm({
      title: `Save role ${role.name}?`,
      description: sensitiveAdded.length ? `You are adding sensitive permissions: ${sensitiveAdded.join(', ')}.` : undefined,
      confirmText: 'Save', reason: sensitiveAdded.length ? 'required' : 'optional',
    })
    if (r) save.mutate(r.reason)
  }

  return (
    <Card>
      <CardHeader className="flex flex-row flex-wrap items-start justify-between gap-3">
        <div>
          <CardTitle className="flex flex-wrap items-center gap-2">
            <KeyRound className="size-4" /> {role.displayName ?? role.name}
            {role.system && <Badge variant="muted">built-in</Badge>}
            {role.subjectScoped && <Badge variant="outline">subject-scoped</Badge>}
            {role.allPermissions && <Badge>all permissions</Badge>}
          </CardTitle>
          <p className="text-muted-foreground mt-1 text-sm">{role.userCount} user(s) · {role.allPermissions ? 'every permission' : `${perms.size} permissions`}</p>
        </div>
        {editable && (
          <div className="flex gap-2">
            {!role.system && (
              <Button variant="ghost" size="sm" disabled={remove.isPending} onClick={async () => {
                const r = await confirm({ title: `Delete role ${role.name}?`, destructive: true, reason: 'required',
                  description: role.userCount > 0 ? 'It is still assigned to users; remove it from them first.' : 'This cannot be undone.',
                  confirmText: 'Delete role' })
                if (r) remove.mutate(r.reason)
              }}><Trash2 /> Delete</Button>
            )}
            <Button disabled={!dirty} loading={save.isPending} onClick={() => void onSave()}>Save changes</Button>
          </div>
        )}
      </CardHeader>
      <CardContent className="space-y-5">
        <div className="grid gap-4 sm:grid-cols-2">
          <div className="grid gap-1.5"><Label htmlFor="r-name">Display name</Label>
            <Input id="r-name" value={displayName} disabled={!editable} onChange={(e) => setDisplayName(e.target.value)} /></div>
          <div className="grid gap-1.5"><Label htmlFor="r-desc">Description</Label>
            <Input id="r-desc" value={description} disabled={!editable} onChange={(e) => setDescription(e.target.value)} /></div>
        </div>

        {fixed ? (
          <p className="bg-muted flex items-center gap-2 rounded-md p-3 text-sm"><Lock className="size-4" />
            {role.allPermissions ? 'Super admins always have every permission, including ones added in future releases.'
              : 'Students cannot be given admin permissions.'}</p>
        ) : (
          <>
            <div className="relative max-w-sm">
              <Search className="text-muted-foreground absolute top-1/2 left-3 size-4 -translate-y-1/2" />
              <Input className="pl-9" placeholder="Filter permissions…" value={filter} onChange={(e) => setFilter(e.target.value)}
                     aria-label="Filter permissions" />
            </div>
            <div className="grid gap-4 xl:grid-cols-2">
              {groups.map((g) => {
                const on = g.permissions.filter((p) => perms.has(p.code)).length
                return (
                  <fieldset key={g.module} className="rounded-lg border p-3">
                    <legend className="flex w-full items-center gap-2 px-1 text-sm font-semibold">
                      {MODULE_LABEL[g.module] ?? g.module}
                      <span className="text-muted-foreground text-xs font-normal">{on}/{g.permissions.length}</span>
                      {canEditPerms && (
                        <button type="button" className="text-primary ml-auto text-xs font-normal hover:underline"
                                onClick={() => toggleGroup(g, on < g.permissions.length)}>
                          {on < g.permissions.length ? 'Select all' : 'Clear'}
                        </button>
                      )}
                    </legend>
                    <ul className="space-y-1.5">
                      {g.permissions.map((p) => (
                        <li key={p.code}>
                          <label className={cn('flex items-start gap-2 rounded-md p-1.5 text-sm', canEditPerms && 'hover:bg-muted/60 cursor-pointer')}>
                            <Checkbox className="mt-0.5" checked={perms.has(p.code)} disabled={!canEditPerms} onCheckedChange={() => toggle(p.code)} />
                            <span className="min-w-0 flex-1">
                              <span className="flex flex-wrap items-center gap-1.5">
                                <code className="text-xs">{p.code}</code>
                                {p.sensitive && <Badge variant="warning" className="gap-0.5"><AlertTriangle />sensitive</Badge>}
                              </span>
                              <span className="text-muted-foreground block text-xs">{p.description}</span>
                            </span>
                          </label>
                        </li>
                      ))}
                    </ul>
                  </fieldset>
                )
              })}
            </div>
          </>
        )}
      </CardContent>
    </Card>
  )
}

function CreateRoleDialog({ open, onOpenChange, roles, onCreated }: {
  open: boolean; onOpenChange: (o: boolean) => void; roles: Role[]; onCreated: (name: string) => void
}) {
  const qc = useQueryClient()
  const [name, setName] = useState('')
  const [displayName, setDisplayName] = useState('')
  const [copyFrom, setCopyFrom] = useState('')
  const [scoped, setScoped] = useState(false)
  useEffect(() => { if (open) { setName(''); setDisplayName(''); setCopyFrom(''); setScoped(false) } }, [open])
  const valid = /^[A-Z][A-Z0-9_]{1,31}$/.test(name) && displayName.trim().length > 0
  const create = useMutation({
    mutationFn: () => portalApi.createRole({ name, displayName: displayName.trim(), subjectScoped: scoped,
      permissions: ['admin.access'], copyFrom: copyFrom || undefined }),
    onSuccess: (r) => {
      toast.success(`Role ${r.name} created`)
      void qc.invalidateQueries({ queryKey: portalKeys.roles })
      onCreated(r.name)
      onOpenChange(false)
    },
    onError: (e) => toast.error(errorMessage(e), { duration: 8000 }),
  })
  return (
    <Dialog open={open} onOpenChange={(o) => !create.isPending && onOpenChange(o)}>
      <DialogContent>
        <DialogHeader>
          <DialogTitle>New role</DialogTitle>
          <DialogDescription>Start empty or copy another role, then tick the permissions it needs.</DialogDescription>
        </DialogHeader>
        <div className="grid gap-4">
          <div className="grid gap-1.5"><Label htmlFor="nr-name">Code</Label>
            <Input id="nr-name" value={name} placeholder="EXAM_COORDINATOR" aria-invalid={!!name && !valid}
                   onChange={(e) => setName(e.target.value.toUpperCase().replace(/[^A-Z0-9_]/g, '_'))} />
            <p className="text-muted-foreground text-xs">UPPER_SNAKE_CASE, 2–32 characters. It cannot be changed later.</p></div>
          <div className="grid gap-1.5"><Label htmlFor="nr-display">Display name</Label>
            <Input id="nr-display" value={displayName} placeholder="Exam coordinator" onChange={(e) => setDisplayName(e.target.value)} /></div>
          <div className="grid gap-1.5"><Label htmlFor="nr-copy">Copy permissions from</Label>
            <NativeSelect id="nr-copy" value={copyFrom} onChange={(e) => setCopyFrom(e.target.value)}>
              <option value="">Nothing (portal access only)</option>
              {roles.filter((r) => r.staff && !r.allPermissions).map((r) => <option key={r.name} value={r.name}>{r.displayName ?? r.name}</option>)}
            </NativeSelect></div>
          <label className="flex items-start gap-2 text-sm">
            <Checkbox className="mt-0.5" checked={scoped} onCheckedChange={setScoped} />
            Limit content to assigned subjects (like teachers)
          </label>
        </div>
        <DialogFooter>
          <Button variant="outline" onClick={() => onOpenChange(false)} disabled={create.isPending}>Cancel</Button>
          <Button disabled={!valid} loading={create.isPending} onClick={() => create.mutate()}>Create role</Button>
        </DialogFooter>
      </DialogContent>
    </Dialog>
  )
}
