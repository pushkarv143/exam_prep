import { useForm } from 'react-hook-form'
import { zodResolver } from '@hookform/resolvers/zod'
import { useMutation } from '@tanstack/react-query'
import { Link, useNavigate, useSearchParams } from 'react-router-dom'
import { MailCheck } from 'lucide-react'
import { authApi } from '@/api/auth'
import { FormField } from '@/components/common/FormField'
import { Alert, AlertDescription } from '@/components/ui/alert'
import { Button } from '@/components/ui/button'
import { Card, CardContent, CardDescription, CardFooter, CardHeader, CardTitle } from '@/components/ui/card'
import { Input } from '@/components/ui/input'
import { errorMessage } from '@/lib/errors'
import { forgotSchema, resetSchema, type ForgotValues, type ResetValues } from './schemas'

export function ForgotPasswordPage() {
  const form = useForm<ForgotValues>({ resolver: zodResolver(forgotSchema), defaultValues: { email: '' } })
  const request = useMutation({ mutationFn: (v: ForgotValues) => authApi.forgotPassword(v.email) })
  const { register, handleSubmit, formState: { errors } } = form

  if (request.isSuccess) {
    return (
      <Card>
        <CardHeader className="items-center text-center">
          <MailCheck className="text-success mx-auto size-10" />
          <CardTitle className="text-xl">Check your email</CardTitle>
          <CardDescription>
            If an account exists for <strong>{form.getValues('email')}</strong>, we have sent a link to reset your
            password. It expires in 30 minutes.
          </CardDescription>
        </CardHeader>
        <CardFooter className="justify-center">
          <Button variant="outline" asChild><Link to="/login">Back to login</Link></Button>
        </CardFooter>
      </Card>
    )
  }

  return (
    <Card>
      <CardHeader>
        <CardTitle className="text-2xl">Forgot password</CardTitle>
        <CardDescription>Enter your email and we will send you a reset link.</CardDescription>
      </CardHeader>
      <form onSubmit={handleSubmit((v) => request.mutate(v))} noValidate>
        <CardContent className="grid gap-4">
          <FormField id="email" label="Email" error={errors.email}>
            <Input id="email" type="email" autoComplete="email" autoFocus aria-invalid={!!errors.email}
                   {...register('email')} />
          </FormField>
        </CardContent>
        <CardFooter className="mt-6 flex-col gap-3">
          <Button type="submit" className="w-full" loading={request.isPending}>Send reset link</Button>
          <Link to="/login" className="text-muted-foreground text-sm hover:underline">Back to login</Link>
        </CardFooter>
      </form>
    </Card>
  )
}

export function ResetPasswordPage() {
  const [params] = useSearchParams()
  const navigate = useNavigate()
  const token = params.get('token') ?? ''
  const form = useForm<ResetValues>({ resolver: zodResolver(resetSchema),
    defaultValues: { newPassword: '', confirmPassword: '' } })
  const reset = useMutation({
    mutationFn: (v: ResetValues) => authApi.resetPassword(token, v.newPassword),
    onSuccess: () => navigate('/login?reason=password-reset', { replace: true }),
    onError: () => { /* inline */ },
  })
  const { register, handleSubmit, formState: { errors } } = form

  if (!token) {
    return (
      <Alert variant="destructive">
        <AlertDescription>
          This reset link is incomplete. <Link to="/forgot-password" className="underline">Request a new one</Link>.
        </AlertDescription>
      </Alert>
    )
  }

  return (
    <Card>
      <CardHeader>
        <CardTitle className="text-2xl">Set a new password</CardTitle>
        <CardDescription>You will be logged out of all devices.</CardDescription>
      </CardHeader>
      <form onSubmit={handleSubmit((v) => reset.mutate(v))} noValidate>
        <CardContent className="grid gap-4">
          {reset.error && (
            <Alert variant="destructive">
              <AlertDescription>
                {errorMessage(reset.error)} <Link to="/forgot-password" className="underline">Request a new link</Link>.
              </AlertDescription>
            </Alert>
          )}
          <FormField id="newPassword" label="New password" error={errors.newPassword}
                     hint="8+ characters with a letter and a digit">
            <Input id="newPassword" type="password" autoComplete="new-password" autoFocus
                   aria-invalid={!!errors.newPassword} {...register('newPassword')} />
          </FormField>
          <FormField id="confirmPassword" label="Confirm new password" error={errors.confirmPassword}>
            <Input id="confirmPassword" type="password" autoComplete="new-password"
                   aria-invalid={!!errors.confirmPassword} {...register('confirmPassword')} />
          </FormField>
        </CardContent>
        <CardFooter className="mt-6">
          <Button type="submit" className="w-full" loading={reset.isPending}>Reset password</Button>
        </CardFooter>
      </form>
    </Card>
  )
}
