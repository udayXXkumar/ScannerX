import { useMemo } from 'react'
import { Activity, ChevronRight, Clock3, Download, Info, PauseCircle, Play, Shield, Trash2 } from 'lucide-react'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { useNavigate, useParams } from 'react-router-dom'
import {
  cancelScan,
  deleteScan,
  downloadScanReportCsv,
  downloadScanReportJson,
  getScanActivity,
  getScanById,
  getScanReportJson,
  pauseScan,
  resumeScan,
} from '../../api/scanApi'
import SeverityBadge from '../../components/ui/SeverityBadge'
import StatusBadge from '../../components/ui/StatusBadge'
import { useScanWebSocket } from '../../hooks/useScanWebSocket'
import {
  normalizeFindingSeverity,
  sanitizeFindingDescription,
  sanitizeFindingTitle,
} from '../../lib/findingUtils'
import { getScanDisplayName, getScanStatusLabel, getScanTierLabel, isActiveScanStatus, normalizeScanStatus } from '../../lib/scanUtils'

const ScanDetail = () => {
  const { id } = useParams()
  const navigate = useNavigate()
  const queryClient = useQueryClient()

  const { data: scan, isLoading } = useQuery({
    queryKey: ['scan', id],
    queryFn: () => getScanById(id),
    enabled: Boolean(id),
    refetchInterval: (query) => (isActiveScanStatus(query.state.data?.status) ? 2000 : false),
  })

  const { data: reportData } = useQuery({
    queryKey: ['scan-report', id],
    queryFn: () => getScanReportJson(id),
    enabled: Boolean(id),
    refetchInterval: isActiveScanStatus(scan?.status) ? 3000 : false,
  })

  const { data: activityRecords = [] } = useQuery({
    queryKey: ['scan-activity', id],
    queryFn: () => getScanActivity(id),
    enabled: Boolean(id),
    refetchInterval: isActiveScanStatus(scan?.status) ? 3000 : false,
  })

  const cancelMutation = useMutation({
    mutationFn: () => cancelScan(id),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['scan', id] })
      queryClient.invalidateQueries({ queryKey: ['scans'] })
      queryClient.invalidateQueries({ queryKey: ['dashboardSummary'] })
      queryClient.invalidateQueries({ queryKey: ['targets'] })
    },
  })

  const deleteMutation = useMutation({
    mutationFn: () => deleteScan(id),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['scans'] })
      queryClient.invalidateQueries({ queryKey: ['findings'] })
      queryClient.invalidateQueries({ queryKey: ['dashboardSummary'] })
      queryClient.invalidateQueries({ queryKey: ['notifications'] })
      queryClient.invalidateQueries({ queryKey: ['notifications', 'unreadCount'] })
      queryClient.invalidateQueries({ queryKey: ['targets'] })
      navigate('/scans')
    },
    onError: (error) => {
      window.alert(getErrorMessage(error, 'Unable to delete this scan right now.'))
    },
  })

  const pauseMutation = useMutation({
    mutationFn: () => pauseScan(id),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['scan', id] })
      queryClient.invalidateQueries({ queryKey: ['scan-activity', id] })
      queryClient.invalidateQueries({ queryKey: ['scans'] })
      queryClient.invalidateQueries({ queryKey: ['targets'] })
    },
    onError: (error) => {
      window.alert(getErrorMessage(error, 'Unable to pause this scan right now.'))
    },
  })

  const resumeMutation = useMutation({
    mutationFn: () => resumeScan(id),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['scan', id] })
      queryClient.invalidateQueries({ queryKey: ['scan-activity', id] })
      queryClient.invalidateQueries({ queryKey: ['scans'] })
      queryClient.invalidateQueries({ queryKey: ['targets'] })
    },
    onError: (error) => {
      window.alert(getErrorMessage(error, 'Unable to resume this scan right now.'))
    },
  })

  const { events, isConnected } = useScanWebSocket(id)

  const latestProgressEvent = useMemo(
    () => [...events].reverse().find((event) => event.type === 'SCAN_PROGRESS'),
    [events],
  )

  const latestStatusEvent = useMemo(
    () => [...events].reverse().find((event) => event.type === 'SCAN_STATUS'),
    [events],
  )

  const findings = useMemo(() => {
    const liveFindings = events
      .filter((event) => event.type === 'FINDING_FOUND')
      .map((event) => event.data)
      .reverse()

    const persistedFindings = Array.isArray(reportData?.findings) ? [...reportData.findings].reverse() : []
    const findingsByKey = new Map()

    ;[...persistedFindings, ...liveFindings].forEach((finding, index) => {
      if (!finding) {
        return
      }

      const sanitizedFinding = {
        ...finding,
        severity: normalizeFindingSeverity(finding.severity),
        title: sanitizeFindingTitle(finding.title),
        description: sanitizeFindingDescription(finding.description),
      }

      const key =
        finding.id ??
        `${sanitizedFinding.title || 'finding'}-${finding.affectedUrl || finding.endpoint || 'url'}-${finding.createdAt || index}`

      findingsByKey.set(key, sanitizedFinding)
    })

    return Array.from(findingsByKey.values())
  }, [events, reportData])

  const activityItems = useMemo(
    () => {
      const mergedEntries = [
        ...activityRecords.map((record) => ({
          id: `persisted-${record.id}`,
          type: record.type,
          data: record.message,
          stageOrder: record.stageOrder,
          timestamp: record.createdAt,
        })),
        ...events.map((event, index) => ({
          ...event,
          id: event.id || `live-${event.timestamp || index}-${event.type}`,
        })),
      ]

      const uniqueItems = new Map()
      mergedEntries.forEach((entry, index) => {
        const activityItem = buildActivityItem(entry, index)
        if (!activityItem) {
          return
        }

        const dedupeKey = [
          entry.type,
          entry.stageOrder ?? '',
          extractEventTime(entry) ?? '',
          resolveActivityMessage(entry) ?? '',
        ].join('|')

        if (!uniqueItems.has(dedupeKey)) {
          uniqueItems.set(dedupeKey, activityItem)
        }
      })

      return Array.from(uniqueItems.values()).reverse()
    },
    [activityRecords, events],
  )

  const displayStatus = latestStatusEvent?.data?.status || scan?.status
  const displayProgress = useMemo(() => {
    const progressPayload = latestProgressEvent?.data
    const fallbackProgress = Number(latestStatusEvent?.data?.progress ?? scan?.progress ?? 0)
    if (!progressPayload) {
      return Math.min(fallbackProgress, 100)
    }

    const baseProgress = Number(progressPayload.progress ?? fallbackProgress)
    const processedSteps = Number(progressPayload.processedSteps)
    const totalSteps = Number(progressPayload.totalSteps)
    const stageProgressPercent = Number(progressPayload.stageProgressPercent)

    if (
      Number.isFinite(processedSteps) &&
      Number.isFinite(totalSteps) &&
      totalSteps > 0 &&
      Number.isFinite(stageProgressPercent)
    ) {
      const interpolatedProgress = ((processedSteps + Math.max(0, Math.min(stageProgressPercent, 100)) / 100) * 100) / totalSteps
      return Math.min(100, Math.max(baseProgress, interpolatedProgress))
    }

    return Math.min(baseProgress, 100)
  }, [latestProgressEvent, latestStatusEvent, scan?.progress])

  const adaptiveBudgetActive = Boolean(latestProgressEvent?.data?.adaptiveBudgetActive)
  const batchCompleted = Number(latestProgressEvent?.data?.batchCompleted)
  const batchTotal = Number(latestProgressEvent?.data?.batchTotal)
  const progressHelper = adaptiveBudgetActive
    ? 'Extending scan window to finish current stage'
    : scan?.target?.name || 'Current target'
  const progressDetail =
    Number.isFinite(batchCompleted) && Number.isFinite(batchTotal) && batchTotal > 0
      ? `Batch ${Math.min(batchCompleted + 1, batchTotal)} of ${batchTotal}`
      : null
  const status = normalizeScanStatus(displayStatus)
  const showPause = status === 'QUEUED' || status === 'RUNNING'
  const showResume = status === 'PAUSED'
  const showCancel = isActiveScanStatus(status)
  const showDelete = !isLoading && scan && !showCancel

  if (isLoading) {
    return <div className="flex min-h-[50vh] items-center justify-center text-slate-400">Loading scan...</div>
  }

  return (
    <div className="flex min-h-full flex-col gap-6 pb-6">
      <div className="shrink-0">
        <div className="flex items-start justify-between gap-4">
          <div>
            <div className="flex flex-wrap items-center gap-3">
              <h1 className="text-2xl font-semibold text-gray-100">{getScanDisplayName(scan)}</h1>
              <StatusBadge status={displayStatus} />
              <span
                className={`inline-flex items-center gap-2 rounded-full border px-3 py-1 text-xs font-medium ${
                  isConnected
                    ? 'border-[#60dfb2]/30 bg-[#60dfb2]/10 text-[#7be7c0]'
                    : 'border-white/10 bg-white/[0.03] text-slate-400'
                }`}
              >
                <Activity className={`h-3 w-3 ${isConnected ? 'animate-pulse' : ''}`} />
                {isConnected ? 'Live feed connected' : 'Live feed offline'}
              </span>
            </div>
            <p className="mt-2 text-gray-400">
              Target: {scan?.target?.baseUrl || 'Unknown target'} •{' '}
              {scan?.createdAt ? new Date(scan.createdAt).toLocaleString() : ''}
            </p>
          </div>

          <div className="flex flex-wrap items-center gap-3">
            <button
              onClick={() => downloadScanReportCsv(id)}
              className="inline-flex items-center rounded-md border border-border-subtle bg-bg-panel px-4 py-2 text-sm font-medium text-gray-100 transition-colors hover:bg-white/5"
            >
              <Download className="mr-2 h-4 w-4" />
              CSV
            </button>
            <button
              onClick={() => downloadScanReportJson(id)}
              className="inline-flex items-center rounded-md border border-border-subtle bg-bg-panel px-4 py-2 text-sm font-medium text-gray-100 transition-colors hover:bg-white/5"
            >
              <Download className="mr-2 h-4 w-4" />
              JSON
            </button>
            {showPause ? (
              <button
                onClick={() => pauseMutation.mutate()}
                disabled={pauseMutation.isPending}
                className="inline-flex items-center rounded-md border border-border-subtle bg-bg-panel px-4 py-2 text-sm font-medium text-gray-100 transition-colors hover:bg-white/5 disabled:cursor-not-allowed disabled:opacity-60"
              >
                <PauseCircle className="mr-2 h-4 w-4" />
                {pauseMutation.isPending ? 'Pausing...' : 'Pause Scan'}
              </button>
            ) : null}
            {showResume ? (
              <button
                onClick={() => resumeMutation.mutate()}
                disabled={resumeMutation.isPending}
                className="inline-flex items-center rounded-md border border-border-subtle bg-bg-panel px-4 py-2 text-sm font-medium text-gray-100 transition-colors hover:bg-white/5 disabled:cursor-not-allowed disabled:opacity-60"
              >
                <Play className="mr-2 h-4 w-4 fill-current" />
                {resumeMutation.isPending ? 'Resuming...' : 'Resume Scan'}
              </button>
            ) : null}
            {showCancel ? (
              <button
                onClick={() => {
                  if (window.confirm('Cancel this scan?')) {
                    cancelMutation.mutate()
                  }
                }}
                disabled={cancelMutation.isPending}
                className="rounded-md border border-border-subtle bg-bg-panel px-4 py-2 text-sm font-medium text-red-500 transition-colors hover:bg-white/5 hover:text-red-400 disabled:cursor-not-allowed disabled:opacity-60"
              >
                {cancelMutation.isPending ? 'Cancelling...' : 'Cancel Scan'}
              </button>
            ) : null}
            {showDelete ? (
              <button
                onClick={() => {
                  if (window.confirm(`Delete "${getScanDisplayName(scan)}" and all its findings?`)) {
                    deleteMutation.mutate()
                  }
                }}
                disabled={deleteMutation.isPending}
                className="inline-flex items-center rounded-md border border-rose-500/20 bg-rose-500/8 px-4 py-2 text-sm font-medium text-rose-200 transition-colors hover:bg-rose-500/12 disabled:cursor-not-allowed disabled:opacity-60"
              >
                <Trash2 className="mr-2 h-4 w-4" />
                {deleteMutation.isPending ? 'Deleting...' : 'Delete Scan'}
              </button>
            ) : null}
          </div>
        </div>
      </div>

      <div className="grid gap-6 lg:grid-cols-3">
        <SummaryCard
          label="Status"
          value={getScanStatusLabel(displayStatus)}
          helper={scan?.completedAt ? `Finished ${new Date(scan.completedAt).toLocaleString()}` : 'Awaiting completion'}
        />
        <SummaryCard
          label="Recorded Findings"
          value={String(findings.length)}
          helper="New results appear here as the scan records them."
        />
        <SummaryCard
          label="Progress"
          value={`${displayProgress}%`}
          helper={progressHelper}
          detail={progressDetail}
        />
      </div>

      <section className="flex min-h-[460px] flex-1 flex-col overflow-hidden rounded-xl border border-border-subtle bg-bg-panel shadow-sm">
        <div className="shrink-0 border-b border-border-subtle bg-bg-base/30 p-4">
          <div className="flex items-center justify-between">
            <h3 className="flex items-center font-medium text-gray-200">
              <Shield className="mr-2 h-4 w-4 text-primary" /> Live Findings Feed
            </h3>
            <span className="rounded border border-white/5 bg-white/5 px-2 py-1 text-xs font-medium text-gray-400">
              {findings.length} Discovered
            </span>
          </div>
        </div>

        <div className="relative flex-1 overflow-y-auto p-4">
          {findings.length === 0 ? (
            <div className="flex h-full items-center justify-center">
              <p className="text-sm text-slate-500">Results will appear here as the scan records new findings.</p>
            </div>
          ) : (
            <div className="space-y-3">
              {findings.map((finding, index) => (
                <div
                  key={finding.id || index}
                  className="animate-in fade-in slide-in-from-bottom-4 flex items-start gap-4 rounded-lg border border-border-subtle bg-bg-base p-4 transition-colors duration-500 hover:border-border-subtle/80"
                >
                  <div className="mt-0.5 shrink-0">
                    <SeverityBadge severity={finding.severity} />
                  </div>
                  <div className="min-w-0 flex-1">
                    <h4 className="text-pretty break-words font-medium text-gray-200">
                      {finding.title}
                    </h4>
                    <p className="mt-1 break-words font-mono text-xs text-gray-500">
                      {finding.affectedUrl || finding.endpoint || scan?.target?.baseUrl || 'No affected URL'}
                    </p>
                    {finding.description ? (
                      <p className="mt-2 line-clamp-2 text-sm text-slate-400">{finding.description}</p>
                    ) : null}
                  </div>
                  <div className="flex shrink-0 items-center text-xs text-gray-500">
                    Just now
                    <ChevronRight className="ml-2 h-4 w-4 text-gray-600" />
                  </div>
                </div>
              ))}
            </div>
          )}
        </div>
      </section>

      <div className="grid gap-6 xl:grid-cols-[320px_minmax(0,1fr)]">
        <section className="rounded-xl border border-border-subtle bg-bg-panel p-5 shadow-sm">
          <h3 className="text-sm font-medium uppercase tracking-wider text-gray-400">Scan Summary</h3>
          <div className="mt-5 space-y-4">
            <SummaryRow label="Target Name" value={getScanDisplayName(scan)} />
            <SummaryRow label="Base URL" value={scan?.target?.baseUrl || 'No target URL'} monospace />
            <SummaryRow label="Tier" value={getScanTierLabel(scan?.tier || scan?.profileType)} />
            <SummaryRow
              label="Created"
              value={scan?.createdAt ? new Date(scan.createdAt).toLocaleString() : 'No timestamp'}
            />
          </div>
        </section>

        <section className="flex min-h-[260px] flex-col overflow-hidden rounded-xl border border-border-subtle bg-[#0f1115] p-4 shadow-inner">
          <h3 className="mb-3 flex items-center text-xs font-medium uppercase text-gray-500">
            <TerminalIcon className="mr-1.5 h-3 w-3" /> Activity
          </h3>
          <div className="flex-1 space-y-3 overflow-y-auto">
            {activityItems.length === 0 ? (
              <div className="flex h-full items-center justify-center text-sm text-slate-500">
                Activity updates will appear during the scan.
              </div>
            ) : (
              activityItems.map((item) => (
                <div
                  key={item.id}
                  className="rounded-lg border border-white/6 bg-white/[0.03] px-4 py-3"
                >
                  <div className="flex items-start justify-between gap-4">
                    <div className="min-w-0">
                      <p className={`text-sm font-medium ${item.toneClass}`}>{item.label}</p>
                      {item.description ? (
                        <p className="mt-1 text-sm text-slate-400">{item.description}</p>
                      ) : null}
                    </div>
                    <div className="flex shrink-0 items-center gap-2 text-xs text-slate-500">
                      <Clock3 className="h-3.5 w-3.5" />
                      <span>{item.timeLabel}</span>
                    </div>
                  </div>
                </div>
              ))
            )}
          </div>
        </section>
      </div>
    </div>
  )
}

