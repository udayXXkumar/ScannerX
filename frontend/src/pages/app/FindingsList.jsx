import { useMemo, useState } from 'react'
import { createPortal } from 'react-dom'
import { AlertTriangle, Search, X } from 'lucide-react'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { useSearchParams } from 'react-router-dom'
import { getFindings, updateFinding } from '../../api/findingApi'
import { getScans } from '../../api/scanApi'
import { getTargets } from '../../api/targetApi'
import { useScanWebSocket } from '../../hooks/useScanWebSocket'
import SeverityBadge from '../../components/ui/SeverityBadge'
import StatusBadge from '../../components/ui/StatusBadge'
import {
  normalizeFindingSeverity,
  sanitizeFindingDescription,
  sanitizeFindingTitle,
} from '../../lib/findingUtils'
import { isActiveScanStatus } from '../../lib/scanUtils'

const ALL_TARGETS = 'all-targets'

const FindingsList = () => {
  const queryClient = useQueryClient()
  const [searchParams, setSearchParams] = useSearchParams()
  const [selectedFinding, setSelectedFinding] = useState(null)
  const [isModalOpen, setIsModalOpen] = useState(false)
  const [updateForm, setUpdateForm] = useState({ status: '', assignedUser: '', comments: '' })
  const [filterSeverity, setFilterSeverity] = useState('All')
  const [filterStatus, setFilterStatus] = useState('All')
  const [searchQuery, setSearchQuery] = useState('')
  const selectedTargetId = searchParams.get('targetId') || ALL_TARGETS

  const { data: targets = [] } = useQuery({
    queryKey: ['targets'],
    queryFn: getTargets,
  })

  const { data: scans = [] } = useQuery({
    queryKey: ['scans'],
    queryFn: getScans,
    refetchInterval: (query) =>
      (query.state.data ?? []).some((scan) => isActiveScanStatus(scan.status)) ? 3000 : false,
  })
  const activeScan = useMemo(
    () => scans.find((scan) => isActiveScanStatus(scan.status)),
    [scans],
  )
  const { events } = useScanWebSocket(activeScan?.id)

  const targetId = selectedTargetId === ALL_TARGETS ? undefined : Number(selectedTargetId)

  const { data: findings = [], isLoading } = useQuery({
    queryKey: ['findings', 'list', targetId ?? 'all-targets', 'live-workspace'],
    queryFn: () => getFindings({ targetId, completedOnly: false }),
  })

  const mergedFindings = useMemo(() => {
    const liveFindings = events
      .filter((event) => event.type === 'FINDING_FOUND' && event.data)
      .map((event) => event.data)
      .filter((finding) => {
        if (!targetId) {
          return true
        }

        return String(finding.target?.id) === String(targetId)
      })

    const findingsByKey = new Map()
    ;[...findings, ...liveFindings].forEach((finding, index) => {
      if (!finding) {
        return
      }

      const normalizedFinding = {
        ...finding,
        severity: normalizeFindingSeverity(finding.severity),
        title: sanitizeFindingTitle(finding.title),
        description: sanitizeFindingDescription(finding.description),
      }
      const key =
        finding.id ??
        `${normalizedFinding.title || 'finding'}-${finding.affectedUrl || 'url'}-${finding.createdAt || index}`

      findingsByKey.set(key, normalizedFinding)
    })

    return Array.from(findingsByKey.values())
  }, [events, findings, targetId])

  const updateMutation = useMutation({
    mutationFn: ({ id, data }) => updateFinding(id, data),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['findings'] })
      queryClient.invalidateQueries({ queryKey: ['dashboardSummary'] })
      setIsModalOpen(false)
      setSelectedFinding(null)
    },
  })

  const handleRowClick = (finding) => {
    setSelectedFinding(finding)
    setUpdateForm({
      status: normalizeFindingStatus(finding.status),
      assignedUser: finding.assignedUser || '',
      comments: finding.comments || '',
    })
    setIsModalOpen(true)
  }

  const handleUpdateSubmit = (event) => {
    event.preventDefault()
    updateMutation.mutate({ id: selectedFinding.id, data: updateForm })
  }

  const filteredFindings = mergedFindings.filter((finding) => {
    const normalizedSeverity = normalizeFindingSeverity(finding.severity)
    const normalizedStatus = normalizeFindingStatus(finding.status)
    const matchesSeverity = filterSeverity === 'All' || normalizedSeverity === filterSeverity
    const matchesStatus = filterStatus === 'All' || normalizedStatus === filterStatus
    const query = searchQuery.trim().toLowerCase()
    const matchesSearch =
      !query ||
      [
        finding.title,
        finding.description,
        finding.affectedUrl,
        finding.target?.name,
      ]
        .some((value) => String(value || '').toLowerCase().includes(query))

    return matchesSeverity && matchesStatus && matchesSearch
  })

  const severityStats = {
    CRITICAL: filteredFindings.filter((finding) => String(finding.severity || '').toUpperCase() === 'CRITICAL').length,
    HIGH: filteredFindings.filter((finding) => String(finding.severity || '').toUpperCase() === 'HIGH').length,
    MEDIUM: filteredFindings.filter((finding) => String(finding.severity || '').toUpperCase() === 'MEDIUM').length,
    LOW: filteredFindings.filter((finding) => String(finding.severity || '').toUpperCase() === 'LOW').length,
  }

  return (
    <div className="page-shell">
      <div className="space-y-4">
        <div className="page-header">
          <div className="page-header-copy">
            <h2 className="page-title">Findings Portfolio</h2>
            <p className="page-subtitle">Review historical findings and watch new results arrive live for the active target.</p>
          </div>
          <div className="text-right">
            <p className="text-4xl font-bold text-white">{filteredFindings.length}</p>
            <p className="text-sm text-slate-400">Visible Findings</p>
          </div>
        </div>

        <div className="grid gap-4 sm:grid-cols-2 xl:grid-cols-4">
          <StatCard label="Critical" value={severityStats.CRITICAL} color="critical" />
          <StatCard label="High" value={severityStats.HIGH} color="high" />
          <StatCard label="Medium" value={severityStats.MEDIUM} color="medium" />
          <StatCard label="Low" value={severityStats.LOW} color="low" />
        </div>
      </div>

      <div className="table-shell min-h-[560px]">
        <div className="table-toolbar shrink-0">
          <div className="search-control w-full max-w-xs">
            <Search className="mr-2 h-4 w-4 text-slate-400" />
            <input
              type="text"
              placeholder="Search findings..."
              value={searchQuery}
              onChange={(event) => setSearchQuery(event.target.value)}
              className="w-full border-none bg-transparent text-sm text-slate-200 outline-none placeholder:text-slate-500"
            />
          </div>

          <select
            value={selectedTargetId}
            onChange={(event) => {
              const nextTargetId = event.target.value
              const params = new URLSearchParams(searchParams)
              if (nextTargetId === ALL_TARGETS) {
                params.delete('targetId')
              } else {
                params.set('targetId', nextTargetId)
              }
              setSearchParams(params, { replace: true })
            }}
            className="filter-control"
          >
            <option value={ALL_TARGETS}>All Targets</option>
            {targets.map((target) => (
              <option key={target.id} value={String(target.id)}>
                {target.name}
              </option>
            ))}
          </select>

          <select
            value={filterSeverity}
            onChange={(event) => setFilterSeverity(event.target.value)}
            className="filter-control"
          >
            <option value="All">All Severities</option>
            <option value="CRITICAL">Critical Only</option>
            <option value="HIGH">High Only</option>
            <option value="MEDIUM">Medium Only</option>
            <option value="LOW">Low Only</option>
          </select>

          <select
            value={filterStatus}
            onChange={(event) => setFilterStatus(event.target.value)}
            className="filter-control"
          >
            <option value="All">All Status</option>
            <option value="OPEN">Open</option>
            <option value="IN PROGRESS">In Progress</option>
            <option value="RESOLVED">Resolved</option>
            <option value="FALSE POSITIVE">False Positive</option>
          </select>
        </div>

        <div className="table-scroll">
          {isLoading ? (
            <div className="flex h-full items-center justify-center text-slate-400">Loading findings...</div>
          ) : filteredFindings.length === 0 ? (
            <div className="empty-state">
              <div className="empty-state-panel">
                <div className="empty-state-icon">
                  <AlertTriangle size={34} />
                </div>
                <p className="text-slate-400">No findings match your criteria</p>
              </div>
            </div>
          ) : (
            <table className="table-base table-fixed">
              <thead className="table-head">
                <tr className="table-head-row">
                  <th className="w-[34%] px-6 py-4 font-semibold">Title</th>
                  <th className="w-[12%] px-6 py-4 font-semibold">Severity</th>
                  <th className="w-[12%] px-6 py-4 font-semibold">Status</th>
                  <th className="w-[20%] px-6 py-4 font-semibold">Target</th>
                  <th className="w-[8%] px-6 py-4 font-semibold">CWE</th>
                  <th className="w-[14%] px-6 py-4 font-semibold">Discovered</th>
                </tr>
              </thead>
              <tbody className="divide-y divide-white/8">
                {filteredFindings.map((finding) => (
                  <tr
                    key={finding.id}
                    onClick={() => handleRowClick(finding)}
                    className="group cursor-pointer transition-colors hover:bg-white/[0.03]"
                    >
                    <td className="px-6 py-4">
                      <div className="max-w-full break-words text-pretty font-medium leading-6 text-white transition-colors group-hover:text-prowler-green line-clamp-2">
                        {sanitizeFindingTitle(finding.title)}
                      </div>
                    </td>
                    <td className="px-6 py-4">
                      <SeverityBadge severity={finding.severity} />
                    </td>
                    <td className="px-6 py-4">
                      <StatusBadge status={finding.status} />
                    </td>
                    <td className="px-6 py-4 text-sm text-slate-400">
                      <div className="table-cell-wrap">{finding.target?.name || finding.affectedUrl || 'N/A'}</div>
                    </td>
                    <td className="px-6 py-4 font-mono text-xs text-slate-500">
                      {finding.cweId || 'N/A'}
                    </td>
                    <td className="px-6 py-4 text-sm text-slate-500">
                      {finding.createdAt ? new Date(finding.createdAt).toLocaleDateString() : 'N/A'}
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          )}
        </div>
      </div>

      {isModalOpen && selectedFinding ? (
        <FindingModal
          finding={selectedFinding}
          updateForm={updateForm}
          setUpdateForm={setUpdateForm}
          onSubmit={handleUpdateSubmit}
          onClose={() => setIsModalOpen(false)}
          isLoading={updateMutation.isPending}
        />
      ) : null}
    </div>
  )
}

const StatCard = ({ label, value, color }) => {
  const textClass =
    color === 'critical'
      ? 'text-severity-critical'
      : color === 'high'
        ? 'text-severity-high'
        : color === 'medium'
          ? 'text-severity-medium'
          : 'text-severity-low'

  return (
    <div className="surface-card px-5 py-4">
      <p className="text-sm font-medium text-slate-400">{label}</p>
      <p className={`mt-2 text-2xl font-bold ${textClass}`}>{value}</p>
    </div>
  )
}

const FindingModal = ({ finding, updateForm, setUpdateForm, onSubmit, onClose, isLoading }) => createPortal(
  <div className="fixed inset-0 z-[140] overflow-y-auto bg-black/72 p-4 backdrop-blur-md">
    <div className="flex min-h-full items-center justify-center py-8">
      <div className="surface-card flex w-full max-w-5xl flex-col overflow-hidden shadow-[0_42px_120px_rgba(0,0,0,0.48)]">
        <div className="flex shrink-0 items-start justify-between gap-4 border-b border-white/8 bg-white/[0.03] p-6">
          <div className="min-w-0">
            <div className="mb-3 flex flex-wrap items-center gap-3">
              <SeverityBadge severity={finding.severity} />
              <span className="font-mono text-sm text-slate-500">#{finding.id}</span>
            </div>
            <h2 className="break-words text-2xl font-bold text-white">{sanitizeFindingTitle(finding.title)}</h2>
            <p className="mt-2 break-all font-mono text-sm text-slate-400">{finding.affectedUrl || finding.target?.baseUrl}</p>
          </div>
          <button onClick={onClose} className="shrink-0 text-slate-400 transition-colors hover:text-slate-200">
            <X size={24} />
          </button>
        </div>

        <div className="grid flex-1 gap-6 overflow-y-auto p-6 xl:grid-cols-[minmax(0,1.45fr)_minmax(300px,0.95fr)]">
          <div className="space-y-6">
            <div>
              <h3 className="mb-3 text-sm font-semibold uppercase tracking-wider text-slate-300">Description</h3>
              <div className="surface-card-inner whitespace-pre-wrap break-words p-4 text-sm leading-6 text-slate-300">
                {sanitizeFindingDescription(finding.description) || 'No description available.'}
              </div>
            </div>

            {finding.remediation ? (
              <div>
                <h3 className="mb-3 text-sm font-semibold uppercase tracking-wider text-slate-300">
                  Remediation Guidance
                </h3>
                <div className="whitespace-pre-wrap break-words rounded-lg border border-prowler-green/20 bg-prowler-green/5 p-4 text-sm text-prowler-green/90">
                  {finding.remediation}
                </div>
              </div>
            ) : null}
          </div>

          <div className="min-w-0">
            <form onSubmit={onSubmit} className="surface-card-inner space-y-4 p-4">
              <h3 className="mb-4 border-b border-white/8 pb-3 font-semibold text-white">Workflow</h3>

              <div>
                <label className="mb-2 block text-xs font-medium text-slate-400">Status</label>
                <select
                  value={updateForm.status}
                  onChange={(event) => setUpdateForm({ ...updateForm, status: event.target.value })}
                  className="modal-input"
                >
                  <option value="OPEN">Open</option>
                  <option value="IN PROGRESS">In Progress</option>
                  <option value="RESOLVED">Resolved</option>
                  <option value="FALSE POSITIVE">False Positive</option>
                </select>
              </div>

              <div>
                <label className="mb-2 block text-xs font-medium text-slate-400">Assigned Analyst</label>
                <input
                  type="text"
                  value={updateForm.assignedUser}
                  onChange={(event) => setUpdateForm({ ...updateForm, assignedUser: event.target.value })}
                  className="modal-input"
                  placeholder="alice@company.com"
                />
              </div>

              <div>
                <label className="mb-2 block text-xs font-medium text-slate-400">Comments</label>
                <textarea
                  value={updateForm.comments}
                  onChange={(event) => setUpdateForm({ ...updateForm, comments: event.target.value })}
                  className="modal-input resize-none"
                  rows={4}
                  placeholder="Add your notes..."
                />
              </div>

              <button
                type="submit"
                disabled={isLoading}
                className="surface-button-primary w-full justify-center py-2.5 disabled:cursor-not-allowed disabled:opacity-50"
              >
                {isLoading ? 'Saving...' : 'Save Changes'}
              </button>
            </form>
          </div>
        </div>
      </div>
    </div>
  </div>,
  document.body,
)

function normalizeFindingStatus(status) {
  return String(status || 'OPEN').trim().toUpperCase()
}

export default FindingsList
