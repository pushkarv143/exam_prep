import { useEffect, useState } from 'react'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import QRCode from 'qrcode'
import { Check, Copy, Download, KeyRound, LaptopMinimal, LogOut, Plus, ShieldCheck, ShieldOff, Trash2 } from 'lucide-react'
import { toast } from 'sonner'
import { downloadBlob } from '@/api/admin'
import { portalApi, portalKeys, usePermissions } from '@/api/portal'
import type { ApprovalPolicy, MfaSetup } from '@/api/types'
import { useConfirm } from '@/components/common/ConfirmDialog'
import { ErrorState } from '@/components/common/States'
import { PageHeader } from '@/components/layout/Layouts'
import { Alert, AlertDescription, AlertTitle } from '@/components/ui/alert'
import { Badge } from '@/components/ui/badge'
import { Button } from '@/components/ui/button'
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from '@/components/ui/card'
import { Checkbox } from '@/components/ui/checkbox'
import { Input } from '@/components/ui/input'
import { Label } from '@/components/ui/label'
import { Skeleton } from '@/components/ui/skeleton'
import { refreshAccessToken } from '@/lib/api'
import { errorMessage } from '@/lib/errors'
import { formatDateTime } from '@/lib/format'
import { useLogout } from '@/hooks/useLogout'

export default function SecurityPage() {
  const { can, access } = usePermissions()
  return (
    <>
      <PageHeader title="Security" description="Your two-factor authentication and devices; organisation-wide settings for security admins." />
      {access?.mfaEnforced && !access.mfaVerified && (
        <Alert variant="destructive" className="mb-6">
          <ShieldOff className="size-4" />
          <AlertTitle>2FA is required for the admin portal</AlertTitle>
          <AlertDescription>Set it up below to unlock the rest of the portal.</AlertDescription>
        </Alert>
      )}
      <div className="grid gap-6 xl:grid-cols-2">
        <TwoFactorCard />
        <SessionsCard />
        {can('security.manage') && <IpAllowlistCard />}
        {(can('security.manage') || can('approval.view')) && <PoliciesCard editable={can('security.manage')} />}
      </div>
    </>
  )
}

// ------------------------------------------------------------------ 2FA

