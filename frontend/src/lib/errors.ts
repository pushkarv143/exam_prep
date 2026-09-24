import { AxiosError } from 'axios'
import type { FieldValues, Path, UseFormSetError } from 'react-hook-form'
import type { ApiEnvelope, FieldViolation } from '@/types/api'

/** Normalised error for everything that fails in the API layer. */
export class ApiError extends Error {
  constructor(
    public readonly status: number,
    public readonly code: string,
    message: string,
    public readonly violations: FieldViolation[] = [],
    public readonly requestId?: string,
  ) {
    super(message)
    this.name = 'ApiError'
  }

  get isNetwork() {
    return this.status === 0
  }
}

export function toApiError(e: unknown): ApiError {
  if (e instanceof ApiError) return e
  if (e instanceof AxiosError) {
    const body = e.response?.data as ApiEnvelope<unknown> | undefined
    if (body?.error) {
      return new ApiError(e.response!.status, body.error.code, body.error.message, body.error.violations ?? [],
        body.error.requestId)
    }
    if (!e.response) {
      return new ApiError(0, 'NETWORK_ERROR', 'Cannot reach the server. Check your internet connection.')
    }
    return new ApiError(e.response.status, 'HTTP_' + e.response.status, e.message)
  }
  return new ApiError(-1, 'UNKNOWN', e instanceof Error ? e.message : 'Something went wrong')
}

/** User-facing message for any thrown value. */
export function errorMessage(e: unknown): string {
  const err = toApiError(e)
  if (err.code === 'VALIDATION_FAILED' && err.violations.length > 0) {
    return err.violations.map((v) => `${v.field}: ${v.message}`).join('; ')
  }
  return err.message
}

/**
 * Maps a backend 400 VALIDATION_FAILED onto react-hook-form fields, so server rules show
 * next to the right input. Returns true if at least one field error was applied.
 */
export function applyServerErrors<T extends FieldValues>(e: unknown, setError: UseFormSetError<T>,
                                                         fields: readonly Path<T>[]): boolean {
  const err = toApiError(e)
  let applied = false
  for (const v of err.violations) {
    const field = v.field as Path<T>
    if (fields.includes(field)) {
      setError(field, { type: 'server', message: v.message })
      applied = true
    }
  }
  return applied
}
