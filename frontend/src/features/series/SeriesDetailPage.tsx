import { Link, useLocation, useParams } from 'react-router-dom'
import { CalendarClock, CheckCircle2, FileText, ShieldCheck } from 'lucide-react'
import { toast } from 'sonner'
import { useExams } from '@/api/catalog'
import { useEnroll, useSeriesDetail } from '@/api/series'
import { EmptyState, ErrorState, PageLoader } from '@/components/common/States'
import { Badge } from '@/components/ui/badge'
import { Button } from '@/components/ui/button'
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from '@/components/ui/card'
import {
  Dialog, DialogContent, DialogDescription, DialogFooter, DialogHeader, DialogTitle,
} from '@/components/ui/dialog'
import { usePurchase } from '@/hooks/usePurchase'
import { formatDate, formatPrice, plural } from '@/lib/format'
import { useIsAuthenticated } from '@/store/auth'
import type { PublicSeries } from '@/types/domain'
import { validityText } from './SeriesCard'
import { TestList } from './TestList'

export default function SeriesDetailPage() {
  const { slug = '' } = useParams()
  const detail = useSeriesDetail(slug)
  const exams = useExams()
  const signedIn = useIsAuthenticated()

  if (detail.isPending) return <PageLoader />
  if (detail.isError) return <ErrorState error={detail.error} onRetry={() => detail.refetch()} />

  const { series, tests } = detail.data
  const exam = exams.data?.find((e) => e.id === series.examId)

  return (
    <div className="mx-auto max-w-7xl px-4 py-10 sm:px-6">
      <nav className="text-muted-foreground mb-4 text-sm"><Link to="/series" className="hover:underline">Test series</Link> / {series.name}</nav>
      <div className="grid gap-8 lg:grid-cols-[1fr_340px]">
        <div>
          <div className="flex flex-wrap gap-2">
            {exam && <Badge variant="secondary">{exam.name}</Badge>}
            {series.free && <Badge variant="success">Free</Badge>}
          </div>
          <h1 className="mt-3 text-3xl font-semibold tracking-tight">{series.name}</h1>
          {series.description && <p className="text-muted-foreground mt-3 max-w-3xl whitespace-pre-line">{series.description}</p>}
          <div className="text-muted-foreground mt-4 flex flex-wrap gap-x-6 gap-y-2 text-sm">
            <span className="flex items-center gap-1.5"><FileText className="size-4" /> {plural(series.testCount, 'test')}</span>
            {validityText(series) && (
              <span className="flex items-center gap-1.5"><CalendarClock className="size-4" /> {validityText(series)}</span>
            )}
          </div>

          <h2 className="mt-10 mb-4 text-xl font-semibold">Tests in this series</h2>
          {tests.length === 0
            ? <EmptyState title="Tests are being added" description="Check back soon." />
            : <TestList tests={tests} signedIn={signedIn} />}
        </div>

        <aside className="lg:sticky lg:top-24 lg:self-start">
          <AccessPanel series={series} signedIn={signedIn} />
        </aside>
      </div>
    </div>
  )
}

function AccessPanel({ series, signedIn }: { series: PublicSeries; signedIn: boolean }) {
  const location = useLocation()
  const enroll = useEnroll()
  const purchase = usePurchase(series.id)
  const access = series.myAccess

  let action: React.ReactNode
  if (access?.hasAccess) {
    action = (
      <div className="text-success flex items-start gap-2 text-sm">
        <CheckCircle2 className="mt-0.5 size-5 shrink-0" />
        <div>
          <p className="font-medium">You have access</p>
          {access.expiresAt && <p className="text-muted-foreground">Valid till {formatDate(access.expiresAt)}</p>}
        </div>
      </div>
    )
  } else if (!signedIn) {
    action = (
      <Button className="w-full" asChild>
        <Link to={`/login?next=${encodeURIComponent(location.pathname)}`}>
          {series.free ? 'Log in to enroll free' : 'Log in to buy'}
        </Link>
      </Button>
    )
  } else if (series.free) {
    action = (
      <Button className="w-full" loading={enroll.isPending}
              onClick={() => enroll.mutate(series.id, { onSuccess: () => toast.success('Enrolled! All tests are unlocked.') })}>
        Enroll for free
      </Button>
    )
  } else {
    action = (
      <Button className="w-full" size="lg" loading={purchase.busy} onClick={() => void purchase.start()}>
        Buy for {formatPrice(series.price)}
      </Button>
    )
  }

  return (
    <Card>
      <CardHeader>
        <CardDescription>Price</CardDescription>
        <CardTitle className="text-3xl">{series.free ? 'Free' : formatPrice(series.price)}</CardTitle>
      </CardHeader>
      <CardContent className="space-y-4">
        {action}
        <ul className="text-muted-foreground space-y-2 text-sm">
          <li className="flex gap-2"><CheckCircle2 className="text-success size-4 shrink-0" /> NTA-style exam interface</li>
          <li className="flex gap-2"><CheckCircle2 className="text-success size-4 shrink-0" /> Instant result, All-India rank &amp; percentile</li>
          <li className="flex gap-2"><CheckCircle2 className="text-success size-4 shrink-0" /> Detailed solutions &amp; topic-wise analysis</li>
          {!series.free && <li className="flex gap-2"><ShieldCheck className="text-success size-4 shrink-0" /> Secure payment via Razorpay</li>}
        </ul>
      </CardContent>

      <Dialog open={purchase.step === 'awaiting-mock-confirm'} onOpenChange={(o) => !o && purchase.cancelMock()}>
        <DialogContent>
          <DialogHeader>
            <DialogTitle>Test payment (mock mode)</DialogTitle>
            <DialogDescription>
              The server is running with PAYMENT_PROVIDER=MOCK. No real money moves. Confirm to simulate a successful
              payment of {formatPrice(series.price)}. It runs through the same server-side verification as Razorpay.
            </DialogDescription>
          </DialogHeader>
          <DialogFooter>
            <Button variant="outline" onClick={purchase.cancelMock}>Cancel</Button>
            <Button variant="success" onClick={() => void purchase.confirmMock()}>Simulate successful payment</Button>
          </DialogFooter>
        </DialogContent>
      </Dialog>
    </Card>
  )
}
