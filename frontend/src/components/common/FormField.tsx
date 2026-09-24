import type { ReactNode } from 'react'
import type { FieldError } from 'react-hook-form'
import { Label } from '@/components/ui/label'

/**
 * Label + control + validation message, wired for accessibility. Pass the control as a
 * child with `id={id}` and `aria-invalid={!!error}`.
 */
export function FormField({ id, label, error, hint, children, action }:
                            { id: string; label: string; error?: FieldError; hint?: string; children: ReactNode;
                              action?: ReactNode }) {
  return (
    <div className="grid gap-2">
      <div className="flex items-center justify-between">
        <Label htmlFor={id}>{label}</Label>
        {action}
      </div>
      {children}
      {error?.message ? (
        <p id={`${id}-error`} className="text-destructive text-sm" role="alert">{error.message}</p>
      ) : hint ? (
        <p className="text-muted-foreground text-xs">{hint}</p>
      ) : null}
    </div>
  )
}
