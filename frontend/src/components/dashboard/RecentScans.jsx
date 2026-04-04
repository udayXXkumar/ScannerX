import { ArrowUpRight, Clock3 } from 'lucide-react'
import { useNavigate } from 'react-router-dom'
import { getScanDisplayName, getScanTierLabel, normalizeScanStatus } from '../../lib/scanUtils'
import StatusBadge from '../ui/StatusBadge'

export default function RecentScans({ scans = [] }) {
  const navigate = useNavigate()

  return (
    <section className="page-card min-h-[460px]">
      <div className="page-card-header">
        <h3 className="page-card-title">Recent Scans</h3>

        <button
          onClick={() => navigate('/scans')}
          className="inline-flex items-center gap-2 text-sm font-medium text-zinc-300 transition-colors hover:text-white"
        >
          View Scans
          <ArrowUpRight size={16} />
        </button>
      </div>

      {scans.length === 0 ? (
        <div className="surface-card-inner flex flex-1 items-center justify-center">
          <div className="text-center">
            <Clock3 className="mx-auto mb-3 text-zinc-600" size={36} />
            <p className="text-sm text-zinc-400">No scans have been recorded yet.</p>
          </div>
        </div>
      ) : (
        <div className="surface-card-inner flex flex-1 flex-col overflow-hidden">
          {scans.map((scan, index) => (
            <button
              key={scan.id}
              type="button"
              onClick={() => navigate(`/scans/${scan.id}`)}
              className={`flex items-center justify-between gap-4 px-4 py-3 text-left transition-colors hover:bg-white/[0.03] ${
                index !== scans.length - 1 ? 'border-b border-white/5' : ''
              }`}
            >
              <div className="min-w-0 flex-1">
                <p className="truncate text-sm font-medium text-zinc-100">
                  {getScanDisplayName(scan)}
                </p>
                <p className="mt-1 truncate text-xs text-zinc-500">
                  {getScanTierLabel(scan.tier || scan.profileType)} · {scan.displayTime}
                </p>
                <div className="mt-3 h-2 rounded-full bg-white/[0.04]">
                  <div
                    className={`h-2 rounded-full transition-all duration-500 ${
                      normalizeScanStatus(scan.status) === 'FAILED' || normalizeScanStatus(scan.status) === 'CANCELLED'
                        ? 'bg-rose-500'
                        : 'bg-emerald-300'
                    }`}
                    style={{ width: `${Math.max(4, Math.min(scan.progress ?? 0, 100))}%` }}
                  />
                </div>
              </div>

              <div className="flex shrink-0 flex-col items-end gap-2">
                <StatusBadge status={scan.status} />
                <span className="text-xs text-zinc-500">{scan.progress ?? 0}%</span>
              </div>
            </button>
          ))}
        </div>
      )}
    </section>
  )
}
