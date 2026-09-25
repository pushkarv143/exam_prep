import { Line, LineChart, ResponsiveContainer, YAxis } from 'recharts'

/** A tiny inline trend line (no axes/tooltip) for compact dashboard stats. */
export function Sparkline({ values, color = 'var(--primary)', height = 36 }:
  { values: number[]; color?: string; height?: number }) {
  if (values.length < 2) return null
  const data = values.map((v, i) => ({ i, v }))
  const min = Math.min(...values)
  const max = Math.max(...values)
  return (
    <div style={{ height }} aria-hidden>
      <ResponsiveContainer width="100%" height="100%">
        <LineChart data={data} margin={{ top: 2, right: 2, bottom: 2, left: 2 }}>
          <YAxis hide domain={[min - (max - min) * 0.1 - 0.01, max + (max - min) * 0.1 + 0.01]} />
          <Line type="monotone" dataKey="v" stroke={color} strokeWidth={2} dot={false} isAnimationActive={false} />
        </LineChart>
      </ResponsiveContainer>
    </div>
  )
}
