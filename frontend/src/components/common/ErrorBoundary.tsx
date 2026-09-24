import { Component, type ErrorInfo, type ReactNode } from 'react'
import { AlertTriangle } from 'lucide-react'
import { Button } from '@/components/ui/button'

interface State {
  error: Error | null
}

/**
 * Last line of defence for render errors: one broken widget must not blank the whole
 * app. Data-fetching errors are handled by the query layer instead.
 */
export class ErrorBoundary extends Component<{ children: ReactNode }, State> {
  state: State = { error: null }

  static getDerivedStateFromError(error: Error): State {
    return { error }
  }

  componentDidCatch(error: Error, info: ErrorInfo) {
    console.error('Unhandled render error', error, info.componentStack)
  }

  render() {
    if (!this.state.error) return this.props.children
    return (
      <div className="flex min-h-screen flex-col items-center justify-center gap-4 p-6 text-center">
        <AlertTriangle className="text-destructive size-10" />
        <div>
          <h1 className="text-xl font-semibold">This page crashed</h1>
          <p className="text-muted-foreground mt-1 text-sm">Your answers and progress are saved on the server.</p>
        </div>
        <div className="flex gap-2">
          <Button onClick={() => window.location.reload()}>Reload page</Button>
          <Button variant="outline" onClick={() => window.location.assign('/')}>Go home</Button>
        </div>
      </div>
    )
  }
}
