import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { beforeEach, describe, expect, it } from 'vitest'
import { LanguagePicker } from '@/components/common/LanguagePicker'
import { useQuestionLanguage } from '@/store/language'

describe('LanguagePicker', () => {
  beforeEach(() => useQuestionLanguage.setState({ language: 'EN' }))

  it('always offers English and Hindi, with English selected by default', () => {
    render(<LanguagePicker />)
    expect(screen.getByRole('radio', { name: 'English' })).toHaveAttribute('aria-checked', 'true')
    expect(screen.getByRole('radio', { name: 'Hindi' })).toHaveAttribute('aria-checked', 'false')
    expect(screen.getByText('हिन्दी')).toBeInTheDocument()
  })

  it('stores the choice as the question language used by the exam and solutions', async () => {
    render(<LanguagePicker />)
    await userEvent.click(screen.getByRole('radio', { name: 'Hindi' }))
    expect(useQuestionLanguage.getState().language).toBe('HI')
    expect(screen.getByRole('radio', { name: 'Hindi' })).toHaveAttribute('aria-checked', 'true')
  })

  it('can hide the help text', () => {
    render(<LanguagePicker showHelp={false} />)
    expect(screen.queryByText(/shown in this language/)).not.toBeInTheDocument()
  })
})
