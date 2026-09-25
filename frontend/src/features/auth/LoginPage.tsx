import { useState } from 'react'
import { useForm } from 'react-hook-form'
import { zodResolver } from '@hookform/resolvers/zod'
import { useMutation } from '@tanstack/react-query'
import { Link, useNavigate, useSearchParams } from 'react-router-dom'
import { Info, ShieldCheck } from 'lucide-react'
import { authApi } from '@/api/auth'
import { FormField } from '@/components/common/FormField'
import { safeNext } from '@/components/routing/Guards'
import { Alert, AlertDescription } from '@/components/ui/alert'
import { Button } from '@/components/ui/button'
import { Card, CardContent, CardDescription, CardFooter, CardHeader, CardTitle } from '@/components/ui/card'
import { Input } from '@/components/ui/input'
import { errorMessage, toApiError } from '@/lib/errors'
import { useAuthStore } from '@/store/auth'
import { isMfaChallenge, type AuthResponse } from '@/types/domain'
import { loginSchema, type LoginValues } from './schemas'

const REASONS: Record<string, string> = {
  session: 'Your session has ended. Please log in again.',
  'password-changed': 'Password changed. Please log in with your new password.',
  'password-reset': 'Password reset successful. Log in with your new password.',
}

export default function LoginPage() {
  const [params] = useSearchParams()
  const navigate = useNavigate()
  const setSession = useAuthStore((s) => s.setSession)
  const [mfaToken, setMfaToken] = useState<string | null>(null)
  const form = useForm<LoginValues>({ resolver: zodResolver(loginSchema), defaultValues: { identifier: '', password: '' } })
  const { register, handleSubmit, formState: { errors } } = form

  const finish = (auth: AuthResponse) => {
    setSession(auth)
    navigate(safeNext(params.get('next')), { replace: true })
  }

  const login = useMutation({
    mutationFn: authApi.login,
    onSuccess: (res) => (isMfaChallenge(res) ? setMfaToken(res.mfaToken) : finish(res)),
    onError: () => { /* rendered inline below */ },
  })

  if (mfaToken) {
    return <MfaStep mfaToken={mfaToken} onDone={finish} onRestart={() => { setMfaToken(null); login.reset() }} />
  }

  const reason = REASONS[params.get('reason') ?? '']
  const error = login.error ? toApiError(login.error) : null

  return (
    <Card>
      <CardHeader>
        <CardTitle className="text-2xl">Welcome back</CardTitle>
        <CardDescription>Log in to continue your preparation.</CardDescription>
      </CardHeader>
      <form onSubmit={handleSubmit((v) => login.mutate(v))} noValidate>
        <CardContent className="grid gap-4">
          {reason && (
            <Alert variant="info"><Info /><AlertDescription>{reason}</AlertDescription></Alert>
          )}
          {error && (
            <Alert variant="destructive">
              <AlertDescription>
                {error.code === 'RATE_LIMITED' ? 'Too many attempts. Please wait a minute and try again.' : errorMessage(error)}
              </AlertDescription>
            </Alert>
          )}
          <FormField id="identifier" label="Email or mobile number" error={errors.identifier}>
            <Input id="identifier" autoComplete="username" autoFocus aria-invalid={!!errors.identifier}
                   placeholder="you@example.com or 98xxxxxxxx" {...register('identifier')} />
          </FormField>
          <FormField id="password" label="Password" error={errors.password}
                     action={<Link to="/forgot-password" className="text-primary text-sm hover:underline">Forgot password?</Link>}>
            <Input id="password" type="password" autoComplete="current-password" aria-invalid={!!errors.password}
                   {...register('password')} />
          </FormField>
        </CardContent>
        <CardFooter className="mt-6 flex-col gap-3">
          <Button type="submit" className="w-full" loading={login.isPending}>Log in</Button>
          <p className="text-muted-foreground text-sm">
            New here? <Link to="/register" className="text-primary font-medium hover:underline">Create a free account</Link>
          </p>
        </CardFooter>
      </form>
    </Card>
  )
}

/** Second step: 6-digit authenticator code, or a recovery code. */
function MfaStep({ mfaToken, onDone, onRestart }: { mfaToken: string; onDone: (a: AuthResponse) => void; onRestart: () => void }) {
  const [code, setCode] = useState('')
  const [useRecovery, setUseRecovery] = useState(false)
  const verify = useMutation({ mutationFn: () => authApi.loginMfa(mfaToken, code), onSuccess: onDone })
  const error = verify.error ? toApiError(verify.error) : null
  const expired = error?.code === 'MFA_CHALLENGE_EXPIRED'
  const valid = useRecovery ? code.replace(/[\s-]/g, '').length >= 8 : /^\d{6}$/.test(code)

  return (
    <Card>
      <CardHeader>
        <div className="bg-primary/10 text-primary mb-2 grid size-10 place-items-center rounded-full"><ShieldCheck className="size-5" /></div>
        <CardTitle className="text-2xl">Two-step verification</CardTitle>
        <CardDescription>
          {useRecovery ? 'Enter one of the recovery codes you saved when you set up 2FA. Each code works once.'
            : 'Open your authenticator app and enter the 6-digit code for ExamPrep.'}
        </CardDescription>
      </CardHeader>
      <form onSubmit={(e) => { e.preventDefault(); if (valid) verify.mutate() }} noValidate>
        <CardContent className="grid gap-4">
          {error && (
            <Alert variant="destructive"><AlertDescription>{errorMessage(error)}</AlertDescription></Alert>
          )}
          <FormField id="mfa-code" label={useRecovery ? 'Recovery code' : 'Verification code'}>
            <Input id="mfa-code" autoFocus autoComplete="one-time-code" inputMode={useRecovery ? 'text' : 'numeric'}
                   className="text-center text-lg tracking-[0.4em] tabular-nums" maxLength={useRecovery ? 14 : 6}
                   placeholder={useRecovery ? 'XXXXX-XXXXX' : '000000'} value={code} disabled={expired}
                   onChange={(e) => setCode(useRecovery ? e.target.value.toUpperCase() : e.target.value.replace(/\D/g, ''))} />
          </FormField>
        </CardContent>
        <CardFooter className="mt-6 flex-col gap-3">
          {expired ? (
            <Button type="button" className="w-full" onClick={onRestart}>Log in again</Button>
          ) : (
            <Button type="submit" className="w-full" disabled={!valid} loading={verify.isPending}>Verify</Button>
          )}
          <div className="flex w-full justify-between text-sm">
            <button type="button" className="text-primary hover:underline" onClick={() => { setUseRecovery(!useRecovery); setCode('') }}>
              {useRecovery ? 'Use authenticator code' : 'Use a recovery code'}
            </button>
            <button type="button" className="text-muted-foreground hover:underline" onClick={onRestart}>Back</button>
          </div>
        </CardFooter>
      </form>
    </Card>
  )
}