function TwoFactorCard() {
  const qc = useQueryClient()
  const confirm = useConfirm()
  const status = useQuery({ queryKey: portalKeys.mfa, queryFn: portalApi.mfaStatus })
  const [setup, setSetup] = useState<MfaSetup | null>(null)
  const [qr, setQr] = useState<string | null>(null)
  const [code, setCode] = useState('')
  const [codes, setCodes] = useState<string[] | null>(null)
  const [saved, setSaved] = useState(false)
  const [manageCode, setManageCode] = useState('')

  useEffect(() => {
    if (setup) {
      void QRCode.toDataURL(setup.otpauthUri, { margin: 1, width: 220, errorCorrectionLevel: 'M' }).then(setQr)
    }
  }, [setup])

  const afterChange = async () => {
    await qc.invalidateQueries({ queryKey: portalKeys.mfa })
    try {
      await refreshAccessToken()   // new token carries the session's 2FA state
    } catch {
      // keep the current token; the next refresh picks it up
    }
    await qc.invalidateQueries({ queryKey: portalKeys.access })
  }

  const begin = useMutation({ mutationFn: portalApi.mfaSetup, onSuccess: (s) => { setSetup(s); setCode('') },
    onError: (e) => toast.error(errorMessage(e)) })
  const confirmSetup = useMutation({
    mutationFn: () => portalApi.mfaConfirm(code),
    onSuccess: async (r) => { setCodes(r.recoveryCodes); setSetup(null); setSaved(false); await afterChange() },
    onError: (e) => toast.error(errorMessage(e)),
  })
  const regenerate = useMutation({
    mutationFn: () => portalApi.mfaRecoveryCodes(manageCode),
    onSuccess: async (r) => { setCodes(r.recoveryCodes); setSaved(false); setManageCode(''); await afterChange() },
    onError: (e) => toast.error(errorMessage(e)),
  })
  const disable = useMutation({
    mutationFn: () => portalApi.mfaDisable(manageCode),
    onSuccess: async () => { toast.success('Two-factor authentication turned off'); setManageCode(''); await afterChange() },
    onError: (e) => toast.error(errorMessage(e)),
  })

  if (status.isError) return <ErrorState error={status.error} onRetry={() => status.refetch()} />

  return (
    <Card>
      <CardHeader>
        <CardTitle className="flex items-center gap-2"><ShieldCheck className="size-5" /> Two-factor authentication
          {status.data?.enabled && <Badge variant="success">On</Badge>}</CardTitle>
        <CardDescription>A code from your phone is needed at every sign-in, so a stolen password alone is useless.</CardDescription>
      </CardHeader>
      <CardContent className="space-y-4 text-sm">
        {status.isPending && <Skeleton className="h-24" />}

        {codes && (
          <RecoveryCodes codes={codes} saved={saved} onSaved={setSaved} onDone={() => setCodes(null)} />
        )}

        {!codes && status.data && !status.data.enabled && !setup && (
          <>
            <ol className="text-muted-foreground list-decimal space-y-1 pl-5">
              <li>Install an authenticator app (Google Authenticator, Microsoft Authenticator, Authy, 1Password…).</li>
              <li>Scan the QR code we show you.</li>
              <li>Type the 6-digit code to confirm.</li>
            </ol>
            <Button loading={begin.isPending} onClick={() => begin.mutate()}><ShieldCheck /> Set up 2FA</Button>
          </>
        )}

        {!codes && setup && (
          <div className="grid gap-4 sm:grid-cols-[220px_1fr]">
            <div className="grid place-items-center rounded-lg border bg-white p-2">
              {qr ? <img src={qr} alt="QR code for your authenticator app" width={200} height={200} /> : <Skeleton className="size-[200px]" />}
            </div>
            <div className="space-y-3">
              <p>Scan the code with your app, or enter this key manually:</p>
              <code className="bg-muted block rounded-md p-2 text-xs break-all select-all">{setup.secret.replace(/(.{4})/g, '$1 ').trim()}</code>
              <form className="grid gap-2" onSubmit={(e) => { e.preventDefault(); if (/^\d{6}$/.test(code)) confirmSetup.mutate() }}>
                <Label htmlFor="mfa-confirm">6-digit code from the app</Label>
                <div className="flex gap-2">
                  <Input id="mfa-confirm" inputMode="numeric" autoComplete="one-time-code" maxLength={6} value={code} autoFocus
                         className="max-w-40 text-center tracking-[0.3em] tabular-nums"
                         onChange={(e) => setCode(e.target.value.replace(/\D/g, ''))} />
                  <Button type="submit" disabled={!/^\d{6}$/.test(code)} loading={confirmSetup.isPending}>Verify & turn on</Button>
                </div>
              </form>
              <Button variant="ghost" size="sm" onClick={() => setSetup(null)}>Cancel</Button>
            </div>
          </div>
        )}

        {!codes && status.data?.enabled && (
          <>
            <dl className="grid grid-cols-[10rem_1fr] gap-y-1">
              <dt className="text-muted-foreground">Turned on</dt><dd>{status.data.enabledAt ? formatDateTime(status.data.enabledAt) : '–'}</dd>
              <dt className="text-muted-foreground">Recovery codes left</dt>
              <dd className={status.data.recoveryCodesLeft <= 3 ? 'text-destructive font-medium' : ''}>{status.data.recoveryCodesLeft} of 10</dd>
            </dl>
            <div className="grid gap-2">
              <Label htmlFor="mfa-manage">Current code (needed to change 2FA)</Label>
              <div className="flex flex-wrap gap-2">
                <Input id="mfa-manage" inputMode="numeric" maxLength={6} value={manageCode} className="max-w-36 text-center tabular-nums"
                       onChange={(e) => setManageCode(e.target.value.replace(/\D/g, ''))} />
                <Button variant="outline" disabled={manageCode.length !== 6} loading={regenerate.isPending}
                        onClick={() => regenerate.mutate()}><KeyRound /> New recovery codes</Button>
                <Button variant="ghost" className="text-destructive" disabled={manageCode.length !== 6} loading={disable.isPending}
                        onClick={async () => {
                          const r = await confirm({ title: 'Turn off 2FA?', destructive: true, reason: false, confirmText: 'Turn off',
                            description: status.data?.enforced ? 'Your organisation requires 2FA: you will lose access to the admin portal until you set it up again.'
                              : 'Your account will be protected by the password only.' })
                          if (r) disable.mutate()
                        }}><ShieldOff /> Turn off</Button>
              </div>
            </div>
          </>
        )}
      </CardContent>
    </Card>
  )
}

