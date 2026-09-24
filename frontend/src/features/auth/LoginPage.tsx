import { useForm } from 'react-hook-form'
import { zodResolver } from '@hookform/resolvers/zod'
import { useMutation } from '@tanstack/react-query'
import { Link, useNavigate, useSearchParams } from 'react-router-dom'
import { Info } from 'lucide-react'
import { authApi } from '@/api/auth'
import { FormField } from '@/components/common/FormField'
import { safeNext } from '@/components/routing/Guards'
import { Alert, AlertDescription } from '@/components/ui/alert'
import { Button } from '@/components/ui/button'
import { Card, CardContent, CardDescription, CardFooter, CardHeader, CardTitle } from '@/components/ui/card'
import { Input } from '@/components/ui/input'
import { errorMessage, toApiError } from '@/lib/errors'
import { useAuthStore } from '@/store/auth'
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
  const form = useForm<LoginValues>({ resolver: zodResolver(loginSchema), defaultValues: { identifier: '', password: '' } })
  const { register, handleSubmit, formState: { errors } } = form

  const login = useMutation({
    mutationFn: authApi.login,
    onSuccess: (auth) => {
      setSession(auth)
      navigate(safeNext(params.get('next')), { replace: true })
    },
    onError: () => { /* rendered inline below */ },
  })

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
