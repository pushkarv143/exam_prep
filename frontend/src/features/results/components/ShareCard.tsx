import { useState } from 'react'
import { Download, Loader2, Share2 } from 'lucide-react'
import { toast } from 'sonner'
import { Button } from '@/components/ui/button'
import { formatClock, formatNumber } from '@/lib/format'
import type { Result } from '@/types/exam'

/** Draws a 1080×1080 branded scorecard onto a canvas and returns it as a PNG blob. */
async function drawShareCard(r: Result): Promise<Blob | null> {
  const size = 1080
  const canvas = document.createElement('canvas')
  canvas.width = size
  canvas.height = size
  const ctx = canvas.getContext('2d')
  if (!ctx) return null

  // Background gradient.
  const grad = ctx.createLinearGradient(0, 0, size, size)
  grad.addColorStop(0, '#4f2fb3')
  grad.addColorStop(1, '#2a1a6b')
  ctx.fillStyle = grad
  ctx.fillRect(0, 0, size, size)

  const cx = size / 2
  ctx.textAlign = 'center'
  ctx.fillStyle = 'rgba(255,255,255,0.85)'
  ctx.font = '600 34px Inter, sans-serif'
  ctx.fillText('SCORECARD', cx, 120)

  ctx.fillStyle = '#ffffff'
  ctx.font = '700 46px Inter, sans-serif'
  wrapText(ctx, r.testTitle, cx, 200, size - 160, 56)

  // Big score.
  ctx.font = '800 180px Inter, sans-serif'
  ctx.fillText(`${formatNumber(r.score, 0)}`, cx, 520)
  ctx.font = '500 40px Inter, sans-serif'
  ctx.fillStyle = 'rgba(255,255,255,0.8)'
  ctx.fillText(`out of ${formatNumber(r.maxScore, 0)} marks  ·  ${formatNumber(r.percentage, 1)}%`, cx, 585)

  // Metric row.
  const metrics: [string, string][] = []
  if (r.ranked && r.rank != null) metrics.push(['Rank', `#${formatNumber(r.rank, 0)}`])
  if (r.ranked && r.percentile != null) metrics.push(['Percentile', formatNumber(r.percentile, 2)])
  metrics.push(['Accuracy', `${formatNumber(r.accuracy, 0)}%`])
  metrics.push(['Time', formatClock(r.timeTakenSeconds ?? 0)])

  const cardW = 210
  const gap = 24
  const totalW = metrics.length * cardW + (metrics.length - 1) * gap
  let x = cx - totalW / 2
  const y = 680
  for (const [label, value] of metrics) {
    roundRect(ctx, x, y, cardW, 170, 20)
    ctx.fillStyle = 'rgba(255,255,255,0.12)'
    ctx.fill()
    ctx.fillStyle = '#ffffff'
    ctx.font = '700 52px Inter, sans-serif'
    ctx.fillText(value, x + cardW / 2, y + 90)
    ctx.fillStyle = 'rgba(255,255,255,0.75)'
    ctx.font = '500 28px Inter, sans-serif'
    ctx.fillText(label, x + cardW / 2, y + 135)
    x += cardW + gap
  }

  ctx.fillStyle = 'rgba(255,255,255,0.7)'
  ctx.font = '500 30px Inter, sans-serif'
  ctx.fillText('ExamPrep', cx, 1010)

  return await new Promise((resolve) => canvas.toBlob(resolve, 'image/png'))
}

function roundRect(ctx: CanvasRenderingContext2D, x: number, y: number, w: number, h: number, r: number) {
  ctx.beginPath()
  ctx.moveTo(x + r, y)
  ctx.arcTo(x + w, y, x + w, y + h, r)
  ctx.arcTo(x + w, y + h, x, y + h, r)
  ctx.arcTo(x, y + h, x, y, r)
  ctx.arcTo(x, y, x + w, y, r)
  ctx.closePath()
}

function wrapText(ctx: CanvasRenderingContext2D, text: string, x: number, y: number, maxWidth: number, lineHeight: number) {
  const words = text.split(' ')
  let line = ''
  let curY = y
  for (const w of words) {
    const test = line ? `${line} ${w}` : w
    if (ctx.measureText(test).width > maxWidth && line) {
      ctx.fillText(line, x, curY)
      line = w
      curY += lineHeight
    } else {
      line = test
    }
  }
  ctx.fillText(line, x, curY)
}

