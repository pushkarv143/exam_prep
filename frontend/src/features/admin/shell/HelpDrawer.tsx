import { useLocation } from 'react-router-dom'
import { Keyboard, Lightbulb } from 'lucide-react'
import { Sheet, SheetContent, SheetDescription, SheetHeader, SheetTitle } from '@/components/ui/sheet'
import { ALL_NAV_ITEMS, navItemFor } from './adminNav'

const SHORTCUTS: [string, string][] = [
  ['Ctrl + K  /  ⌘ K', 'Command palette: go anywhere, search records'],
  ['?', 'Open this help'],
  ['g, then a letter', 'Jump to a page (shown below)'],
  ['Esc', 'Close dialogs and drawers'],
  ['Ctrl + Enter', 'Confirm a dialog with a reason field'],
]

/** Contextual help for the current page, plus keyboard shortcuts. Opens with "?". */
export function HelpDrawer({ open, onOpenChange }: { open: boolean; onOpenChange: (o: boolean) => void }) {
  const { pathname } = useLocation()
  const item = navItemFor(pathname)
  return (
    <Sheet open={open} onOpenChange={onOpenChange}>
      <SheetContent className="sm:max-w-md">
        <SheetHeader>
          <SheetTitle>{item ? `Help: ${item.label}` : 'Help'}</SheetTitle>
          <SheetDescription>{item?.help.summary ?? 'Tips for the admin portal.'}</SheetDescription>
        </SheetHeader>
        {item && item.help.tips.length > 0 && (
          <section>
            <h3 className="mb-2 flex items-center gap-2 text-sm font-semibold"><Lightbulb className="size-4" /> Good to know</h3>
            <ul className="list-disc space-y-1.5 pl-5 text-sm">
              {item.help.tips.map((t) => <li key={t}>{t}</li>)}
            </ul>
          </section>
        )}
        <section>
          <h3 className="mb-2 flex items-center gap-2 text-sm font-semibold"><Keyboard className="size-4" /> Keyboard shortcuts</h3>
          <dl className="grid grid-cols-[auto_1fr] gap-x-4 gap-y-1.5 text-sm">
            {SHORTCUTS.map(([k, d]) => (
              <div key={k} className="contents">
                <dt><kbd className="bg-muted rounded border px-1.5 py-0.5 font-mono text-xs">{k}</kbd></dt>
                <dd className="text-muted-foreground">{d}</dd>
              </div>
            ))}
            {ALL_NAV_ITEMS.filter((i) => i.shortcut).map((i) => (
              <div key={i.to} className="contents">
                <dt><kbd className="bg-muted rounded border px-1.5 py-0.5 font-mono text-xs">g {i.shortcut}</kbd></dt>
                <dd className="text-muted-foreground">{i.label}</dd>
              </div>
            ))}
          </dl>
        </section>
      </SheetContent>
    </Sheet>
  )
}
