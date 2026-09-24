import { useQuery } from '@tanstack/react-query'
import { Activity, IndianRupee, ListChecks, Users } from 'lucide-react'
import { Area, AreaChart, Bar, BarChart, CartesianGrid, ResponsiveContainer, Tooltip, XAxis, YAxis } from 'recharts'
import { adminApi } from '@/api/admin'
import { ErrorState, PageLoader } from '@/components/common/States'
import { PageHeader } from '@/components/layout/Layouts'
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from '@/components/ui/card'
import { formatNumber, formatPrice } from '@/lib/format'

export default function AdminDashboardPage() {
  const dash = useQuery({ queryKey: ['admin', 'dashboard'], queryFn: adminApi.dashboard, refetchInterval: 60_000 })
  if (dash.isError) return <ErrorState error={dash.error} onRetry={() => dash.refetch()} />
  if (dash.isPending) return <PageLoader />
  const d = dash.data
  const shortDate = (iso: string) => new Date(iso).toLocaleDateString('en-IN', { day: 'numeric', month: 'short' })

  return (
    <>
      <PageHeader title="Dashboard" description="Platform activity at a glance" />
      <div className="grid gap-4 sm:grid-cols-2 xl:grid-cols-4">
        <Kpi icon={Users} label="Students" value={formatNumber(d.usersByRole.STUDENT ?? 0, 0)}
             hint={`+${formatNumber(d.newStudentsLast7Days, 0)} in the last 7 days`} />
        <Kpi icon={Activity} label="Live attempts" value={formatNumber(d.liveAttempts, 0)}
             hint={`${formatNumber(d.attemptsToday, 0)} today · ${formatNumber(d.attemptsTotal, 0)} total`} />
        <Kpi icon={IndianRupee} label="Revenue (30 days)" value={formatPrice(d.revenue.last30Days)}
             hint={`${formatPrice(d.revenue.today)} today · ${formatPrice(d.revenue.total)} all time`} />
        <Kpi icon={ListChecks} label="Published tests"
             value={formatNumber((d.testsByStatus.PUBLISHED ?? 0) + (d.testsByStatus.LIVE ?? 0), 0)}
             hint={`${formatNumber(d.testsByStatus.DRAFT ?? 0, 0)} drafts`} />
      </div>

      <div className="mt-6 grid gap-6 xl:grid-cols-2">
        <Card>
          <CardHeader><CardTitle>Attempts per day</CardTitle><CardDescription>Last 30 days</CardDescription></CardHeader>
          <CardContent className="h-64">
            <ResponsiveContainer width="100%" height="100%">
              <BarChart data={d.attemptsDaily} margin={{ top: 5, right: 8, bottom: 0, left: -16 }}>
                <CartesianGrid strokeDasharray="3 3" stroke="var(--border)" />
                <XAxis dataKey="date" tickFormatter={shortDate} tick={{ fontSize: 11 }} minTickGap={16} />
                <YAxis allowDecimals={false} tick={{ fontSize: 11 }} />
                <Tooltip labelFormatter={(l) => shortDate(String(l))} formatter={(v) => [v, 'Attempts']} />
                <Bar dataKey="count" fill="var(--primary)" radius={[3, 3, 0, 0]} />
              </BarChart>
            </ResponsiveContainer>
          </CardContent>
        </Card>
        <Card>
          <CardHeader><CardTitle>Revenue per day</CardTitle><CardDescription>Last 30 days, captured payments</CardDescription></CardHeader>
          <CardContent className="h-64">
            <ResponsiveContainer width="100%" height="100%">
              <AreaChart data={d.revenue.daily.map((p) => ({ ...p, amount: Number(p.amount ?? 0) }))}
                         margin={{ top: 5, right: 8, bottom: 0, left: 0 }}>
                <CartesianGrid strokeDasharray="3 3" stroke="var(--border)" />
                <XAxis dataKey="date" tickFormatter={shortDate} tick={{ fontSize: 11 }} minTickGap={16} />
                <YAxis tick={{ fontSize: 11 }} />
                <Tooltip labelFormatter={(l) => shortDate(String(l))} formatter={(v) => [formatPrice(Number(v)), 'Revenue']} />
                <Area type="monotone" dataKey="amount" stroke="var(--success)" fill="var(--success)" fillOpacity={0.15} />
              </AreaChart>
            </ResponsiveContainer>
          </CardContent>
        </Card>
      </div>

      <div className="mt-6 grid gap-6 xl:grid-cols-2">
        <Card>
          <CardHeader><CardTitle>Top series by revenue</CardTitle></CardHeader>
          <CardContent>
            {d.revenue.topSeries.length === 0 ? <p className="text-muted-foreground text-sm">No payments yet.</p> : (
              <ul className="divide-y text-sm">
                {d.revenue.topSeries.map((s) => (
                  <li key={s.seriesId} className="flex justify-between gap-3 py-2">
                    <span className="truncate">{s.name}</span>
                    <span className="shrink-0 tabular-nums">{formatPrice(s.revenue)} <span className="text-muted-foreground">· {s.payments}</span></span>
                  </li>
                ))}
              </ul>
            )}
          </CardContent>
        </Card>
        <Card>
          <CardHeader><CardTitle>Users &amp; tests</CardTitle></CardHeader>
          <CardContent className="grid grid-cols-2 gap-6 text-sm">
            <Breakdown title="Users by role" data={d.usersByRole} />
            <Breakdown title="Tests by status" data={d.testsByStatus} />
          </CardContent>
        </Card>
      </div>
    </>
  )
}

function Kpi({ icon: Icon, label, value, hint }: { icon: typeof Users; label: string; value: string; hint?: string }) {
  return (
    <Card className="gap-2 py-5">
      <CardContent className="flex items-start gap-3">
        <div className="bg-primary/10 text-primary rounded-lg p-2"><Icon className="size-5" /></div>
        <div className="min-w-0">
          <p className="text-muted-foreground text-sm">{label}</p>
          <p className="truncate text-2xl font-semibold">{value}</p>
          {hint && <p className="text-muted-foreground text-xs">{hint}</p>}
        </div>
      </CardContent>
    </Card>
  )
}

function Breakdown({ title, data }: { title: string; data: Record<string, number> }) {
  return (
    <div>
      <p className="text-muted-foreground mb-2 text-xs font-semibold uppercase">{title}</p>
      <ul className="space-y-1">
        {Object.entries(data).map(([k, v]) => (
          <li key={k} className="flex justify-between"><span className="capitalize">{k.toLowerCase()}</span><span className="tabular-nums">{formatNumber(v, 0)}</span></li>
        ))}
      </ul>
    </div>
  )
}