function RecoveryCodes({ codes, saved, onSaved, onDone }: {
  codes: string[]; saved: boolean; onSaved: (v: boolean) => void; onDone: () => void
}) {
  const text = `ExamPrep recovery codes (each works once)\n\n${codes.join('\n')}\n`
  return (
    <div className="space-y-3">
      <Alert>
        <KeyRound className="size-4" />
        <AlertTitle>Save your recovery codes now</AlertTitle>
        <AlertDescription>If you lose your phone, each of these codes lets you sign in once. They are shown only this time.</AlertDescription>
      </Alert>
      <ul className="bg-muted grid grid-cols-2 gap-1 rounded-lg p-3 font-mono text-sm">
        {codes.map((c) => <li key={c}>{c}</li>)}
      </ul>
      <div className="flex flex-wrap gap-2">
        <Button variant="outline" size="sm" onClick={() => void navigator.clipboard.writeText(text).then(() => toast.success('Copied'))}>
          <Copy /> Copy</Button>
        <Button variant="outline" size="sm" onClick={() => downloadBlob(new Blob([text], { type: 'text/plain' }), 'examprep-recovery-codes.txt')}>
          <Download /> Download</Button>
      </div>
      <label className="flex items-center gap-2 text-sm"><Checkbox checked={saved} onCheckedChange={onSaved} /> I have saved these codes somewhere safe</label>
      <Button disabled={!saved} onClick={onDone}><Check /> Done</Button>
    </div>
  )
}

// ------------------------------------------------------------------ sessions

function SessionsCard() {
  const qc = useQueryClient()
  const confirm = useConfirm()
  const logout = useLogout()
  const sessions = useQuery({ queryKey: portalKeys.sessions, queryFn: portalApi.sessions })
  const revoke = useMutation({ mutationFn: portalApi.revokeSession,
    onSuccess: () => { toast.success('Device signed out'); void qc.invalidateQueries({ queryKey: portalKeys.sessions }) },
    onError: (e) => toast.error(errorMessage(e)) })
  const revokeAll = useMutation({
    mutationFn: (keepCurrent: boolean) => portalApi.revokeAllSessions(keepCurrent),
    onSuccess: (r, keepCurrent) => {
      toast.success(`Signed out ${r.revoked} session(s)`)
      if (keepCurrent) void qc.invalidateQueries({ queryKey: portalKeys.sessions })
      else void logout('/login')
    },
    onError: (e) => toast.error(errorMessage(e)),
  })
  return (
    <Card>
      <CardHeader>
        <CardTitle className="flex items-center gap-2"><LaptopMinimal className="size-5" /> Where you're signed in</CardTitle>
        <CardDescription>End sessions you don't recognise. Their tokens stop working immediately.</CardDescription>
      </CardHeader>
      <CardContent className="space-y-3 text-sm">
        {sessions.isPending ? <Skeleton className="h-24" /> : sessions.isError ? <ErrorState error={sessions.error} /> : (
          <ul className="divide-y rounded-lg border">
            {sessions.data.map((s) => (
              <li key={s.id} className="flex flex-wrap items-center gap-2 px-3 py-2.5">
                <div className="min-w-0 flex-1">
                  <p className="font-medium">{s.device}{s.current && <Badge variant="success" className="ml-2">this device</Badge>}
                    {s.mfaVerified && <Badge variant="outline" className="ml-1">2FA</Badge>}</p>
                  <p className="text-muted-foreground text-xs">{s.ip ?? 'unknown IP'} · last active {formatDateTime(s.lastSeenAt)} · since {formatDateTime(s.createdAt)}</p>
                </div>
                {!s.current && <Button size="sm" variant="ghost" disabled={revoke.isPending} onClick={() => revoke.mutate(s.id)}>Sign out</Button>}
              </li>
            ))}
          </ul>
        )}
        <div className="flex flex-wrap gap-2">
          <Button variant="outline" size="sm" loading={revokeAll.isPending && revokeAll.variables === true}
                  onClick={() => revokeAll.mutate(true)}><LogOut /> Sign out all other devices</Button>
          <Button variant="ghost" size="sm" className="text-destructive" onClick={async () => {
            const r = await confirm({ title: 'Log out of all devices?', destructive: true, reason: false, confirmText: 'Log out everywhere',
              description: 'Every session ends, including this one. You will need to sign in again.' })
            if (r) revokeAll.mutate(false)
          }}>Log out everywhere</Button>
        </div>
      </CardContent>
    </Card>
  )
}

