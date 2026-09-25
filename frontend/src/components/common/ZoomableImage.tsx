import { useCallback, useRef, useState } from 'react'
import { Maximize2, Minus, Plus, X } from 'lucide-react'
import { cn } from '@/lib/utils'

/**
 * An image that opens in a zoomable overlay on tap/click. On touch devices the overlay uses
 * native pinch-zoom (touch-action: pinch-zoom) inside a scroll container, so figures can be
 * inspected without ever breaking the page layout. Desktop gets +/- buttons.
 */
export function ZoomableImage({ src, alt, className, thumbClassName }:
  { src: string; alt?: string; className?: string; thumbClassName?: string }) {
  const [open, setOpen] = useState(false)
  const [scale, setScale] = useState(1)
  const containerRef = useRef<HTMLDivElement>(null)

  const close = useCallback(() => { setOpen(false); setScale(1) }, [])

  return (
    <>
      <button type="button" onClick={() => setOpen(true)}
              className={cn('group relative inline-block cursor-zoom-in overflow-hidden rounded border bg-white', thumbClassName)}
              aria-label={alt ? `Zoom image: ${alt}` : 'Zoom image'}>
        <img src={src} alt={alt ?? ''} loading="lazy" className={cn('max-h-64', className)} />
        <span className="bg-background/80 text-foreground absolute right-1 bottom-1 rounded p-1 opacity-0 shadow transition-opacity group-hover:opacity-100">
          <Maximize2 className="size-3.5" />
        </span>
      </button>

      {open && (
        <div className="fixed inset-0 z-[100] flex flex-col bg-black/90 backdrop-blur-sm" role="dialog" aria-modal="true"
             aria-label={alt ?? 'Zoomed image'} onClick={close}>
          <div className="flex justify-end gap-2 p-3">
            <button type="button" onClick={(e) => { e.stopPropagation(); setScale((s) => Math.max(1, s - 0.5)) }}
                    className="rounded-full bg-white/15 p-2 text-white hover:bg-white/25" aria-label="Zoom out"><Minus className="size-5" /></button>
            <button type="button" onClick={(e) => { e.stopPropagation(); setScale((s) => Math.min(5, s + 0.5)) }}
                    className="rounded-full bg-white/15 p-2 text-white hover:bg-white/25" aria-label="Zoom in"><Plus className="size-5" /></button>
            <button type="button" onClick={close}
                    className="rounded-full bg-white/15 p-2 text-white hover:bg-white/25" aria-label="Close"><X className="size-5" /></button>
          </div>
          <div ref={containerRef} className="flex-1 overflow-auto p-4" style={{ touchAction: 'pinch-zoom' }}
               onClick={(e) => e.stopPropagation()}>
            <img src={src} alt={alt ?? ''}
                 className="mx-auto origin-top rounded bg-white transition-transform"
                 style={{ transform: `scale(${scale})`, maxWidth: '100%' }} />
          </div>
          <p className="pb-3 text-center text-xs text-white/70">Pinch to zoom · tap outside to close</p>
        </div>
      )}
    </>
  )
}
