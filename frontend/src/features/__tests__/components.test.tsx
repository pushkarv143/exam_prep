import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { MemoryRouter } from 'react-router-dom'
import { describe, expect, it } from 'vitest'
import { SeriesCard } from '@/features/series/SeriesCard'
import { TestList } from '@/features/series/TestList'
import LoginPage from '@/features/auth/LoginPage'
import type { PublicSeries, PublicTest } from '@/types/domain'

const series: PublicSeries = {
  id: 's1', examId: 'e1', name: 'JEE Main Full Mock Series', slug: 'jee-main', price: 499, currency: 'INR',
  free: false, validityDays: 180, batchRestricted: false, testCount: 12,
}

function wrap(ui: React.ReactNode) {
  const qc = new QueryClient({ defaultOptions: { queries: { retry: false }, mutations: { retry: false } } })
  return render(<QueryClientProvider client={qc}><MemoryRouter>{ui}</MemoryRouter></QueryClientProvider>)
}

describe('SeriesCard', () => {
  it('shows price, test count and validity', () => {
    wrap(<SeriesCard series={series} />)
    expect(screen.getByTestId('series-price')).toHaveTextContent('₹499')
    expect(screen.getByText('12 tests')).toBeInTheDocument()
    expect(screen.getByText('180 days access')).toBeInTheDocument()
    expect(screen.getByRole('link', { name: series.name })).toHaveAttribute('href', '/series/jee-main')
  })

  it('marks free and enrolled series', () => {
    wrap(<SeriesCard series={{ ...series, free: true, price: 0, myAccess: { enrolled: true, hasAccess: true } }} />)
    expect(screen.getByTestId('series-price')).toHaveTextContent('Free')
    expect(screen.getByText('Enrolled')).toBeInTheDocument()
  })
})

describe('TestList', () => {
  const tests: PublicTest[] = [
    { id: 't1', title: 'Free Sample Test', pattern: 'CUSTOM', durationMinutes: 30, totalMarks: 40, totalQuestions: 10,
      availability: 'OPEN', free: true, displayOrder: 1, accessible: true },
    { id: 't2', title: 'Full Mock 1', pattern: 'JEE_MAIN', durationMinutes: 180, totalMarks: 300, totalQuestions: 90,
      availability: 'OPEN', free: false, displayOrder: 2, accessible: false },
  ]

  it('offers the attempt only for accessible tests', () => {
    wrap(<TestList tests={tests} signedIn />)
    expect(screen.getByRole('link', { name: 'Start' })).toHaveAttribute('href', '/tests/t1')
    expect(screen.getByText('Enroll in this series to unlock')).toBeInTheDocument()
    expect(screen.getByText('3 h')).toBeInTheDocument()
  })

  it('asks anonymous visitors to log in', () => {
    wrap(<TestList tests={tests} signedIn={false} />)
    expect(screen.getByRole('link', { name: 'Log in to attempt' })).toBeInTheDocument()
    expect(screen.getByText('Enroll in this series to unlock')).toBeInTheDocument()
  })
})

describe('LoginPage', () => {
  it('validates required fields before calling the server', async () => {
    wrap(<LoginPage />)
    await userEvent.click(screen.getByRole('button', { name: 'Log in' }))
    expect(await screen.findByText('Enter your email or mobile number')).toBeInTheDocument()
    expect(screen.getByText('Enter your password')).toBeInTheDocument()
  })
})
