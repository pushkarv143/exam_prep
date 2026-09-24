import { Link } from 'react-router-dom'
import { cn } from '@/lib/utils'

export function Logo({ className, to = '/' }: { className?: string; to?: string }) {
  return (
    <Link to={to} className={cn('flex items-center gap-2 font-semibold tracking-tight', className)}>
      <span className="bg-primary text-primary-foreground grid size-8 place-items-center rounded-lg text-sm font-bold">E</span>
      <span className="text-lg">Exam<span className="text-primary">Prep</span></span>
    </Link>
  )
}
