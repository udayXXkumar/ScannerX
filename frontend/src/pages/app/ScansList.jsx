import { useMemo, useState } from 'react'
import { CheckCircle2, Clock, Play, Plus, Search, Trash2, XCircle } from 'lucide-react'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { useNavigate } from 'react-router-dom'
import { deleteScan, getScans, resumeScan } from '../../api/scanApi'
import StatusBadge from '../../components/ui/StatusBadge'
import {
  getScanDisplayName,
  isActiveScanStatus,
  isCompletedScan,
  getScanTierLabel,
  normalizeScanStatus,
} from '../../lib/scanUtils'

const ScansList = () => {
  const navigate = useNavigate()
  const queryClient = useQueryClient()
  const [searchQuery, setSearchQuery] = useState('')

  const { data: scans = [], isLoading, isError, error } = useQuery({
    queryKey: ['scans'],
    queryFn: getScans,
    refetchInterval: (query) =>
      (query.state.data ?? []).some((scan) => isActiveScanStatus(scan.status)) ? 3000 : false,
  })

  const deleteMutation = useMutation({
    mutationFn: deleteScan,
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['scans'] })
      queryClient.invalidateQueries({ queryKey: ['findings'] })
      queryClient.invalidateQueries({ queryKey: ['dashboardSummary'] })
      queryClient.invalidateQueries({ queryKey: ['notifications'] })
      queryClient.invalidateQueries({ queryKey: ['notifications', 'unreadCount'] })
      queryClient.invalidateQueries({ queryKey: ['reportSummary'] })
    },
    onError: (error) => {
      window.alert(getErrorMessage(error, 'Unable to delete this scan right now.'))
    },
  })

  const resumeMutation = useMutation({
    mutationFn: resumeScan,
    onSuccess: (scan) => {
      queryClient.invalidateQueries({ queryKey: ['scans'] })
      queryClient.invalidateQueries({ queryKey: ['targets'] })
      queryClient.invalidateQueries({ queryKey: ['dashboardSummary'] })
      queryClient.invalidateQueries({ queryKey: ['reportSummary'] })
      navigate(`/scans/${scan.id}`)
    },
    onError: (error) => {
      window.alert(getErrorMessage(error, 'Unable to resume this scan right now.'))
    },
  })

  const filteredScans = useMemo(() => {
    const query = searchQuery.trim().toLowerCase()
    if (!query) {
      return scans
    }

    return scans.filter((scan) =>
      [
        scan.target?.name,
        scan.target?.baseUrl,
        scan.tier,
        scan.profileType,
        scan.status,
      ].some((value) => String(value || '').toLowerCase().includes(query)),
    )
  }, [scans, searchQuery])

  const completedScans = scans.filter((scan) => isCompletedScan(scan.status)).length
  const inProgressScans = scans.filter((scan) => isActiveScanStatus(scan.status)).length
  const hasActiveScan = inProgressScans > 0
  const failedScans = scans.filter((scan) => {
    const status = normalizeScanStatus(scan.status)
    return status === 'FAILED' || status === 'CANCELLED'
  }).length

  const handleDelete = (event, scan) => {
    event.stopPropagation()
    const confirmed = window.confirm(`Delete "${getScanDisplayName(scan)}" and all of its findings?`)
    if (!confirmed) {
      return
    }
    deleteMutation.mutate(scan.id)
  }

  return (
    <div className="page-shell">
      <div className="space-y-4">
        <div className="page-header">
          <div className="page-header-copy">
            <h2 className="page-title">Scans</h2>
            <p className="page-subtitle">Monitor completed jobs, track progress, and remove old scan runs.</p>
          </div>
          <button
            onClick={() => navigate('/targets')}
            className="surface-button-primary h-12 px-6 shadow-[0_14px_36px_rgba(96,223,178,0.18)]"
          >
            <Plus size={20} />
            New Scan
          </button>
        </div>

        <div className="page-stats-grid">
          <StatCard label="Completed" value={completedScans} tone="success" icon={CheckCircle2} />
          <StatCard label="In Progress" value={inProgressScans} tone="neutral" icon={Clock} />
          <StatCard label="Failed" value={failedScans} tone="error" icon={XCircle} />
        </div>
      </div>

      <div className="table-shell">
        <div className="table-toolbar">
          <div className="search-control w-full max-w-sm">
            <Search className="mr-2 h-4 w-4 text-slate-400" />
            <input
              type="text"
              value={searchQuery}
              onChange={(event) => setSearchQuery(event.target.value)}
              placeholder="Search targets, URLs, or status..."
              className="w-full border-none bg-transparent text-sm text-slate-200 outline-none placeholder:text-slate-500"
            />
          </div>
        </div>

        <div className="table-scroll">
          {isLoading ? (
            <div className="flex h-full items-center justify-center text-slate-400">Loading scans...</div>
          ) : isError ? (
            <div className="empty-state">
              <div className="empty-state-panel">
                <div className="empty-state-icon">
                  <XCircle size={34} />
                </div>
                <p className="text-slate-300">Unable to load scans right now.</p>
                <p className="mt-2 text-sm text-slate-500">{getErrorMessage(error, 'Please refresh and try again.')}</p>
              </div>
            </div>
          ) : filteredScans.length === 0 ? (
            <div className="empty-state">
              <div className="empty-state-panel">
                <div className="empty-state-icon">
                  <Clock size={34} />
                </div>
                <p className="text-slate-400">
                  {searchQuery ? 'No scans match your search.' : 'No scans yet. Start one from the Targets page.'}
                </p>
              </div>
            </div>
          ) : (
            <table className="table-base min-w-[860px]">
              <thead className="table-head">
                <tr className="table-head-row">
                  <th className="px-6 py-4 font-semibold">Target</th>
                  <th className="px-6 py-4 font-semibold">Base URL</th>
                  <th className="px-6 py-4 font-semibold">Tier</th>
                  <th className="px-6 py-4 font-semibold">Status</th>
                  <th className="px-6 py-4 font-semibold">Progress</th>
                  <th className="px-6 py-4 font-semibold">Started</th>
                  <th className="px-6 py-4 font-semibold text-right">Actions</th>
                </tr>
              </thead>
              <tbody className="divide-y divide-white/8">
                {filteredScans.map((scan) => {
                  const status = normalizeScanStatus(scan.status)
                  const progress = Math.min(scan.progress || 0, 100)
                  const isFailed = status === 'FAILED' || status === 'CANCELLED'
                  const isDeleting = deleteMutation.isPending && deleteMutation.variables === scan.id
                  const isPaused = status === 'PAUSED'
                  const isResuming = resumeMutation.isPending && resumeMutation.variables === scan.id

                  return (
                    <tr
                      key={scan.id}
                      onClick={() => navigate(`/scans/${scan.id}`)}
                      className="group cursor-pointer transition-colors hover:bg-white/[0.03]"
                    >
                      <td className="px-6 py-4">
                        <div className="space-y-1">
                          <p className="font-medium text-white transition-colors group-hover:text-prowler-green">
                            {getScanDisplayName(scan)}
                          </p>
                          <p className="font-mono text-xs text-slate-500">#{scan.id}</p>
                        </div>
                      </td>
                      <td className="px-6 py-4">
                        <p className="max-w-xs truncate text-sm text-slate-500">{scan.target?.baseUrl || 'No target URL'}</p>
                      </td>
                      <td className="px-6 py-4 text-sm">
                        <span className="rounded-lg border border-white/8 bg-white/[0.04] px-3 py-1 text-xs font-medium text-slate-300">
                          {getScanTierLabel(scan.tier || scan.profileType)}
                        </span>
                      </td>
                      <td className="px-6 py-4">
                        <StatusBadge status={scan.status} />
                      </td>
                      <td className="px-6 py-4">
                        <div className="flex w-52 items-center gap-3">
                          <div className="h-2 w-full overflow-hidden rounded-full border border-white/8 bg-white/[0.04]">
                            <div
                              className={`h-full transition-all ${isFailed ? 'bg-rose-500' : 'bg-gradient-to-r from-prowler-green to-prowler-green/80'}`}
                              style={{ width: `${progress}%` }}
                            />
                          </div>
                          <span className="w-10 text-right text-xs font-semibold text-slate-400">{progress}%</span>
                        </div>
                      </td>
                      <td className="px-6 py-4 text-sm text-slate-500">
                        {scan.createdAt ? new Date(scan.createdAt).toLocaleString() : 'N/A'}
                      </td>
                      <td className="px-6 py-4">
                        <div className="flex justify-end gap-2">
                          {isPaused ? (
                            <button
                              type="button"
                              onClick={(event) => {
                                event.stopPropagation()
                                resumeMutation.mutate(scan.id)
                              }}
                              disabled={isResuming || hasActiveScan}
                              className="inline-flex items-center gap-2 rounded-xl border border-white/10 px-3 py-2 text-sm text-slate-300 transition-colors hover:border-prowler-green/30 hover:bg-prowler-green/10 hover:text-prowler-green disabled:cursor-not-allowed disabled:opacity-60"
                              title={hasActiveScan ? 'Another scan is already running.' : 'Resume scan'}
                            >
                              <Play size={15} className="fill-current" />
                              {isResuming ? 'Resuming...' : 'Resume'}
                            </button>
                          ) : null}
                          <button
                            type="button"
                            onClick={(event) => handleDelete(event, scan)}
                            disabled={isDeleting}
                            className="inline-flex items-center gap-2 rounded-xl border border-white/10 px-3 py-2 text-sm text-slate-400 transition-colors hover:border-rose-500/30 hover:bg-rose-500/10 hover:text-rose-200 disabled:cursor-not-allowed disabled:opacity-60"
                            title="Delete scan"
                          >
                            <Trash2 size={15} />
                            {isDeleting ? 'Deleting...' : 'Delete'}
                          </button>
                        </div>
                      </td>
                    </tr>
                  )
                })}
              </tbody>
            </table>
          )}
        </div>
      </div>
    </div>
  )
}

const StatCard = ({ label, value, tone, icon }) => {
  const Icon = icon
  const toneClasses =
    tone === 'error'
      ? 'border-rose-400/18 bg-[linear-gradient(135deg,rgba(45,22,28,0.88),rgba(17,12,13,0.9))] text-rose-200'
      : tone === 'neutral'
        ? 'border-white/10 bg-[linear-gradient(135deg,rgba(28,28,29,0.88),rgba(15,15,16,0.9))] text-zinc-200'
        : 'border-emerald-400/16 bg-[linear-gradient(135deg,rgba(27,37,31,0.88),rgba(14,19,18,0.9))] text-emerald-200'

  return (
    <div className="metric-card">
      <div className="metric-card-body">
        <div>
          <p className="metric-card-label">{label}</p>
          <p className="metric-card-value">{value}</p>
        </div>
        <div className={`metric-icon-shell ${toneClasses}`}>
          <Icon size={24} />
        </div>
      </div>
    </div>
  )
}

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

export default ScansList
