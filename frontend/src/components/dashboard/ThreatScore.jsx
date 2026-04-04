import { PolarAngleAxis, RadialBar, RadialBarChart, ResponsiveContainer } from 'recharts'

const SCORE_LEVELS = [
  { max: 30, label: 'Critical Risk', color: '#ff006a' },
  { max: 60, label: 'Moderate Risk', color: '#fec94d' },
  { max: 100, label: 'Secure', color: '#9fd655' },
]

function getThreatLevel(score) {
  return SCORE_LEVELS.find((item) => score <= item.max) ?? SCORE_LEVELS[1]
}

export default function ThreatScore({
  score = 0,
}) {
  const safeScore = Math.max(0, Math.min(100, score))
  const data = [{ name: 'ThreatScore', value: safeScore, fill: getThreatLevel(safeScore).color }]

  return (
    <section className="page-card min-h-[372px] w-full lg:max-w-[312px]">
      <div className="page-card-header">
        <h3 className="page-card-title">ScannerX ThreatScore</h3>
      </div>

      <div className="flex flex-1 items-center justify-center">
        <div className="relative mx-auto h-[172px] w-full max-w-[250px]">
          <ResponsiveContainer width="100%" height="100%">
            <RadialBarChart
              data={data}
              innerRadius="72%"
              outerRadius="100%"
              startAngle={210}
              endAngle={-30}
              barSize={14}
            >
              <PolarAngleAxis type="number" domain={[0, 100]} tick={false} />
              <RadialBar dataKey="value" background={{ fill: '#131112' }} cornerRadius={999} />
            </RadialBarChart>
          </ResponsiveContainer>

          <div className="pointer-events-none absolute inset-0 flex flex-col items-center justify-center">
            <p className="text-[2.05rem] font-semibold text-white">{safeScore}%</p>
          </div>
        </div>
      </div>
    </section>
  )
}