// ------------------------------------------------------------------ IP allow-list

function IpAllowlistCard() {
  const qc = useQueryClient()
  const confirm = useConfirm()
  const state = useQuery({ queryKey: portalKeys.ipAllowlist, queryFn: portalApi.ipAllowlist })
  const [cidr, setCidr] = useState('')
  const [label, setLabel] = useState('')
  const invalidate = () => qc.invalidateQueries({ queryKey: portalKeys.ipAllowlist })
  const add = useMutation({ mutationFn: () => portalApi.addIp(cidr, label),
    onSuccess: () => { toast.success('Added'); setCidr(''); setLabel(''); void invalidate() }, onError: (e) => toast.error(errorMessage(e)) })
  const remove = useMutation({ mutationFn: ({ id, reason }: { id: string; reason: string }) => portalApi.removeIp(id, { reason }),
    onSuccess: () => void invalidate(), onError: (e) => toast.error(errorMessage(e)) })
  const toggle = useMutation({ mutationFn: ({ on, reason }: { on: boolean; reason: string }) => portalApi.setIpAllowlist(on, { reason }),
    onSuccess: (s) => { toast.success(s.enabled ? 'Allow-list is on' : 'Allow-list is off'); void invalidate() },
    onError: (e) => toast.error(errorMessage(e), { duration: 10_000 }) })
  const s = state.data
  return (
    <Card>
      <CardHeader>
        <CardTitle className="flex items-center gap-2">Admin IP allow-list {s && <Badge variant={s.enabled ? 'success' : 'muted'}>{s.enabled ? 'On' : 'Off'}</Badge>}</CardTitle>
        <CardDescription>When on, the admin portal only works from these networks. Students are never affected.</CardDescription>
      </CardHeader>
      <CardContent className="space-y-4 text-sm">
        {state.isPending ? <Skeleton className="h-24" /> : state.isError ? <ErrorState error={state.error} /> : s && (
          <>
            {s.forceDisabled && <Alert variant="destructive"><AlertDescription>Emergency override is active (ADMIN_IP_ALLOWLIST_FORCE_DISABLED): the list is ignored.</AlertDescription></Alert>}
            <p>Your IP: <code className="font-mono">{s.yourIp}</code> {s.yourIpAllowed ? <Badge variant="success">on the list</Badge> : <Badge variant="warning">not on the list</Badge>}</p>
            <ul className="divide-y rounded-lg border">
              {s.entries.length === 0 && <li className="text-muted-foreground px-3 py-3">No networks yet.</li>}
              {s.entries.map((e) => (
                <li key={e.id} className="flex items-center gap-2 px-3 py-2">
                  <code className="font-mono">{e.cidr}</code><span className="text-muted-foreground flex-1 truncate">{e.label}</span>
                  <Button size="icon" variant="ghost" aria-label={`Remove ${e.cidr}`} onClick={async () => {
                    const r = await confirm({ title: `Remove ${e.cidr}?`, destructive: true, reason: 'required', confirmText: 'Remove' })
                    if (r) remove.mutate({ id: e.id, reason: r.reason })
                  }}><Trash2 /></Button>
                </li>
              ))}
            </ul>
            <form className="flex flex-wrap gap-2" onSubmit={(e) => { e.preventDefault(); add.mutate() }}>
              <Input className="w-44" placeholder="203.0.113.0/24" value={cidr} onChange={(e) => setCidr(e.target.value)} aria-label="IP or CIDR" />
              <Input className="min-w-40 flex-1" placeholder="Label, e.g. Kota office" value={label} onChange={(e) => setLabel(e.target.value)} aria-label="Label" />
              <Button type="submit" variant="outline" disabled={!cidr.trim() || !label.trim()} loading={add.isPending}><Plus /> Add</Button>
            </form>
            <Button variant={s.enabled ? 'outline' : 'default'} loading={toggle.isPending} onClick={async () => {
              const r = await confirm({
                title: s.enabled ? 'Turn the allow-list off?' : 'Turn the allow-list on?', reason: 'required', destructive: !s.enabled,
                description: s.enabled ? 'The admin portal will work from any network again.'
                  : 'Staff outside these networks lose admin access immediately. Your current IP must be on the list.',
                confirmText: s.enabled ? 'Turn off' : 'Turn on',
              })
              if (r) toggle.mutate({ on: !s.enabled, reason: r.reason })
            }}>{s.enabled ? 'Turn off' : 'Turn on'}</Button>
          </>
        )}
      </CardContent>
    </Card>
  )
}

