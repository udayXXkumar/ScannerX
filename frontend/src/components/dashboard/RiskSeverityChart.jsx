export default function RiskSeverityChart({ counts = {} }) {
  const rows = [
    { name: 'Critical', value: counts.critical ?? 0, color: '#ff1d5c' },
    { name: 'High', value: counts.high ?? 0, color: '#ff7b4f' },
    { name: 'Medium', value: counts.medium ?? 0, color: '#f8c65a' },
    { name: 'Low', value: counts.low ?? 0, color: '#f6efbf' },
    { name: 'Info', value: counts.info ?? 0, color: '#6aaeea' },
  ]

  const maxValue = Math.max(...rows.map((row) => row.value), 1)
  const total = rows.reduce((sum, row) => sum + row.value, 0)

  return (
    <section className="page-card min-h-[388px]">
      <div className="page-card-header mb-8">
        <h3 className="page-card-title">Risk Severity</h3>
      </div>

      <div className="flex flex-1 flex-col justify-center gap-7">
        {rows.map((row) => {
          const width = `${(row.value / maxValue) * 100}%`
          const percentage = total ? Math.round((row.value / total) * 100) : 0

          return (
            <div key={row.name} className="grid grid-cols-[86px_minmax(0,1fr)_92px] items-center gap-5">
              <div className="text-[15px] text-zinc-200">{row.name}</div>

              <div className="h-[22px] rounded-[5px] bg-[#161314]">
                <div
                  className="h-[22px] rounded-[5px] transition-all duration-500"
                  style={{ width, backgroundColor: row.color }}
                />
              </div>

              <div className="text-right text-[15px] text-zinc-300">
                {percentage}% <span className="mx-1 text-zinc-500">•</span> {row.value}
              </div>
            </div>
          )
        })}
      </div>
    </section>
  )
}