/** Opens a clean printable scorecard the browser can "Save as PDF". */
function printScorecard(r: Result) {
  const w = window.open('', '_blank', 'width=800,height=1000')
  if (!w) {
    toast.error('Allow pop-ups to download the PDF scorecard.')
    return
  }
  const row = (label: string, value: string) =>
    `<tr><td>${label}</td><td style="text-align:right;font-weight:600">${value}</td></tr>`
  const sections = r.sections.map((s) =>
    `<tr><td>${escapeHtml(s.name)}</td><td style="text-align:right">${formatNumber(s.score)} / ${formatNumber(s.maxScore)}</td></tr>`).join('')
  w.document.write(`<!doctype html><html><head><meta charset="utf-8"><title>Scorecard — ${escapeHtml(r.testTitle)}</title>
    <style>
      *{font-family:Inter,Arial,sans-serif;color:#1a1a1a}
      body{max-width:640px;margin:40px auto;padding:0 24px}
      h1{font-size:22px;margin:0 0 4px}
      .sub{color:#666;font-size:13px;margin-bottom:24px}
      .hero{background:#4f2fb3;color:#fff;border-radius:16px;padding:24px;text-align:center;margin-bottom:24px}
      .hero .score{font-size:56px;font-weight:800;line-height:1}
      .hero .pct{opacity:.85;margin-top:6px}
      table{width:100%;border-collapse:collapse;margin:8px 0 24px}
      td{padding:8px 4px;border-bottom:1px solid #eee;font-size:14px}
      h2{font-size:15px;margin:16px 0 4px}
      .foot{color:#999;font-size:12px;text-align:center;margin-top:32px}
      @media print{body{margin:0}}
    </style></head><body onload="window.print()">
    <h1>${escapeHtml(r.testTitle)}</h1>
    <div class="sub">Attempt ${r.attemptNo}${r.ranked ? '' : ' · practice (not ranked)'}</div>
    <div class="hero">
      <div class="score">${formatNumber(r.score, 0)} / ${formatNumber(r.maxScore, 0)}</div>
      <div class="pct">${formatNumber(r.percentage, 1)}%${r.ranked && r.rank != null ? ` · Rank #${formatNumber(r.rank, 0)}` : ''}${r.ranked && r.percentile != null ? ` · ${formatNumber(r.percentile, 2)} %ile` : ''}</div>
    </div>
    <h2>Summary</h2>
    <table>
      ${row('Accuracy', `${formatNumber(r.accuracy, 1)}%`)}
      ${row('Correct', String(r.correct ?? 0))}
      ${row('Incorrect', String(r.incorrect ?? 0))}
      ${row('Not attempted', String(r.unattempted ?? 0))}
      ${row('Time taken', formatClock(r.timeTakenSeconds ?? 0))}
    </table>
    <h2>Section scores</h2>
    <table>${sections}</table>
    <div class="foot">Generated by ExamPrep</div>
    </body></html>`)
  w.document.close()
}

function escapeHtml(s: string) {
  return s.replace(/[&<>"']/g, (c) => ({ '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;' }[c]!))
}

export function ShareResult({ r }: { r: Result }) {
  const [busy, setBusy] = useState(false)

  const share = async () => {
    setBusy(true)
    try {
      const blob = await drawShareCard(r)
      if (!blob) throw new Error('Could not render the image')
      const file = new File([blob], 'scorecard.png', { type: 'image/png' })
      const nav = navigator as Navigator & { canShare?: (d: ShareData) => boolean }
      if (nav.canShare?.({ files: [file] }) && navigator.share) {
        await navigator.share({ files: [file], title: r.testTitle, text: `I scored ${formatNumber(r.percentage, 1)}% on ${r.testTitle}!` })
      } else {
        const url = URL.createObjectURL(blob)
        const a = document.createElement('a')
        a.href = url
        a.download = `scorecard-${r.testId}.png`
        a.click()
        URL.revokeObjectURL(url)
        toast.success('Scorecard image downloaded.')
      }
    } catch (e) {
      if ((e as Error).name !== 'AbortError') toast.error('Could not create the share image.')
    } finally {
      setBusy(false)
    }
  }

  return (
    <>
      <Button variant="outline" onClick={() => void share()} disabled={busy}>
        {busy ? <Loader2 className="animate-spin" /> : <Share2 />} Share card
      </Button>
      <Button variant="outline" onClick={() => printScorecard(r)}>
        <Download /> PDF
      </Button>
    </>
  )
}
