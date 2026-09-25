import { render, screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import type { AxiosResponse, InternalAxiosRequestConfig } from 'axios'
import { afterEach, describe, expect, it } from 'vitest'
import { ConfirmHost, useConfirm, type ConfirmResult } from '@/components/common/ConfirmDialog'
import { ApprovalPendingError } from '@/api/admin'
import { portalApi } from '@/api/portal'
import { api } from '@/lib/api'
import { ADMIN_NAV } from '../shell/adminNav'

function Harness({ reason, onResult }: { reason: 'required' | 'optional'; onResult: (r: ConfirmResult | null) => void }) {
  const confirm = useConfirm()
  return (
    <>
      <button onClick={async () => onResult(await confirm({ title: 'Archive test?', destructive: true, reason, confirmText: 'Archive' }))}>
        open
      </button>
      <ConfirmHost />
    </>
  )
}

describe('ConfirmDialog', () => {
  it('blocks confirm until a required reason is typed, then resolves with it', async () => {
    const results: (ConfirmResult | null)[] = []
    render(<Harness reason="required" onResult={(r) => results.push(r)} />)
    await userEvent.click(screen.getByText('open'))
    const button = await screen.findByRole('button', { name: 'Archive' })
    expect(button).toBeDisabled()
    await userEvent.type(screen.getByLabelText(/Reason/), '  duplicate paper  ')
    expect(button).toBeEnabled()
    await userEvent.click(button)
    await waitFor(() => expect(results).toEqual([{ reason: 'duplicate paper' }]))
  })

  it('resolves null on cancel', async () => {
    const results: (ConfirmResult | null)[] = []
    render(<Harness reason="optional" onResult={(r) => results.push(r)} />)
    await userEvent.click(screen.getByText('open'))
    await userEvent.click(await screen.findByRole('button', { name: 'Cancel' }))
    await waitFor(() => expect(results).toEqual([null]))
  })
})

describe('portal API writes', () => {
  const original = api.defaults.adapter
  afterEach(() => { api.defaults.adapter = original })

  function respond(status: number, data: unknown, seen: InternalAxiosRequestConfig[] = []) {
    api.defaults.adapter = async (config) => {
      seen.push(config)
      return { data: { success: true, data, timestamp: '' }, status, statusText: '', headers: {}, config } as AxiosResponse
    }
    return seen
  }

  it('turns a 202 approval answer into ApprovalPendingError', async () => {
    respond(202, { approvalRequired: true, request: { id: 'req-1' }, message: 'Needs approval' })
    const err = await portalApi.updateRole('TEACHER', { displayName: 'Teacher' }).catch((e: unknown) => e)
    expect(err).toBeInstanceOf(ApprovalPendingError)
    expect((err as ApprovalPendingError).requestId).toBe('req-1')
  })

  it('sends the reason as an encoded X-Reason header', async () => {
    const seen = respond(200, null)
    await portalApi.deleteRole('CUSTOM', { reason: 'not used — cleanup' })
    expect(seen[0].headers['X-Reason']).toBe(encodeURIComponent('not used — cleanup'))
  })
})

describe('admin navigation', () => {
  const visible = (perms: string[]) => {
    const set = new Set(perms)
    return ADMIN_NAV.flatMap((g) => g.items).filter((i) => i.perms.length === 0 || i.perms.some((p) => set.has(p))).map((i) => i.to)
  }

  it('shows only what the permissions allow', () => {
    const support = visible(['user.view'])
    expect(support).toContain('/admin')
    expect(support).toContain('/admin/users')
    expect(support).not.toContain('/admin/roles')
    expect(support).not.toContain('/admin/audit')
  })

  it('has unique routes and shortcuts', () => {
    const items = ADMIN_NAV.flatMap((g) => g.items)
    expect(new Set(items.map((i) => i.to)).size).toBe(items.length)
    const keys = items.map((i) => i.shortcut).filter(Boolean)
    expect(new Set(keys).size).toBe(keys.length)
  })
})
