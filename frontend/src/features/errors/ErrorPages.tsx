import { Link } from 'react-router-dom'
import { Compass, ShieldX } from 'lucide-react'
import { Button } from '@/components/ui/button'

export function NotFoundPage() {
  return (
    <div className="flex min-h-[60vh] flex-col items-center justify-center gap-4 p-6 text-center">
      <Compass className="text-muted-foreground size-12" />
      <div>
        <h1 className="text-2xl font-semibold">Page not found</h1>
        <p className="text-muted-foreground mt-1">The page you are looking for does not exist or has moved.</p>
      </div>
      <Button asChild><Link to="/">Back to home</Link></Button>
    </div>
  )
}

export function ForbiddenPage() {
  return (
    <div className="flex min-h-[60vh] flex-col items-center justify-center gap-4 p-6 text-center">
      <ShieldX className="text-destructive size-12" />
      <div>
        <h1 className="text-2xl font-semibold">No access</h1>
        <p className="text-muted-foreground mt-1">Your account does not have permission to view this page.</p>
      </div>
      <Button asChild variant="outline"><Link to="/dashboard">Go to dashboard</Link></Button>
    </div>
  )
}
