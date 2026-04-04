import { Line, LineChart, ResponsiveContainer, Tooltip, XAxis, YAxis, CartesianGrid } from 'recharts'

export default function FindingsTrendChart({ data = [] }) {
  const hasData = data.length > 0

  return (
    <section className="page-card min-h-[460px]">
      <div className="page-card-header">
        <h3 className="page-card-title">Findings Over Time</h3>
      </div>

      {hasData ? (
        <div className="h-[360px] w-full">
          <ResponsiveContainer width="100%" height="100%">
            <LineChart data={data} margin={{ left: -14, right: 8, top: 4, bottom: 0 }}>
              <CartesianGrid vertical={false} stroke="rgba(255,255,255,0.05)" />
              <XAxis
                dataKey="name"
                axisLine={false}
                tickLine={false}
                tick={{ fill: '#71717a', fontSize: 12 }}
              />
              <YAxis
                axisLine={false}
                tickLine={false}
                allowDecimals={false}
                tick={{ fill: '#71717a', fontSize: 12 }}
              />
              <Tooltip
                contentStyle={{
                  backgroundColor: 'rgba(9, 9, 11, 0.96)',
                  border: '1px solid rgba(63, 63, 70, 0.78)',
                  borderRadius: '16px',
                  color: '#f4f4f5',
                }}
              />
              <Line
                type="monotone"
                dataKey="findings"
                stroke="#9fd655"
                strokeWidth={3}
                dot={{ r: 4, fill: '#9fd655' }}
                activeDot={{ r: 6, fill: '#b6e66f' }}
              />
            </LineChart>
          </ResponsiveContainer>
        </div>
      ) : (
        <div className="surface-card-inner flex h-[360px] items-center justify-center">
          <p className="text-sm text-zinc-400">Trend data will appear once scans produce findings.</p>
        </div>
      )}
    </section>
  )
}