// ------------------------------------------------------------------ approval policies

function PoliciesCard({ editable }: { editable: boolean }) {
  const policies = useQuery({ queryKey: portalKeys.policies, queryFn: portalApi.policies })
  return (
    <Card className="xl:col-span-2" id="policies">
      <CardHeader>
        <CardTitle>Approval policies (maker-checker)</CardTitle>
        <CardDescription>Actions that need a second person. The requester can never approve their own request.</CardDescription>
      </CardHeader>
      <CardContent>
        {policies.isPending ? <Skeleton className="h-32" /> : policies.isError ? <ErrorState error={policies.error} /> : (
          <div className="overflow-x-auto">
            <table className="w-full text-sm">
              <thead><tr className="text-muted-foreground border-b text-left">
                <th className="py-2 pr-3 font-medium">Action</th><th className="px-3 font-medium">Needs approval</th>
                <th className="px-3 font-medium">Approver needs</th><th className="px-3 font-medium">Only above amount</th>
                <th className="px-3 font-medium">Expires after</th><th /></tr></thead>
              <tbody>{policies.data.map((p) => <PolicyRow key={p.action} policy={p} editable={editable} />)}</tbody>
            </table>
          </div>
        )}
      </CardContent>
    </Card>
  )
}

function PolicyRow({ policy, editable }: { policy: ApprovalPolicy; editable: boolean }) {
  const qc = useQueryClient()
  const [enabled, setEnabled] = useState(policy.enabled)
  const [threshold, setThreshold] = useState(policy.threshold == null ? '' : String(policy.threshold))
  const [hours, setHours] = useState(String(policy.expiryHours))
  const dirty = enabled !== policy.enabled || threshold !== (policy.threshold == null ? '' : String(policy.threshold))
    || hours !== String(policy.expiryHours)
  const save = useMutation({
    mutationFn: () => portalApi.updatePolicy(policy.action, { enabled, threshold: threshold ? Number(threshold) : null,
      expiryHours: Number(hours) }),
    onSuccess: () => { toast.success('Policy saved'); void qc.invalidateQueries({ queryKey: portalKeys.policies }) },
    onError: (e) => toast.error(errorMessage(e)),
  })
  return (
    <tr className="border-b last:border-0">
      <td className="py-2 pr-3"><p className="font-medium">{policy.description}</p><code className="text-muted-foreground text-xs">{policy.action}</code></td>
      <td className="px-3"><Checkbox checked={enabled} disabled={!editable} onCheckedChange={setEnabled} aria-label={`Require approval for ${policy.action}`} /></td>
      <td className="px-3"><code className="text-xs">approval.decide + {policy.approvePermission}</code></td>
      <td className="px-3"><Input className="h-8 w-28" inputMode="decimal" placeholder="always" value={threshold} disabled={!editable}
                                  onChange={(e) => setThreshold(e.target.value.replace(/[^\d.]/g, ''))} aria-label="Threshold" /></td>
      <td className="px-3"><span className="flex items-center gap-1"><Input className="h-8 w-16" inputMode="numeric" value={hours} disabled={!editable}
                                  onChange={(e) => setHours(e.target.value.replace(/\D/g, ''))} aria-label="Expiry hours" /> h</span></td>
      <td className="pl-3 text-right">{editable && <Button size="sm" variant="outline" disabled={!dirty || !Number(hours)}
                                                           loading={save.isPending} onClick={() => save.mutate()}>Save</Button>}</td>
    </tr>
  )
}
