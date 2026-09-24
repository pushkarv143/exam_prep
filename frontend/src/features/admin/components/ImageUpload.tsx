import { useRef, useState } from 'react'
import { ImagePlus, X } from 'lucide-react'
import { toast } from 'sonner'
import { adminApi } from '@/api/admin'
import { Button } from '@/components/ui/button'
import { errorMessage } from '@/lib/errors'
import type { Media } from '@/types/exam'

const MAX_BYTES = 2 * 1024 * 1024

/** Uploads an image to object storage (QUESTION_IMAGE) and returns its public URL. */
export function useImageUpload() {
  const [uploading, setUploading] = useState(false)
  const upload = async (file: File): Promise<string | null> => {
    if (!/^image\/(png|jpeg|webp|gif)$/.test(file.type)) {
      toast.error('Use a PNG, JPEG, WebP or GIF image')
      return null
    }
    if (file.size > MAX_BYTES) {
      toast.error('Images must be 2 MB or smaller')
      return null
    }
    setUploading(true)
    try {
      const stored = await adminApi.upload(file, 'QUESTION_IMAGE')
      return stored.url ?? null
    } catch (e) {
      toast.error(errorMessage(e))
      return null
    } finally {
      setUploading(false)
    }
  }
  return { upload, uploading }
}

/** A button that opens the file picker and uploads the chosen image. */
export function UploadButton({ onUploaded, label = 'Add image', size = 'sm' }:
                               { onUploaded: (url: string) => void; label?: string; size?: 'sm' | 'icon' }) {
  const input = useRef<HTMLInputElement>(null)
  const { upload, uploading } = useImageUpload()
  return (
    <>
      <Button type="button" variant="outline" size={size} loading={uploading} onClick={() => input.current?.click()}
              aria-label={size === 'icon' ? label : undefined}>
        {!uploading && <ImagePlus />}{size !== 'icon' && label}
      </Button>
      <input ref={input} type="file" accept="image/png,image/jpeg,image/webp,image/gif" className="sr-only" tabIndex={-1}
             onChange={async (e) => {
               const file = e.target.files?.[0]
               e.target.value = ''
               if (!file) return
               const url = await upload(file)
               if (url) onUploaded(url)
             }} />
    </>
  )
}

/** Editable image list (question figures, solution figures). */
export function ImageList({ images, onChange }: { images: Media[]; onChange: (m: Media[]) => void }) {
  return (
    <div className="space-y-2">
      {images.length > 0 && (
        <div className="flex flex-wrap gap-3">
          {images.map((img, i) => (
            <div key={img.url + i} className="relative">
              <img src={img.url} alt={img.alt ?? ''} className="h-24 rounded border bg-white object-contain" />
              <button type="button" aria-label="Remove image" onClick={() => onChange(images.filter((_, j) => j !== i))}
                      className="bg-destructive absolute -top-2 -right-2 rounded-full p-0.5 text-white shadow">
                <X className="size-3.5" />
              </button>
            </div>
          ))}
        </div>
      )}
      {images.length < 10 && <UploadButton onUploaded={(url) => onChange([...images, { url }])} />}
    </div>
  )
}
