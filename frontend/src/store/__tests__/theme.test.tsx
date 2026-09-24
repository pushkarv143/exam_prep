import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { beforeEach, describe, expect, it } from 'vitest'
import { DarkModeToggle, ThemeMenu } from '@/components/common/ThemeMenu'
import { applyTheme, initTheme, THEME_STORAGE_KEY, useThemeStore } from '../theme'

describe('theme', () => {
  beforeEach(() => {
    useThemeStore.setState({ mode: 'system', accent: 'indigo' })
    document.documentElement.className = ''
  })

  it('applies dark class, accent and color-scheme to <html>', () => {
    applyTheme({ mode: 'dark', accent: 'rose' })
    const html = document.documentElement
    expect(html.classList.contains('dark')).toBe(true)
    expect(html.dataset.accent).toBe('rose')
    expect(html.style.colorScheme).toBe('dark')
    applyTheme({ mode: 'system', accent: 'teal' })   // matchMedia stub says light
    expect(html.classList.contains('dark')).toBe(false)
  })

  it('toggle flips light/dark, persists, and syncs <html>', async () => {
    initTheme()
    render(<DarkModeToggle />)
    await userEvent.click(screen.getByRole('button', { name: 'Switch to dark mode' }))
    expect(useThemeStore.getState().mode).toBe('dark')
    expect(document.documentElement.classList.contains('dark')).toBe(true)
    expect(JSON.parse(localStorage.getItem(THEME_STORAGE_KEY)!).state.mode).toBe('dark')
    await userEvent.click(screen.getByRole('button', { name: 'Switch to light mode' }))
    expect(document.documentElement.classList.contains('dark')).toBe(false)
  })

  it('menu picks a mode and a colour', async () => {
    initTheme()
    render(<ThemeMenu />)
    await userEvent.click(screen.getByRole('button', { name: /Appearance/ }))
    await userEvent.click(screen.getByRole('menuitemradio', { name: 'Emerald' }))
    expect(useThemeStore.getState().accent).toBe('emerald')
    expect(document.documentElement.dataset.accent).toBe('emerald')
    await userEvent.click(screen.getByRole('menuitemradio', { name: /Dark/ }))
    expect(useThemeStore.getState().mode).toBe('dark')
  })
})