const SummaryCard = ({ label, value, helper, detail }) => (
  <div className="rounded-xl border border-border-subtle bg-bg-panel px-5 py-5 shadow-sm">
    <p className="text-xs font-medium uppercase tracking-[0.2em] text-slate-500">{label}</p>
    <p className="mt-3 text-2xl font-semibold text-white">{value}</p>
    <p className="mt-2 text-sm text-slate-400">{helper}</p>
    {detail ? <p className="mt-1 text-xs text-slate-500">{detail}</p> : null}
  </div>
)

const SummaryRow = ({ label, value, monospace = false }) => (
  <div className="rounded-lg border border-white/6 bg-white/[0.03] px-4 py-3">
    <p className="text-[11px] font-semibold uppercase tracking-[0.18em] text-slate-500">{label}</p>
    <p className={`mt-2 break-words text-sm text-slate-200 ${monospace ? 'font-mono' : ''}`}>{value}</p>
  </div>
)

const TerminalIcon = Info

function buildStageActivityItem(event, index, fallbackLabel, description, toneClass, timeLabel) {
  const stageLabel = extractStageLabel(resolveActivityMessage(event))

  return {
    id: event.id || `activity-${index}`,
    label: stageLabel || fallbackLabel,
    description,
    toneClass,
    timeLabel,
  }
}

