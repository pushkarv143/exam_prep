import { act, renderHook } from '@testing-library/react'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { attemptsApi } from '@/api/attempts'
import { useExamStore } from '../examStore'
import { leaveLimit, useAntiCheat } from '../useAntiCheat'

function setVisibility(state: 'visible' | 'hidden') {
  Object.defineProperty(document, 'visibilityState', { configurable: true, get: () => state })
  document.dispatchEvent(new Event('visibilitychange'))
}

describe('leaveLimit', () => {
  it('is the leave that makes the server auto-submit', () => {
    expect(leaveLimit(2)).toBe(3)
    expect(leaveLimit(0)).toBe(0)
  })
})

describe('useAntiCheat', () => {
  let focused = true

  beforeEach(() => {
    focused = true
    vi.spyOn(document, 'hasFocus').mockImplementation(() => focused)
    vi.spyOn(attemptsApi, 'events').mockImplementation(async (_id, batch) => ({
      tabSwitchCount: batch.filter((e) => e.type === 'TAB_SWITCH').length,
      fullscreenExitCount: 0, maxTabSwitches: 2, autoSubmitted: false,
    }))
    useExamStore.setState({
      antiCheat: { tabSwitchCount: 0, fullscreenExitCount: 0, maxTabSwitches: 2, autoSubmitted: false },
    })
    setVisibility('visible')
  })

  afterEach(() => vi.restoreAllMocks())

  const render = (finishing = false) =>
    renderHook(() => useAntiCheat('a1', true, () => undefined, () => finishing))

  it('counts one leave per away episode, however many signals it fires', async () => {
    const { result } = render()
    expect(result.current.limit).toBe(3)

    await act(async () => {
      focused = false
      window.dispatchEvent(new Event('blur'))
      setVisibility('hidden')           // minimising fires blur and visibilitychange
    })
    expect(result.current.away).toBe('blur')
    expect(result.current.leaves).toBe(1)
    const sent = vi.mocked(attemptsApi.events).mock.calls.flatMap(([, batch]) => batch)
    expect(sent.filter((e) => e.type === 'TAB_SWITCH')).toHaveLength(1)

    await act(async () => {
      focused = true
      setVisibility('visible')
      window.dispatchEvent(new Event('focus'))
    })
    expect(result.current.away).toBeNull()

    await act(async () => {
      focused = false
      window.dispatchEvent(new Event('blur'))
    })
    expect(result.current.leaves).toBe(2)
  })

  it('stays locked until the tab is visible and focused again', async () => {
    const { result } = render()
    await act(async () => { setVisibility('hidden') })
    expect(result.current.away).toBe('hidden')

    focused = false
    await act(async () => { setVisibility('visible') })
    expect(result.current.away).toBe('hidden')

    focused = true
    await act(async () => { result.current.checkBack() })
    expect(result.current.away).toBeNull()
  })

  it('ignores leaving once the test is being submitted', async () => {
    const { result } = render(true)
    await act(async () => { setVisibility('hidden') })
    expect(result.current.away).toBeNull()
    expect(result.current.leaves).toBe(0)
  })
})
