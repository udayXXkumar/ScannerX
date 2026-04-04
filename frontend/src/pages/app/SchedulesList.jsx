import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query'
import { getSchedules, cancelSchedule } from '../../api/scheduleApi'
import { Clock, Pause, CheckCircle2 } from 'lucide-react'
import StatusBadge from '../../components/ui/StatusBadge'
import { getScanTierLabel } from '../../lib/scanUtils'

const SchedulesList = () => {
  const queryClient = useQueryClient()

  const { data: schedules = [], isLoading, isError, error } = useQuery({
    queryKey: ['schedules'],
    queryFn: getSchedules
  })

  const cancelMutation = useMutation({
    mutationFn: cancelSchedule,
    onSuccess: () => queryClient.invalidateQueries({ queryKey: ['schedules'] })
  })

  const activeSchedules = schedules.filter(s => s.status === 'ACTIVE').length
  const inactiveSchedules = schedules.filter(s => s.status !== 'ACTIVE').length

  if (isLoading) {
    return <div className="p-8 text-slate-400">Loading schedules...</div>
  }

  if (isError) {
    return (
      <div className="page-shell">
        <div className="table-shell">
          <div className="empty-state">
            <div className="empty-state-panel">
              <div className="empty-state-icon">
                <Pause size={34} />
              </div>
              <p className="text-slate-300">Unable to load schedules right now.</p>
              <p className="mt-2 text-sm text-slate-500">{getErrorMessage(error, 'Please refresh and try again.')}</p>
            </div>
          </div>
        </div>
      </div>
    )
  }

  return (
    <div className="page-shell">
      <div className="space-y-4">
        <div className="page-header">
          <div className="page-header-copy">
            <h2 className="flex items-center gap-3 text-3xl font-bold text-white">
              <div className="flex h-10 w-10 items-center justify-center rounded-xl border border-white/10 bg-white/[0.04] backdrop-blur-xl">
                <Clock className="w-6 h-6 text-prowler-green" />
              </div>
              Scheduled Scans
            </h2>
            <p className="page-subtitle">Manage recurring scan schedules</p>
          </div>
        </div>

        <div className="grid gap-4 sm:grid-cols-2">
          <StatCard label="Active Schedules" value={activeSchedules} tone="success" icon={CheckCircle2} />
          <StatCard label="Inactive Schedules" value={inactiveSchedules} tone="neutral" icon={Pause} />
        </div>
      </div>

      <div className="table-shell">
        {schedules.length === 0 ? (
          <div className="empty-state">
            <div className="empty-state-panel">
              <div className="empty-state-icon">
                <Clock size={34} />
              </div>
              <p className="text-slate-400 text-lg">No active schedules.</p>
              <p className="text-slate-500 text-sm mt-2">Create one from the Targets page to set up recurring scans.</p>
            </div>
          </div>
        ) : (
          <>
            <div className="table-scroll">
              <table className="table-base min-w-[940px]">
                <thead className="table-head">
                  <tr className="table-head-row">
                    <th className="py-4 px-6 font-semibold">Target</th>
                    <th className="py-4 px-6 font-semibold">Tier</th>
                    <th className="py-4 px-6 font-semibold">Schedule (Cron)</th>
                    <th className="py-4 px-6 font-semibold">Status</th>
                    <th className="py-4 px-6 font-semibold">Last Run</th>
                    <th className="py-4 px-6 font-semibold text-right">Actions</th>
                  </tr>
                </thead>
                <tbody className="divide-y divide-white/8">
                  {schedules.map(schedule => (
                    <tr key={schedule.id} className="transition-colors hover:bg-white/[0.03]">
                      <td className="py-4 px-6">
                        <div>
                          <p className="font-medium text-white group-hover:text-prowler-green transition-colors">
                            {schedule.target?.name || `Target #${schedule.target?.id || 'Unknown'}`}
                          </p>
                          <p className="text-sm text-slate-500 mt-1">{schedule.target?.baseUrl || 'N/A'}</p>
                        </div>
                      </td>
                      <td className="py-4 px-6">
                        <span className="rounded-lg border border-prowler-green/30 bg-prowler-green/10 px-3 py-1 text-xs font-semibold uppercase tracking-wider text-prowler-green">
                          {getScanTierLabel(schedule.target?.defaultTier || schedule.scanProfile)}
                        </span>
                      </td>
                      <td className="py-4 px-6">
                        <code className="rounded-lg border border-white/8 bg-white/[0.04] px-3 py-2 text-xs font-mono text-zinc-200 select-all">
                          {schedule.cronExpression}
                        </code>
                      </td>
                      <td className="py-4 px-6">
                        <StatusBadge status={schedule.status} />
                      </td>
                      <td className="py-4 px-6">
                        <span className="text-sm text-slate-500">
                          {schedule.lastRunAt ? new Date(schedule.lastRunAt).toLocaleString() : 'Never'}
                        </span>
                      </td>
                      <td className="py-4 px-6 text-right">
                        {schedule.status === 'ACTIVE' && (
                          <button
                            onClick={() => cancelMutation.mutate(schedule.id)}
                            className="inline-flex items-center gap-2 rounded-lg border border-system-error/30 bg-system-error/10 px-4 py-2 text-sm font-medium text-system-error transition-colors hover:bg-system-error/20"
                            title="Cancel Schedule"
                            disabled={cancelMutation.isPending}
                          >
                            <Pause size={16} />
                            {cancelMutation.isPending ? 'Canceling...' : 'Cancel'}
                          </button>
                        )}
                      </td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
          </>
        )}
      </div>
    </div>
  )
}

const StatCard = ({ label, value, icon }) => {
  const Icon = icon

  return (
    <div className="metric-card">
      <div className="metric-card-body">
        <div>
          <p className="metric-card-label">{label}</p>
          <p className="metric-card-value">{value}</p>
        </div>
        <div className={`metric-icon-shell ${
          label === 'Active Schedules'
            ? 'border-emerald-400/16 bg-[linear-gradient(135deg,rgba(27,37,31,0.88),rgba(14,19,18,0.9))] text-emerald-200'
            : 'border-white/10 bg-[linear-gradient(135deg,rgba(28,28,29,0.88),rgba(15,15,16,0.9))] text-zinc-200'
        }`}>
          <Icon size={24} />
        </div>
      </div>
    </div>
  )
}

export default SchedulesList

function getErrorMessage(error, fallbackMessage) {
  const responseData = error?.response?.data

  if (typeof responseData === 'string' && responseData.trim()) {
    return responseData
  }

  if (typeof responseData?.message === 'string' && responseData.message.trim()) {
    return responseData.message
  }

  if (typeof error?.message === 'string' && error.message.trim()) {
    return error.message
  }

  return fallbackMessage
}