function buildActivityItem(event, index) {
  if (!event?.type) {
    return null
  }

  if (event.type === 'FINDING_FOUND') {
    return null
  }

  const eventTime = extractEventTime(event)
  const timeLabel = eventTime ? new Date(eventTime).toLocaleTimeString() : 'Now'

  switch (event.type) {
    case 'SCAN_STARTED':
      return {
        id: `activity-${index}`,
        label: 'Scan started',
        description: 'The scan job was accepted and execution has begun.',
        toneClass: 'text-zinc-100',
        timeLabel,
      }
    case 'SCAN_COMPLETED':
      return {
        id: `activity-${index}`,
        label: 'Scan completed',
        description: 'All enabled scan stages finished and the findings feed is finalized.',
        toneClass: 'text-emerald-200',
        timeLabel,
      }
    case 'SCAN_FAILED':
      return {
        id: event.id || `activity-${index}`,
        label: 'Scan failed',
        description: sanitizeActivityText(resolveActivityMessage(event)),
        toneClass: 'text-rose-200',
        timeLabel,
      }
    case 'SCAN_RESUMED':
      return {
        id: event.id || `activity-${index}`,
        label: 'Scan resumed',
        description: 'The scan has been queued to continue from the paused stage.',
        toneClass: 'text-emerald-200',
        timeLabel,
      }
    case 'PAUSE_REQUESTED':
      return {
        id: event.id || `activity-${index}`,
        label: 'Pause requested',
        description: 'The current stage is being stopped so the scan can pause cleanly.',
        toneClass: 'text-amber-200',
        timeLabel,
      }
    case 'SCAN_PAUSED':
      return {
        id: event.id || `activity-${index}`,
        label: 'Scan paused',
        description: sanitizeActivityText(resolveActivityMessage(event)),
        toneClass: 'text-amber-200',
        timeLabel,
      }
    case 'SCAN_CANCELLED':
      return {
        id: event.id || `activity-${index}`,
        label: 'Scan cancelled',
        description: 'The scan was stopped and will not resume.',
        toneClass: 'text-slate-200',
        timeLabel,
      }
    case 'STAGE_STARTED':
      return buildStageActivityItem(
        event,
        index,
        'Stage started',
        sanitizeActivityText(resolveActivityMessage(event)) || 'Stage started.',
        'text-zinc-100',
        timeLabel,
      )
    case 'STAGE_COMPLETED':
      return buildStageActivityItem(
        event,
        index,
        'Stage completed',
        sanitizeActivityText(resolveActivityMessage(event)) || 'This stage finished successfully.',
        'text-emerald-200',
        timeLabel,
      )
    case 'STAGE_FAILED':
      return buildStageActivityItem(
        event,
        index,
        'Stage completed with warnings',
        sanitizeActivityText(resolveActivityMessage(event)) || 'One stage reported a recoverable issue.',
        'text-amber-200',
        timeLabel,
      )
    case 'LOG': {
      const description = sanitizeActivityText(resolveActivityMessage(event))
      if (!description || /update received/i.test(description)) {
        return null
      }
      return {
        id: event.id || `activity-${index}`,
        label: 'Scanner update',
        description,
        toneClass: 'text-slate-200',
        timeLabel,
      }
    }
    default:
      return null
  }
}

