import { StrictMode } from 'react'
import { createRoot } from 'react-dom/client'
import { QueryClientProvider } from '@tanstack/react-query'
import { RouterProvider } from 'react-router-dom'
import { Toaster } from 'sonner'
import { ErrorBoundary } from '@/components/common/ErrorBoundary'
import { setSessionExpiredHandler } from '@/lib/api'
import { queryClient } from '@/lib/queryClient'
import { router } from '@/router'
import { initTheme, resolvedDark, useThemeStore } from '@/store/theme'
import './index.css'

// When the server ends the session (revoked, or refresh failed), drop cached user data
// and go to login. Use a client-side navigation, so there is no full reload.
setSessionExpiredHandler(() => {
  queryClient.clear()
  const here = window.location.pathname + window.location.search
  if (!window.location.pathname.startsWith('/login')) {
    void router.navigate(`/login?reason=session&next=${encodeURIComponent(here)}`, { replace: true })
  }
})

initTheme()

/** Toasts follow the light/dark choice. */
function ThemedToaster() {
  const mode = useThemeStore((s) => s.mode)
  return <Toaster richColors closeButton position="top-right" theme={resolvedDark(mode) ? 'dark' : 'light'} />
}

createRoot(document.getElementById('root')!).render(
  <StrictMode>
    <ErrorBoundary>
      <QueryClientProvider client={queryClient}>
        <RouterProvider router={router} />
        <ThemedToaster />
      </QueryClientProvider>
    </ErrorBoundary>
  </StrictMode>,
)
