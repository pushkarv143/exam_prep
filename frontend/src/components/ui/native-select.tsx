import * as React from 'react'
import { ChevronDown } from 'lucide-react'
import { cn } from '@/lib/utils'

/**
 * A styled native <select>: accessible, mobile-friendly and dependency-free.
 * forwardRef so react-hook-form's register() works on React 18.
 */
const NativeSelect = React.forwardRef<HTMLSelectElement, React.ComponentProps<'select'>>(
  ({ className, children, ...props }, ref) => (
    <div className="relative">
      <select
        ref={ref}
        data-slot="native-select"
        className={cn(
          'border-input h-9 w-full appearance-none rounded-md border bg-transparent pl-3 pr-9 text-sm shadow-xs outline-none',
          'focus-visible:border-ring focus-visible:ring-ring/50 focus-visible:ring-[3px] disabled:opacity-50',
          'aria-invalid:border-destructive',
          className,
        )}
        {...props}
      >
        {children}
      </select>
      <ChevronDown className="text-muted-foreground pointer-events-none absolute top-1/2 right-3 size-4 -translate-y-1/2" />
    </div>
  ),
)
NativeSelect.displayName = 'NativeSelect'

export { NativeSelect }
