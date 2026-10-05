import { useEffect, useMemo, useRef, useState } from 'react'
import { createPortal } from 'react-dom'
import { AlertTriangle, Search, X, ChevronDown, Sparkles } from 'lucide-react'
import { useMutation, useQueryClient } from '@tanstack/react-query'
import { useSearchParams } from 'react-router-dom'
import { updateFinding } from '../../api/findingApi'
import { useScanWebSocket } from '../../hooks/useScanWebSocket'
import SeverityBadge from '../../components/ui/SeverityBadge'
import StatusBadge from '../../components/ui/StatusBadge'
import DarkSelect from '../../components/ui/DarkSelect'
import {
  getFindingDisplayDescription,
  getFindingEnrichmentStatus,
  getFindingExploitNarrative,
  hasFindingAiContent,
  normalizeFindingSeverity,
  sanitizeFindingDescription,
  sanitizeFindingTitle,
} from '../../lib/findingUtils'
import { useWorkspaceFindings } from '../../hooks/useWorkspaceFindings'
import { useWorkspaceScans } from '../../hooks/useWorkspaceScans'
import { useWorkspaceTargets } from '../../hooks/useWorkspaceTargets'
import { workspaceQueryKeys } from '../../lib/workspaceQueryKeys'

const ALL_TARGETS = 'all-targets'
const AI_DISPLAY_PREFERENCES_KEY = 'scannerx.findings.ai-display-preferences'

const readAiDisplayPreferences = () => {
  try {
    const saved = JSON.parse(window.localStorage.getItem(AI_DISPLAY_PREFERENCES_KEY) || '{}')
    return {
      showAiEnhanced: saved.showAiEnhanced !== false,
      showAiSeverityPriority: saved.showAiSeverityPriority !== false,
    }
  } catch {
    return { showAiEnhanced: true, showAiSeverityPriority: true }
  }
}

