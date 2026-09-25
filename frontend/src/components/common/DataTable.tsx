import { useEffect, useState } from 'react'
import {
  flexRender, getCoreRowModel, useReactTable, type ColumnDef, type VisibilityState,
} from '@tanstack/react-table'
import { Columns3 } from 'lucide-react'
import { Button } from '@/components/ui/button'
import {
  DropdownMenu, DropdownMenuContent, DropdownMenuItem, DropdownMenuLabel, DropdownMenuTrigger,
} from '@/components/ui/dropdown-menu'
import { Skeleton } from '@/components/ui/skeleton'
import { cn } from '@/lib/utils'

/**
 * Server-driven table: the parent owns filtering and pagination (so it scales to millions of
 * rows), and this component renders rows with TanStack Table and a column chooser. The chooser
 * is remembered per table in localStorage.
 */
export function DataTable<T>({ id, columns, data, loading, onRowClick, rowKey, empty, toolbar }: {
  id: string
  columns: ColumnDef<T, unknown>[]
  data: T[]
  loading?: boolean
  onRowClick?: (row: T) => void
  rowKey: (row: T) => string
  empty?: React.ReactNode
  toolbar?: React.ReactNode
}) {
  const storageKey = `examprep-table:${id}`
  const [visibility, setVisibility] = useState<VisibilityState>(() => {
    try {
      return JSON.parse(localStorage.getItem(storageKey) ?? '{}') as VisibilityState
    } catch {
      return {}
    }
  })
  useEffect(() => {
    try {
      localStorage.setItem(storageKey, JSON.stringify(visibility))
    } catch {
      // storage unavailable: the choice just is not remembered
    }
  }, [storageKey, visibility])

  const table = useReactTable({
    data, columns, getCoreRowModel: getCoreRowModel(), getRowId: (r) => rowKey(r),
    state: { columnVisibility: visibility }, onColumnVisibilityChange: setVisibility,
  })

  return (
    <div className="bg-card overflow-hidden rounded-xl border">
      <div className="flex flex-wrap items-center gap-2 border-b px-3 py-2">
        <div className="flex flex-1 flex-wrap items-center gap-2">{toolbar}</div>
        <DropdownMenu>
          <DropdownMenuTrigger asChild>
            <Button variant="ghost" size="sm" aria-label="Choose columns"><Columns3 /> Columns</Button>
          </DropdownMenuTrigger>
          <DropdownMenuContent align="end" className="w-52">
            <DropdownMenuLabel>Show columns</DropdownMenuLabel>
            {table.getAllLeafColumns().filter((c) => c.getCanHide()).map((c) => (
              <DropdownMenuItem key={c.id} onSelect={(e) => { e.preventDefault(); c.toggleVisibility() }}
                                role="menuitemcheckbox" aria-checked={c.getIsVisible()}>
                <input type="checkbox" readOnly checked={c.getIsVisible()} className="accent-primary pointer-events-none" tabIndex={-1} />
                {typeof c.columnDef.header === 'string' ? c.columnDef.header : c.id}
              </DropdownMenuItem>
            ))}
          </DropdownMenuContent>
        </DropdownMenu>
      </div>
      <div className="overflow-x-auto">
        <table className="w-full text-sm">
          <thead>
            {table.getHeaderGroups().map((hg) => (
              <tr key={hg.id} className="text-muted-foreground border-b text-left">
                {hg.headers.map((h) => (
                  <th key={h.id} className="px-3 py-2.5 font-medium whitespace-nowrap">
                    {h.isPlaceholder ? null : flexRender(h.column.columnDef.header, h.getContext())}
                  </th>
                ))}
              </tr>
            ))}
          </thead>
          <tbody>
            {loading && data.length === 0 && Array.from({ length: 6 }).map((_, i) => (
              <tr key={`sk-${i}`} className="border-b last:border-0">
                <td colSpan={table.getVisibleLeafColumns().length} className="px-3 py-2"><Skeleton className="h-5" /></td>
              </tr>
            ))}
            {!loading && data.length === 0 && (
              <tr><td colSpan={table.getVisibleLeafColumns().length} className="px-3 py-10 text-center">{empty ?? 'No rows'}</td></tr>
            )}
            {table.getRowModel().rows.map((row) => (
              <tr key={row.id} tabIndex={onRowClick ? 0 : undefined}
                  onClick={onRowClick ? () => onRowClick(row.original) : undefined}
                  onKeyDown={onRowClick ? (e) => { if (e.key === 'Enter') onRowClick(row.original) } : undefined}
                  className={cn('border-b last:border-0', onRowClick && 'hover:bg-muted/50 focus-visible:bg-muted/50 cursor-pointer outline-none')}>
                {row.getVisibleCells().map((cell) => (
                  <td key={cell.id} className="px-3 py-2 align-top">{flexRender(cell.column.columnDef.cell, cell.getContext())}</td>
                ))}
              </tr>
            ))}
          </tbody>
        </table>
      </div>
    </div>
  )
}
