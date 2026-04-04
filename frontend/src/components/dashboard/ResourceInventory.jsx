import { CircleCheck, ShieldAlert, Target } from 'lucide-react'
import { useNavigate } from 'react-router-dom'

export default function ResourceInventory({ items = [] }) {
  const navigate = useNavigate()
  const visibleItems = items.slice(0, 8)

  return (
    <section className="page-card">
      <div className="page-card-header">
        <h3 className="page-card-title">Resource Inventory</h3>

        <button
          onClick={() => navigate('/targets')}
          className="text-sm font-medium text-zinc-300 transition-colors hover:text-white"
        >
          View All Resources
        </button>
      </div>

      {visibleItems.length === 0 ? (
        <div className="surface-card-inner flex min-h-[180px] items-center justify-center px-6 py-8">
          <p className="text-sm text-zinc-400">No target inventory is available yet.</p>
        </div>
      ) : (
        <div className="grid grid-cols-1 gap-3 sm:grid-cols-2 lg:grid-cols-4">
          {visibleItems.map((item) => {
            const ready =
              item.verificationStatus === 'ACTIVE' ||
              item.verificationStatus === 'VERIFIED' ||
              !item.verificationStatus

            return (
              <button
                key={item.id}
                type="button"
                onClick={() => navigate('/targets')}
                className="flex min-h-[126px] flex-col justify-between rounded-[12px] border border-[rgba(67,34,50,0.7)] bg-[rgba(67,34,50,0.3)] px-4 py-3 text-left transition-all hover:border-rose-500/40"
              >
                <div className="w-full">
                  <div className="mb-3 flex items-start justify-between gap-3">
                    <div className="flex min-w-0 flex-1 items-center gap-2">
                      <Target size={16} className="shrink-0 text-zinc-200" />
                      <p className="truncate text-base font-semibold text-zinc-100">{item.name}</p>
                    </div>
                    <span className="text-[10px] text-zinc-400">{item.scanCount} scans</span>
                  </div>

                  <p className="truncate text-xs text-zinc-400">{item.url}</p>
                </div>

                <div className="mt-3 flex w-full items-end justify-between gap-3">
                  <div className="flex min-w-0 flex-col gap-1">
                    <div className="flex items-center gap-1">
                      <span className="inline-flex items-center gap-1 rounded-full bg-rose-500/12 px-2 py-0.5 text-sm font-semibold text-rose-300">
                        <ShieldAlert size={12} />
                        {item.openFindings}
                      </span>
                      <span className="text-sm font-semibold text-zinc-200">Open Findings</span>
                    </div>
                    <div className="flex items-center gap-1 pl-3">
                      <div className="h-5 w-px rounded-full bg-rose-400/50" />
                      <span className="text-xs text-zinc-400">{item.totalFindings} total findings</span>
                    </div>
                  </div>

                  <span
                    className={`inline-flex items-center gap-1 rounded-full px-2.5 py-1 text-[11px] font-semibold uppercase tracking-[0.16em] ${
                      ready
                        ? 'bg-emerald-400/12 text-emerald-200'
                        : 'bg-amber-400/12 text-amber-200'
                    }`}
                  >
                    {ready ? <CircleCheck size={12} /> : <ShieldAlert size={12} />}
                    {ready ? 'Ready' : 'Pending'}
                  </span>
                </div>
              </button>
            )
          })}
        </div>
      )}
    </section>
  )
}