const FindingsList = () => {
  const queryClient = useQueryClient()
  const [searchParams, setSearchParams] = useSearchParams()
  const [selectedFinding, setSelectedFinding] = useState(null)
  const [isModalOpen, setIsModalOpen] = useState(false)
  const [updateForm, setUpdateForm] = useState({ status: '', assignedUser: '', comments: '' })
  const [filterSeverity, setFilterSeverity] = useState('All')
  const [filterStatus, setFilterStatus] = useState('All')
  const [searchQuery, setSearchQuery] = useState('')
  const [showAiEnhanced, setShowAiEnhanced] = useState(() => readAiDisplayPreferences().showAiEnhanced)
  const [showAiSeverityPriority, setShowAiSeverityPriority] = useState(() => readAiDisplayPreferences().showAiSeverityPriority)
  const [showAiMenu, setShowAiMenu] = useState(false)
  const [aiMenuPosition, setAiMenuPosition] = useState({ top: 0, left: 0 })
  const aiMenuTriggerRef = useRef(null)
  const aiMenuPanelRef = useRef(null)
  const selectedTargetId = searchParams.get('targetId') || ALL_TARGETS

  useEffect(() => {
    try {
      window.localStorage.setItem(AI_DISPLAY_PREFERENCES_KEY, JSON.stringify({ showAiEnhanced, showAiSeverityPriority }))
    } catch {
      // Keep the controls usable if browser storage is unavailable.
    }
  }, [showAiEnhanced, showAiSeverityPriority])

  const { targets } = useWorkspaceTargets()

  const { activeScan, isError: isScanStateError } = useWorkspaceScans()
  const { events } = useScanWebSocket(activeScan?.id)

  const targetId = selectedTargetId === ALL_TARGETS ? undefined : Number(selectedTargetId)

  const { findings, isLoading, isError, error } = useWorkspaceFindings({
    targetId,
    completedOnly: false,
    scope: 'list',
    queryOptions: {
      refetchInterval: activeScan ? 3000 : false,
    },
  })

  useEffect(() => {
    if (!showAiMenu) return undefined

    const closeOnOutsideClick = (event) => {
      if (!aiMenuPanelRef.current?.contains(event.target) && !aiMenuTriggerRef.current?.contains(event.target)) {
        setShowAiMenu(false)
      }
    }
    const closeOnEscape = (event) => {
      if (event.key === 'Escape') setShowAiMenu(false)
    }
    window.addEventListener('pointerdown', closeOnOutsideClick)
    window.addEventListener('keydown', closeOnEscape)
    return () => {
      window.removeEventListener('pointerdown', closeOnOutsideClick)
      window.removeEventListener('keydown', closeOnEscape)
    }
  }, [showAiMenu])

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
        aiDescription: String(finding.aiDescription || '').trim(),
        exploitNarrative: String(finding.exploitNarrative || '').trim(),
      }
      const key =
        finding.id ??
        [
          finding.target?.id ?? targetId ?? 'all-targets',
          normalizedFinding.title || 'finding',
          finding.affectedUrl || 'url',
          finding.severity || 'unknown',
          finding.createdAt || index,
        ].join('|')

      findingsByKey.set(key, normalizedFinding)
    })

    return Array.from(findingsByKey.values())
  }, [events, findings, targetId])

  const updateMutation = useMutation({
    mutationFn: ({ id, data }) => updateFinding(id, data),
    onSuccess: (savedFinding, variables) => {
      const updatedFinding = savedFinding && typeof savedFinding === 'object'
        ? savedFinding
        : { ...selectedFinding, ...variables.data }

      // Patch every cached findings scope so lists and scan views reflect the save immediately.
      queryClient.setQueriesData({ queryKey: workspaceQueryKeys.findings }, (current) => {
        if (!Array.isArray(current)) return current
        return current.map((finding) =>
          String(finding?.id) === String(variables.id)
            ? { ...finding, ...updatedFinding }
            : finding,
        )
      })
      queryClient.invalidateQueries({ queryKey: workspaceQueryKeys.findings })
      queryClient.invalidateQueries({ queryKey: workspaceQueryKeys.dashboardSummary })
      queryClient.invalidateQueries({ queryKey: workspaceQueryKeys.scans })
      queryClient.invalidateQueries({ queryKey: workspaceQueryKeys.targets })
      queryClient.invalidateQueries({ queryKey: ['scan'] })
      queryClient.invalidateQueries({ queryKey: ['scan-report'] })
      queryClient.invalidateQueries({ queryKey: workspaceQueryKeys.reportSummary })
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
    // UI-only filtering: backend processing and saved findings are unchanged.
    const normalizedSeverity = normalizeFindingSeverity(finding.severity)
    const normalizedStatus = normalizeFindingStatus(finding.status)
    const matchesSeverity = filterSeverity === 'All' || normalizedSeverity === filterSeverity
    const matchesStatus = filterStatus === 'All' || normalizedStatus === filterStatus
    const query = searchQuery.trim().toLowerCase()
    const matchesSearch =
      !query ||
      [
        finding.title,
        showAiEnhanced ? getFindingDisplayDescription(finding) : finding.description,
        showAiEnhanced ? getFindingExploitNarrative(finding) : '',
        finding.affectedUrl,
        finding.target?.name,
      ]
        .some((value) => String(value || '').toLowerCase().includes(query))

    return matchesSeverity && matchesStatus && matchesSearch
  })

  const openAiMenu = () => {
    if (aiMenuTriggerRef.current) {
      const rect = aiMenuTriggerRef.current.getBoundingClientRect()
      const width = Math.min(304, window.innerWidth - 24)
      setAiMenuPosition({
        top: Math.max(8, Math.min(rect.bottom + 10, window.innerHeight - 360)),
        left: Math.max(8, Math.min(rect.left, window.innerWidth - width - 12)),
      })
    }
    setShowAiMenu((open) => !open)
  }

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
        {isScanStateError ? (
          <div className="border-b border-rose-500/16 bg-rose-500/8 px-4 py-3">
            <div className="flex items-center gap-3 text-sm text-rose-100">
              <AlertTriangle className="h-4 w-4 shrink-0 text-rose-300" />
              <span>Live scan state is temporarily unavailable. Historical findings are still available below.</span>
            </div>
          </div>
        ) : null}

        {isError ? (
          <div className="border-b border-rose-500/16 bg-rose-500/8 px-4 py-3">
            <div className="flex items-center gap-3 text-sm text-rose-100">
              <AlertTriangle className="h-4 w-4 shrink-0 text-rose-300" />
              <span>{getFindingsErrorMessage(error, 'Unable to refresh findings right now. Showing any live findings already received.')}</span>
            </div>
          </div>
        ) : null}

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

          <div className="relative ml-2">
            <button
              ref={aiMenuTriggerRef}
              type="button"
              aria-expanded={showAiMenu}
              aria-haspopup="dialog"
              onClick={openAiMenu}
              className="inline-flex h-12 items-center gap-2 rounded-xl border border-white/10 bg-black/70 px-4 text-sm font-medium text-zinc-200 shadow-inner transition-colors hover:border-white/20 hover:bg-black focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-prowler-green/50"
            >
              <Sparkles size={15} className="text-slate-300" />
              AI
              <ChevronDown size={14} className={`text-slate-400 transition-transform ${showAiMenu ? 'rotate-180' : ''}`} />
            </button>
            {showAiMenu && createPortal(
              <div
                ref={aiMenuPanelRef}
                role="dialog"
                aria-label="AI findings display options"
                style={{ top: `${aiMenuPosition.top}px`, left: `${aiMenuPosition.left}px` }}
                className="fixed z-[220] w-[min(19rem,calc(100vw-1.5rem))] overflow-hidden rounded-2xl border border-white/12 bg-black/80 p-1.5 shadow-[0_24px_70px_rgba(0,0,0,0.65)] backdrop-blur-2xl"
              >
                <div className="border-b border-white/8 px-3.5 py-3">
                  <div className="flex items-center gap-2 text-sm font-semibold text-white">
                    <Sparkles size={15} className="text-prowler-green" />
                    AI display options
                  </div>
                  <p className="mt-1 text-xs leading-5 text-slate-400">Change how saved findings appear. Processing continues in the background.</p>
                </div>
                <div className="space-y-0.5 p-1">
                  <AiDisplayToggle
                    label="AI-enhanced descriptions"
                    description="Show AI summary and defensive guidance"
                    checked={showAiEnhanced}
                    onChange={setShowAiEnhanced}
                  />
                  <AiDisplayToggle
                    label="AI severity and priority"
                    description="Show AI suggestions beside scanner severity"
                    checked={showAiSeverityPriority}
                    onChange={setShowAiSeverityPriority}
                  />
                </div>
              </div>,
              document.body,
            )}
          </div>

          <DarkSelect
            value={selectedTargetId}
            onChange={(nextTargetId) => {
              const params = new URLSearchParams(searchParams)
              if (nextTargetId === ALL_TARGETS) {
                params.delete('targetId')
              } else {
                params.set('targetId', nextTargetId)
              }
              setSearchParams(params, { replace: true })
            }}
            className="min-w-[180px]"
            options={[
              { value: ALL_TARGETS, label: 'All Targets' },
              ...targets.map((target) => ({
                value: String(target.id),
                label: target.name,
              })),
            ]}
          />

          <DarkSelect
            value={filterSeverity}
            onChange={setFilterSeverity}
            className="min-w-[140px]"
            options={SEVERITY_FILTER_OPTIONS}
          />

          <DarkSelect
            value={filterStatus}
            onChange={setFilterStatus}
            className="min-w-[140px]"
            options={STATUS_FILTER_OPTIONS}
          />
        </div>

        <div className="table-scroll">
          {isLoading ? (
            <div className="flex h-full items-center justify-center text-slate-400">Loading findings...</div>
          ) : isError && filteredFindings.length === 0 ? (
            <div className="empty-state">
              <div className="empty-state-panel">
                <div className="empty-state-icon">
                  <AlertTriangle size={34} />
                </div>
                <p className="text-slate-300">Unable to load findings right now.</p>
                <p className="mt-2 text-sm text-slate-500">
                  {getFindingsErrorMessage(error, 'Please refresh and try again.')}
                </p>
              </div>
            </div>
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
        <FindingModal showAiEnhanced={showAiEnhanced} showAiSeverityPriority={showAiSeverityPriority}
          finding={selectedFinding}
          updateForm={updateForm}
          setUpdateForm={setUpdateForm}
          onSubmit={handleUpdateSubmit}
          onClose={() => setIsModalOpen(false)}
          isLoading={updateMutation.isPending}
          saveError={updateMutation.error ? getFindingsErrorMessage(updateMutation.error, 'Unable to save finding changes.') : ''}
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

const FindingModal = ({ finding, updateForm, setUpdateForm, onSubmit, onClose, isLoading, saveError, showAiEnhanced, showAiSeverityPriority }) => {
  const hasAiContent = hasFindingAiContent(finding)
  const enrichmentStatus = getFindingEnrichmentStatus(finding)

  return createPortal(
    <div className="fixed inset-0 z-[140] overflow-y-auto bg-black/72 p-4 backdrop-blur-md">
      <div className="flex min-h-full items-center justify-center py-8">
        <div className="surface-card flex w-full max-w-5xl flex-col overflow-hidden shadow-[0_42px_120px_rgba(0,0,0,0.48)]">
          <div className="flex shrink-0 items-start justify-between gap-4 border-b border-white/8 bg-white/[0.03] p-6">
            <div className="min-w-0">
              <div className="mb-3 flex flex-wrap items-center gap-3">
                <SeverityBadge severity={finding.severity} />
                {showAiSeverityPriority && finding.aiSeverity ? <span className="rounded-md border border-prowler-green/20 bg-prowler-green/[0.08] px-2 py-1 text-xs text-prowler-green" title={finding.aiSeverityReason || 'AI severity suggestion'}>AI {normalizeFindingSeverity(finding.aiSeverity)}</span> : null}
                {showAiSeverityPriority && finding.aiPriorityScore != null ? <span className="rounded-md border border-indigo-400/20 bg-indigo-400/[0.08] px-2 py-1 text-xs font-mono text-indigo-200" title={finding.aiPriorityReason || 'AI impact priority'}>Priority {finding.aiPriorityScore}/10</span> : null}
                {finding.aiDuplicateOfId ? <span className="rounded-md border border-white/10 bg-white/[0.05] px-2 py-1 text-xs text-slate-300" title={`Linked to finding #${finding.aiDuplicateOfId}`}>Duplicate of #{finding.aiDuplicateOfId}</span> : null}
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
                  {showAiEnhanced && getFindingDisplayDescription(finding) ? getFindingDisplayDescription(finding) : finding.description || 'No description available.'}
                </div>
                {!hasAiContent && (enrichmentStatus === 'PENDING' || enrichmentStatus === 'PROCESSING') ? (
                  <p className="mt-3 text-xs text-cyan-200/80">
                    AI enrichment is generating a clearer analyst summary for this finding.
                  </p>
                ) : null}
                {!hasAiContent && enrichmentStatus === 'FAILED' ? (
                  <div className="mt-3 rounded-lg border border-amber-300/15 bg-amber-300/[0.05] px-3 py-2.5">
                    <p className="text-xs text-amber-200/90">
                      AI enrichment failed for this finding, so ScannerX is showing the raw scanner description instead.
                    </p>
                    {finding.aiEnrichmentError ? (
                      <p className="mt-1.5 break-words text-xs text-slate-400">Reason: {finding.aiEnrichmentError}</p>
                    ) : null}
                  </div>
                ) : null}
              </div>

              {showAiEnhanced && getFindingExploitNarrative(finding) ? (
                <div>
                  <h3 className="mb-3 text-sm font-semibold uppercase tracking-wider text-slate-300">
                    Attacker Perspective And Defensive Guidance
                  </h3>
                  <div className="surface-card-inner whitespace-pre-wrap break-words p-4 text-sm leading-6 text-slate-300">
                    {getFindingExploitNarrative(finding)}
                  </div>
                </div>
              ) : null}

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
                  <DarkSelect
                    value={updateForm.status}
                    onChange={(status) => setUpdateForm({ ...updateForm, status })}
                    variant="field"
                    options={STATUS_FILTER_OPTIONS.filter((option) => option.value !== 'All')}
                  />
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

                {saveError ? (
                  <p role="alert" className="rounded-lg border border-rose-400/20 bg-rose-400/[0.06] px-3 py-2 text-xs text-rose-200">
                    {saveError}
                  </p>
                ) : null}

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
}

function normalizeFindingStatus(status) {
  return String(status || 'OPEN').trim().toUpperCase()
}

const SEVERITY_FILTER_OPTIONS = [
  { value: 'All', label: 'All Severities' },
  { value: 'CRITICAL', label: 'Critical Only' },
  { value: 'HIGH', label: 'High Only' },
  { value: 'MEDIUM', label: 'Medium Only' },
  { value: 'LOW', label: 'Low Only' },
]

const STATUS_FILTER_OPTIONS = [
  { value: 'All', label: 'All Status' },
  { value: 'OPEN', label: 'Open' },
  { value: 'IN PROGRESS', label: 'In Progress' },
  { value: 'RESOLVED', label: 'Resolved' },
  { value: 'FALSE POSITIVE', label: 'False Positive' },
]

export default FindingsList

function getFindingsErrorMessage(error, fallbackMessage) {
  return error?.response?.data?.message || error?.message || fallbackMessage
}

function AiDisplayToggle({ label, description, checked, onChange }) {
  return (
    <button
      type="button"
      role="switch"
      aria-checked={checked}
      onClick={() => onChange(!checked)}
      className="flex w-full items-center justify-between gap-4 rounded-xl px-3 py-2.5 text-left transition-colors hover:bg-white/[0.05] focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-prowler-green/40"
    >
      <span className="min-w-0">
        <span className="block text-[13px] font-medium text-slate-200">{label}</span>
        <span className="mt-0.5 block text-[11px] leading-4 text-slate-500">{description}</span>
      </span>
      <span className={`relative h-[21px] w-[38px] shrink-0 rounded-full border transition-colors ${checked ? 'border-prowler-green/50 bg-prowler-green/30' : 'border-white/15 bg-white/[0.06]'}`}>
        <span className={`absolute top-[3px] h-[13px] w-[13px] rounded-full shadow-sm transition-all ${checked ? 'left-[20px] bg-prowler-green' : 'left-[3px] bg-slate-400'}`} />
      </span>
    </button>
  )
}
