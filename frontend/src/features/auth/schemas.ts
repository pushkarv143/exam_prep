import { z } from 'zod'

/** Must match PasswordPolicy.java (8-72 chars, at least one letter and one digit). */
const password = z.string()
  .min(8, 'At least 8 characters')
  .max(72, 'At most 72 characters')
  .regex(/[A-Za-z]/, 'Include at least one letter')
  .regex(/\d/, 'Include at least one digit')

/** 10-digit Indian mobile number, optional. */
const optionalPhone = z.string().trim()
  .refine((v) => v === '' || /^[6-9]\d{9}$/.test(v), 'Enter a valid 10-digit mobile number')

export const loginSchema = z.object({
  identifier: z.string().trim().min(1, 'Enter your email or mobile number'),
  password: z.string().min(1, 'Enter your password'),
})

export const registerSchema = z.object({
  fullName: z.string().trim().min(2, 'Enter your full name').max(150),
  email: z.email('Enter a valid email address'),
  phone: optionalPhone,
  targetExamCode: z.string(),
  password,
  confirmPassword: z.string(),
}).refine((v) => v.password === v.confirmPassword, { path: ['confirmPassword'], message: 'Passwords do not match' })

export const forgotSchema = z.object({
  email: z.email('Enter a valid email address'),
})

export const resetSchema = z.object({
  newPassword: password,
  confirmPassword: z.string(),
}).refine((v) => v.newPassword === v.confirmPassword, { path: ['confirmPassword'], message: 'Passwords do not match' })

export const profileSchema = z.object({
  fullName: z.string().trim().min(2, 'Enter your full name').max(150),
  phone: optionalPhone,
  targetExamCode: z.string(),
  city: z.string().trim().max(100),
  state: z.string().trim().max(100),
})

export const changePasswordSchema = z.object({
  currentPassword: z.string().min(1, 'Enter your current password'),
  newPassword: password,
  confirmPassword: z.string(),
}).refine((v) => v.newPassword === v.confirmPassword, { path: ['confirmPassword'], message: 'Passwords do not match' })

export type LoginValues = z.infer<typeof loginSchema>
export type RegisterValues = z.infer<typeof registerSchema>
export type ForgotValues = z.infer<typeof forgotSchema>
export type ResetValues = z.infer<typeof resetSchema>
export type ProfileValues = z.infer<typeof profileSchema>
export type ChangePasswordValues = z.infer<typeof changePasswordSchema>