function sanitizeActivityText(value) {
  const rawValue = String(value || '').trim()
  if (!rawValue) {
    return ''
  }

  return rawValue
    .replace(/^\[(?:scan|sys)\]\s*/i, '')
    .replace(/^\[[^\]]+\]\s*/i, '')
    .replace(/Starting Stage 1 - .*/i, 'Profiling started.')
    .replace(/Starting Stage 2 - .*/i, 'Discovery started.')
    .replace(/Starting Stage 3 - .*/i, 'Security checks started.')
    .replace(/Starting Stage 4 - .*/i, 'Cross-site scripting analysis started.')
    .replace(/Starting Stage 5 - .*/i, 'Injection analysis started.')
}

function extractStageLabel(message) {
  const sanitized = sanitizeActivityText(message)
  if (!sanitized) {
    return ''
  }

  return sanitized
    .replace(/\s+started\.?$/i, '')
    .replace(/\s+completed with recoverable issues\.?$/i, '')
    .replace(/\s+completed\.?$/i, '')
    .trim()
}

function extractEventTime(event) {
  return event?.timestamp || event?.createdAt || event?.time || null
}

function resolveActivityMessage(event) {
  if (typeof event?.message === 'string') {
    return event.message
  }

  if (typeof event?.data === 'string') {
    return event.data
  }

  return ''
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

export default ScanDetail
