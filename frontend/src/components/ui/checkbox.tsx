import * as React from 'react'
import { cn } from '@/lib/utils'

type CheckboxProps = Omit<React.ComponentProps<'input'>, 'type' | 'onChange'> & {
  onCheckedChange?: (checked: boolean) => void
  onChange?: React.ChangeEventHandler<HTMLInputElement>
}

/**
 * A styled native checkbox. Native (not Radix) so it works with react-hook-form's register()
 * as well as in controlled mode through onCheckedChange.
 */
const Checkbox = React.forwardRef<HTMLInputElement, CheckboxProps>(
  ({ className, onCheckedChange, onChange, ...props }, ref) => (
    <input
      ref={ref}
      type="checkbox"
      data-slot="checkbox"
      className={cn(
        'border-input accent-primary size-4 shrink-0 cursor-pointer rounded border shadow-xs outline-none',
        'focus-visible:ring-ring/50 focus-visible:ring-[3px] disabled:cursor-not-allowed disabled:opacity-50',
        className,
      )}
      onChange={(e) => {
        onChange?.(e)
        onCheckedChange?.(e.target.checked)
      }}
      {...props}
    />
  ),
)
Checkbox.displayName = 'Checkbox'

export { Checkbox }
