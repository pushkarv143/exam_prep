import { Link } from 'react-router-dom'
import { BarChart3, Clock, Medal, MonitorCheck, ShieldCheck, Sparkles } from 'lucide-react'
import { useExams } from '@/api/catalog'
import { useSeriesList } from '@/api/series'
import { Skeleton } from '@/components/ui/skeleton'
import { Button } from '@/components/ui/button'
import { SeriesCard } from '@/features/series/SeriesCard'
import { useIsAuthenticated } from '@/store/auth'

const FEATURES = [
  { icon: MonitorCheck, title: 'Real NTA interface', text: 'Question palette, mark for review and section tabs, exactly like the actual exam.' },
  { icon: Clock, title: 'Server-timed tests', text: 'A fair, tamper-proof timer with auto-save and resume if your connection drops.' },
  { icon: Medal, title: 'All-India rank', text: 'Live rank and percentile against every student who took the same test.' },
  { icon: BarChart3, title: 'Deep analytics', text: 'Subject and topic-wise accuracy, time per question, and your weak areas.' },
  { icon: Sparkles, title: 'Detailed solutions', text: 'Step-by-step solutions with properly typeset maths and chemistry.' },
  { icon: ShieldCheck, title: 'Latest patterns', text: 'JEE Main, JEE Advanced and NEET papers built on current marking schemes.' },
]

export default function LandingPage() {
  const signedIn = useIsAuthenticated()
  const exams = useExams()
  const featured = useSeriesList({ size: 6 })
  const examById = new Map((exams.data ?? []).map((e) => [e.id, e]))

  return (
    <>
      <section className="from-accent/60 to-background bg-gradient-to-b">
        <div className="mx-auto max-w-7xl px-4 py-16 text-center sm:px-6 sm:py-24">
          <p className="text-primary mb-4 text-sm font-semibold tracking-wide uppercase">JEE Main · JEE Advanced · NEET</p>
          <h1 className="mx-auto max-w-3xl text-4xl font-bold tracking-tight sm:text-5xl">
            Mock tests that feel like the <span className="text-primary">real exam</span>
          </h1>
          <p className="text-muted-foreground mx-auto mt-5 max-w-2xl text-lg">
            Practise in the exact exam interface, get your score and All-India rank the moment you submit, and know
            exactly which topics to fix next.
          </p>
          <div className="mt-8 flex flex-col justify-center gap-3 sm:flex-row">
            <Button size="lg" asChild>
              <Link to={signedIn ? '/dashboard' : '/register'}>{signedIn ? 'Go to dashboard' : 'Start free'}</Link>
            </Button>
            <Button size="lg" variant="outline" asChild><Link to="/series">Browse test series</Link></Button>
          </div>
          {exams.data && (
            <div className="mt-10 flex flex-wrap justify-center gap-3">
              {exams.data.map((e) => (
                <Link key={e.code} to={`/series?exam=${e.code}`}
                      className="bg-card hover:border-primary rounded-full border px-4 py-1.5 text-sm font-medium transition-colors">
                  {e.name}
                </Link>
              ))}
            </div>
          )}
        </div>
      </section>

      <section className="mx-auto max-w-7xl px-4 py-16 sm:px-6">
        <h2 className="text-center text-2xl font-semibold tracking-tight sm:text-3xl">Why students practise here</h2>
        <div className="mt-10 grid gap-6 sm:grid-cols-2 lg:grid-cols-3">
          {FEATURES.map((f) => (
            <div key={f.title} className="bg-card rounded-xl border p-6">
              <f.icon className="text-primary size-6" />
              <h3 className="mt-3 font-semibold">{f.title}</h3>
              <p className="text-muted-foreground mt-1 text-sm">{f.text}</p>
            </div>
          ))}
        </div>
      </section>

      <section className="mx-auto max-w-7xl px-4 pb-8 sm:px-6">
        <div className="mb-6 flex items-end justify-between">
          <h2 className="text-2xl font-semibold tracking-tight">Popular test series</h2>
          <Link to="/series" className="text-primary text-sm font-medium hover:underline">View all →</Link>
        </div>
        <div className="grid gap-5 sm:grid-cols-2 lg:grid-cols-3">
          {featured.isPending
            ? Array.from({ length: 3 }).map((_, i) => <Skeleton key={i} className="h-56 rounded-xl" />)
            : featured.data?.content.map((s) => (
              <div key={s.id} className="relative"><SeriesCard series={s} exam={examById.get(s.examId)} /></div>
            ))}
        </div>
      </section>
    </>
  )
}
