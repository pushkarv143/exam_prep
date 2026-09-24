import { ChevronLeft, ChevronRight } from 'lucide-react'
import { Button } from '@/components/ui/button'

/** Zero-based page navigation that matches the backend's PageResponse. */
export function Pagination({ page, totalPages, onChange }:
                             { page: number; totalPages: number; onChange: (page: number) => void }) {
  if (totalPages <= 1) return null
  return (
    <nav className="flex items-center justify-center gap-3" aria-label="Pagination">
      <Button variant="outline" size="sm" disabled={page === 0} onClick={() => onChange(page - 1)}>
        <ChevronLeft /> Previous
      </Button>
      <span className="text-muted-foreground text-sm">Page {page + 1} of {totalPages}</span>
      <Button variant="outline" size="sm" disabled={page >= totalPages - 1} onClick={() => onChange(page + 1)}>
        Next <ChevronRight />
      </Button>
    </nav>
  )
}
