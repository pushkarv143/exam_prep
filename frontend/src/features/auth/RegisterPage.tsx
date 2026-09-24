import { useForm } from 'react-hook-form'
import { zodResolver } from '@hookform/resolvers/zod'
import { useMutation } from '@tanstack/react-query'
import { Link, useNavigate } from 'react-router-dom'
import { toast } from 'sonner'
import { authApi } from '@/api/auth'
import { useExams } from '@/api/catalog'
import { FormField } from '@/components/common/FormField'
import { Alert, AlertDescription } from '@/components/ui/alert'
import { Button } from '@/components/ui/button'
import { Card, CardContent, CardDescription, CardFooter, CardHeader, CardTitle } from '@/components/ui/card'
import { Input } from '@/components/ui/input'
import { NativeSelect } from '@/components/ui/native-select'
import { applyServerErrors, errorMessage, toApiError } from '@/lib/errors'
import { useAuthStore } from '@/store/auth'
import { registerSchema, type RegisterValues } from './schemas'

export default function RegisterPage() {
  const navigate = useNavigate()
  const setSession = useAuthStore((s) => s.setSession)
  const exams = useExams()
  const form = useForm<RegisterValues>({
    resolver: zodResolver(registerSchema),
    defaultValues: { fullName: '', email: '', phone: '', targetExamCode: '', password: '', confirmPassword: '' },
  })
  const { register, handleSubmit, setError, formState: { errors } } = form

  const signUp = useMutation({
    mutationFn: (v: RegisterValues) => authApi.register({
      fullName: v.fullName, email: v.email, password: v.password,
      phone: v.phone || undefined, targetExamCode: v.targetExamCode || undefined,
    }),
    onSuccess: (auth) => {
      setSession(auth)
      toast.success(`Welcome, ${auth.user.fullName.split(' ')[0]}! Your account is ready.`)
      navigate('/dashboard', { replace: true })
    },
    onError: (e) => {
      applyServerErrors(e, setError, ['fullName', 'email', 'phone', 'password'] as const)
      const err = toApiError(e)
      if (err.code === 'DUPLICATE_RESOURCE') {
        setError(err.message.toLowerCase().includes('phone') ? 'phone' : 'email', { message: err.message })
      }
    },
  })

  const serverError = signUp.error && toApiError(signUp.error).code !== 'DUPLICATE_RESOURCE'
    && toApiError(signUp.error).code !== 'VALIDATION_FAILED' ? errorMessage(signUp.error) : null

  return (
    <Card>
      <CardHeader>
        <CardTitle className="text-2xl">Create your account</CardTitle>
        <CardDescription>Free forever. Take free mock tests right away.</CardDescription>
      </CardHeader>
      <form onSubmit={handleSubmit((v) => signUp.mutate(v))} noValidate>
        <CardContent className="grid gap-4">
          {serverError && <Alert variant="destructive"><AlertDescription>{serverError}</AlertDescription></Alert>}
          <FormField id="fullName" label="Full name" error={errors.fullName}>
            <Input id="fullName" autoComplete="name" autoFocus aria-invalid={!!errors.fullName} {...register('fullName')} />
          </FormField>
          <FormField id="email" label="Email" error={errors.email}>
            <Input id="email" type="email" autoComplete="email" aria-invalid={!!errors.email} {...register('email')} />
          </FormField>
          <div className="grid gap-4 sm:grid-cols-2">
            <FormField id="phone" label="Mobile (optional)" error={errors.phone} hint="Log in with it too">
              <Input id="phone" inputMode="numeric" autoComplete="tel-national" maxLength={10}
                     aria-invalid={!!errors.phone} {...register('phone')} />
            </FormField>
            <FormField id="targetExamCode" label="Preparing for" error={errors.targetExamCode}>
              <NativeSelect id="targetExamCode" {...register('targetExamCode')}>
                <option value="">Select exam</option>
                {exams.data?.map((e) => <option key={e.code} value={e.code}>{e.name}</option>)}
              </NativeSelect>
            </FormField>
          </div>
          <FormField id="password" label="Password" error={errors.password} hint="8+ characters with a letter and a digit">
            <Input id="password" type="password" autoComplete="new-password" aria-invalid={!!errors.password}
                   {...register('password')} />
          </FormField>
          <FormField id="confirmPassword" label="Confirm password" error={errors.confirmPassword}>
            <Input id="confirmPassword" type="password" autoComplete="new-password"
                   aria-invalid={!!errors.confirmPassword} {...register('confirmPassword')} />
          </FormField>
        </CardContent>
        <CardFooter className="mt-6 flex-col gap-3">
          <Button type="submit" className="w-full" loading={signUp.isPending}>Create account</Button>
          <p className="text-muted-foreground text-sm">
            Already have an account? <Link to="/login" className="text-primary font-medium hover:underline">Log in</Link>
          </p>
        </CardFooter>
      </form>
    </Card>
  )
}
