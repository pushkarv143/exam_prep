import { Suspense } from 'react'
import { Link, Outlet, ScrollRestoration } from 'react-router-dom'
import { PageLoader } from '@/components/common/States'
import { Navbar } from './Navbar'

function Footer() {
  return (
    <footer className="mt-16 border-t">
      <div className="text-muted-foreground mx-auto flex max-w-7xl flex-col gap-2 px-4 py-8 text-sm sm:flex-row sm:items-center sm:justify-between sm:px-6">
        <p>© {new Date().getFullYear()} ExamPrep. Mock tests for JEE Main, JEE Advanced &amp; NEET.</p>
        <div className="flex gap-4">
          <Link to="/series" className="hover:text-foreground">Test series</Link>
          <Link to="/register" className="hover:text-foreground">Sign up</Link>
        </div>
      </div>
    </footer>
  )
}

/** Marketing and browsing pages: full-width content. */
export function PublicLayout() {
  return (
    <div className="flex min-h-screen flex-col">
      <Navbar />
      <main className="flex-1">
        <Suspense fallback={<PageLoader />}><Outlet /></Suspense>
      </main>
      <Footer />
      <ScrollRestoration />
    </div>
  )
}

/** Signed-in area: a constrained content column. */
export function AppLayout() {
  return (
    <div className="bg-muted/30 flex min-h-screen flex-col">
      <Navbar />
      <main className="mx-auto w-full max-w-7xl flex-1 px-4 py-6 sm:px-6 sm:py-8">
        <Suspense fallback={<PageLoader />}><Outlet /></Suspense>
      </main>
      <ScrollRestoration />
    </div>
  )
}

/** Centered card for auth screens. */
export function AuthLayout() {
  return (
    <div className="flex min-h-screen flex-col">
      <Navbar />
      <main className="flex flex-1 items-start justify-center px-4 py-10 sm:items-center">
        <div className="w-full max-w-md">
          <Suspense fallback={<PageLoader />}><Outlet /></Suspense>
        </div>
      </main>
      <ScrollRestoration />
    </div>
  )
}

export function PageHeader({ title, description, actions }:
                             { title: string; description?: string; actions?: React.ReactNode }) {
  return (
    <div className="mb-6 flex flex-col gap-3 sm:flex-row sm:items-end sm:justify-between">
      <div>
        <h1 className="text-2xl font-semibold tracking-tight">{title}</h1>
        {description && <p className="text-muted-foreground mt-1">{description}</p>}
      </div>
      {actions}
    </div>
  )
}
