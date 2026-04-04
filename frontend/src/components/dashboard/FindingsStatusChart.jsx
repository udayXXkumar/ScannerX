import { CircleAlert, ShieldCheck } from 'lucide-react'
import { Cell, Pie, PieChart, ResponsiveContainer } from 'recharts'

export default function FindingsStatusChart({
  openFindings = 0,
  resolvedFindings = 0,
}) {
  const totalFindings = openFindings + resolvedFindings
  const chartData = [
    { name: 'Fail Findings', value: openFindings, color: '#ff1d5c' },
    { name: 'Pass Findings', value: resolvedFindings, color: '#00d47c' },
  ]

  return (
    <section className="page-card min-h-[372px] min-w-0 flex-1">
      <div className="page-card-header">
        <h3 className="page-card-title">Check Findings</h3>
      </div>

      <div className="relative mx-auto h-[206px] w-[206px] shrink-0">
        <ResponsiveContainer width="100%" height="100%">
          <PieChart>
            <Pie
              data={chartData}
              dataKey="value"
              nameKey="name"
              innerRadius={74}
              outerRadius={98}
              paddingAngle={0}
              stroke="none"
            >
              {chartData.map((entry) => (
                <Cell key={entry.name} fill={entry.color} />
              ))}
            </Pie>
          </PieChart>
        </ResponsiveContainer>

        <div className="pointer-events-none absolute inset-0 flex items-center justify-center">
          <div className="flex h-[128px] w-[128px] flex-col items-center justify-center rounded-full border border-white/6 bg-[#120f10]">
            <p className="text-[2rem] font-semibold text-white">{totalFindings}</p>
            <p className="mt-0.5 text-[15px] text-zinc-300">Total Findings</p>
          </div>
        </div>
      </div>

      <div className="surface-card-inner mt-auto flex flex-col gap-4 px-4 py-3 lg:flex-row lg:justify-between">
        <div className="flex min-w-0 items-start gap-3">
          <div className="mt-1 flex h-8 w-8 shrink-0 items-center justify-center rounded-full bg-rose-500/12 text-rose-300">
            <CircleAlert size={18} />
          </div>
          <div className="min-w-0">
            <p className="text-sm font-medium text-zinc-100">Fail Findings</p>
            <p className="mt-1 text-xs leading-5 text-zinc-400">{openFindings} New</p>
          </div>
        </div>

        <div className="hidden w-px bg-white/5 lg:block" />

        <div className="flex min-w-0 items-start gap-3">
          <div className="mt-1 flex h-8 w-8 shrink-0 items-center justify-center rounded-full bg-emerald-400/12 text-emerald-200">
            <ShieldCheck size={18} />
          </div>
          <div className="min-w-0">
            <p className="text-sm font-medium text-zinc-100">Pass Findings</p>
            <p className="mt-1 text-xs leading-5 text-zinc-400">{resolvedFindings} New</p>
          </div>
        </div>
      </div>
    </section>
  )
}
