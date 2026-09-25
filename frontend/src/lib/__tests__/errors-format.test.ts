import { AxiosError, type AxiosResponse, type InternalAxiosRequestConfig } from 'axios'
import { describe, expect, it, vi } from 'vitest'
import { safeNext } from '@/components/routing/Guards'
import { ApiError, applyServerErrors, errorMessage, toApiError } from '@/lib/errors'
import { formatClock, formatDuration, formatPrice, initials } from '@/lib/format'

function axiosError(status: number, body: unknown) {
  const config = { headers: {} } as InternalAxiosRequestConfig
  return new AxiosError('x', 'ERR', config, null, { status, data: body, headers: {}, config, statusText: '' } as AxiosResponse)
}

describe('toApiError', () => {
  it('reads the backend envelope', () => {
    const e = toApiError(axiosError(409, { success: false, error: { code: 'DUPLICATE_RESOURCE', message: 'Exists', requestId: 'r-1' } }))
    expect(e).toBeInstanceOf(ApiError)
    expect(e).toMatchObject({ status: 409, code: 'DUPLICATE_RESOURCE', message: 'Exists', requestId: 'r-1' })
  })

  it('classifies missing responses as network errors', () => {
    const e = toApiError(new AxiosError('Network Error', 'ERR_NETWORK'))
    expect(e.isNetwork).toBe(true)
    expect(e.message).toMatch(/internet connection/)
  })

  it('joins validation violations into one message', () => {
    const e = axiosError(400, { error: { code: 'VALIDATION_FAILED', message: 'Request validation failed',
      violations: [{ field: 'email', message: 'must be a well-formed email address' }] } })
    expect(errorMessage(e)).toBe('email: must be a well-formed email address')
  })

  it('maps server field violations onto known form fields only', () => {
    const setError = vi.fn()
    const e = axiosError(400, { error: { code: 'VALIDATION_FAILED', message: '', violations: [
      { field: 'phone', message: 'bad phone' }, { field: 'unknownField', message: 'ignored' }] } })
    expect(applyServerErrors(e, setError, ['phone', 'email'] as const)).toBe(true)
    expect(setError).toHaveBeenCalledExactlyOnceWith('phone', { type: 'server', message: 'bad phone' })
  })
})

describe('format', () => {
  it('formats rupees Indian-style', () => {
    expect(formatPrice(499)).toBe('₹499')
    expect(formatPrice(149999)).toBe('₹1,49,999')
    expect(formatPrice(99.5)).toBe('₹99.50')
  })

  it('formats durations and clocks', () => {
    expect(formatDuration(180)).toBe('3 h')
    expect(formatDuration(200)).toBe('3 h 20 min')
    expect(formatDuration(45)).toBe('45 min')
    expect(formatClock(3725)).toBe('1:02:05')
    expect(formatClock(309)).toBe('5:09')
    expect(formatClock(-4)).toBe('0:00')
  })

  it('builds initials', () => {
    expect(initials('Riya Sharma')).toBe('RS')
    expect(initials('madonna')).toBe('M')
    expect(initials('Sana (Support Agent)')).toBe('S')
    expect(initials('Chitra Rao (Content Manager)')).toBe('CR')
  })
})

describe('safeNext (open-redirect guard)', () => {
  it('only allows same-site relative paths', () => {
    expect(safeNext('/series/abc?x=1')).toBe('/series/abc?x=1')
    expect(safeNext('//evil.com')).toBe('/dashboard')
    expect(safeNext('https://evil.com')).toBe('/dashboard')
    expect(safeNext(null)).toBe('/dashboard')
  })
})
