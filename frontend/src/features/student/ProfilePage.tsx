import { useForm } from 'react-hook-form'
import { zodResolver } from '@hookform/resolvers/zod'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { useNavigate } from 'react-router-dom'
import { toast } from 'sonner'
import { authApi } from '@/api/auth'
import { useExams } from '@/api/catalog'
import { PageHeader } from '@/components/layout/Layouts'
import { FormField } from '@/components/common/FormField'
import { ErrorState, PageLoader } from '@/components/common/States'
import { Button } from '@/components/ui/button'
import { Card, CardContent, CardDescription, CardFooter, CardHeader, CardTitle } from '@/components/ui/card'
import { Input } from '@/components/ui/input'
import { NativeSelect } from '@/components/ui/native-select'
import { applyServerErrors, errorMessage, toApiError } from '@/lib/errors'
import { useAuthStore } from '@/store/auth'
import {
  changePasswordSchema, profileSchema, type ChangePasswordValues, type ProfileValues,
} from '@/features/auth/schemas'

export default function ProfilePage() {
  const me = useQuery({ queryKey: ['me'], queryFn: authApi.me })
  // Wait for the exam options too: a native <select> given a value before its <option>
  // exists silently falls back to the first option ("Select exam").
  const exams = useExams()
  if (me.isPending || exams.isPending) return <PageLoader />
  if (me.isError) return <ErrorState error={me.error} onRetry={() => me.refetch()} />
  return (
    <>
      <PageHeader title="Profile" description={me.data.email} />
      <div className="grid gap-6 lg:grid-cols-2">
        <ProfileForm />
        <PasswordForm />
      </div>
    </>
  )
}

function ProfileForm() {
  const qc = useQueryClient()
  const setUser = useAuthStore((s) => s.setUser)
  const me = qc.getQueryData<Awaited<ReturnType<typeof authApi.me>>>(['me'])!
  const exams = useExams()
  const form = useForm<ProfileValues>({
    resolver: zodResolver(profileSchema),
    defaultValues: {
      fullName: me.fullName, phone: me.phone ?? '', targetExamCode: me.targetExamCode ?? '',
      city: me.city ?? '', state: me.state ?? '',
    },
  })
  const { register, handleSubmit, setError, reset, formState: { errors, isDirty } } = form

  const save = useMutation({
    mutationFn: (v: ProfileValues) => authApi.updateMe({
      fullName: v.fullName, phone: v.phone || undefined, targetExamCode: v.targetExamCode || undefined,
      city: v.city || undefined, state: v.state || undefined,
    }),
    onSuccess: (user) => {
      qc.setQueryData(['me'], user)
      setUser(user)
      reset(form.getValues())
      toast.success('Profile saved')
    },
    onError: (e) => {
      if (!applyServerErrors(e, setError, ['fullName', 'phone', 'city', 'state'] as const)) {
        const err = toApiError(e)
        if (err.code === 'DUPLICATE_RESOURCE') setError('phone', { message: err.message })
        else toast.error(errorMessage(e))
      }
    },
  })

  return (
    <Card>
      <CardHeader>
        <CardTitle>Personal details</CardTitle>
        <CardDescription>Used on your results and certificates.</CardDescription>
      </CardHeader>
      <form onSubmit={handleSubmit((v) => save.mutate(v))} noValidate>
        <CardContent className="grid gap-4">
          <FormField id="fullName" label="Full name" error={errors.fullName}>
            <Input id="fullName" aria-invalid={!!errors.fullName} {...register('fullName')} />
          </FormField>
          <div className="grid gap-4 sm:grid-cols-2">
            <FormField id="phone" label="Mobile" error={errors.phone}>
              <Input id="phone" inputMode="numeric" maxLength={10} aria-invalid={!!errors.phone} {...register('phone')} />
            </FormField>
            <FormField id="targetExamCode" label="Preparing for">
              <NativeSelect id="targetExamCode" {...register('targetExamCode')}>
                <option value="">Select exam</option>
                {exams.data?.map((e) => <option key={e.code} value={e.code}>{e.name}</option>)}
              </NativeSelect>
            </FormField>
            <FormField id="city" label="City" error={errors.city}>
              <Input id="city" {...register('city')} />
            </FormField>
            <FormField id="state" label="State" error={errors.state}>
              <Input id="state" {...register('state')} />
            </FormField>
          </div>
        </CardContent>
        <CardFooter className="mt-6 justify-end">
          <Button type="submit" disabled={!isDirty} loading={save.isPending}>Save changes</Button>
        </CardFooter>
      </form>
    </Card>
  )
}

function PasswordForm() {
  const navigate = useNavigate()
  const qc = useQueryClient()
  const clear = useAuthStore((s) => s.clear)
  const form = useForm<ChangePasswordValues>({
    resolver: zodResolver(changePasswordSchema),
    defaultValues: { currentPassword: '', newPassword: '', confirmPassword: '' },
  })
  const { register, handleSubmit, setError, formState: { errors } } = form

  const change = useMutation({
    mutationFn: (v: ChangePasswordValues) => authApi.changePassword(v.currentPassword, v.newPassword),
    // The backend revokes every session (including this one) on password change.
    onSuccess: () => {
      clear()
      qc.clear()
      navigate('/login?reason=password-changed', { replace: true })
    },
    onError: (e) => {
      const err = toApiError(e)
      if (err.code === 'BAD_REQUEST') setError('currentPassword', { message: err.message })
      else if (!applyServerErrors(e, setError, ['newPassword'] as const)) toast.error(errorMessage(e))
    },
  })

  return (
    <Card>
      <CardHeader>
        <CardTitle>Change password</CardTitle>
        <CardDescription>You will be logged out of all devices.</CardDescription>
      </CardHeader>
      <form onSubmit={handleSubmit((v) => change.mutate(v))} noValidate>
        <CardContent className="grid gap-4">
          <FormField id="currentPassword" label="Current password" error={errors.currentPassword}>
            <Input id="currentPassword" type="password" autoComplete="current-password"
                   aria-invalid={!!errors.currentPassword} {...register('currentPassword')} />
          </FormField>
          <FormField id="newPassword" label="New password" error={errors.newPassword}
                     hint="8+ characters with a letter and a digit">
            <Input id="newPassword" type="password" autoComplete="new-password"
                   aria-invalid={!!errors.newPassword} {...register('newPassword')} />
          </FormField>
          <FormField id="confirmNew" label="Confirm new password" error={errors.confirmPassword}>
            <Input id="confirmNew" type="password" autoComplete="new-password"
                   aria-invalid={!!errors.confirmPassword} {...register('confirmPassword')} />
          </FormField>
        </CardContent>
        <CardFooter className="mt-6 justify-end">
          <Button type="submit" variant="outline" loading={change.isPending}>Update password</Button>
        </CardFooter>
      </form>
    </Card>
  )
}
