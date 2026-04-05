import { useMemo, useState } from 'react'
import { useQuery } from '@tanstack/react-query'
import { useNavigate } from 'react-router-dom'
import { getDashboardSummary } from '../../api/dashboardApi'
import FindingsStatusChart from '../../components/dashboard/FindingsStatusChart'
import FindingsTrendChart from '../../components/dashboard/FindingsTrendChart'
import RecentScans from '../../components/dashboard/RecentScans'
import ResourceInventory from '../../components/dashboard/ResourceInventory'
import RiskSeverityChart from '../../components/dashboard/RiskSeverityChart'
import ThreatScore from '../../components/dashboard/ThreatScore'
import DarkSelect from '../../components/ui/DarkSelect'
import {
  getFindingCategoryLabel,
  normalizeFindingSeverity,
  sanitizeFindingTitle,
} from '../../lib/findingUtils'
import { useWorkspaceFindings } from '../../hooks/useWorkspaceFindings'
import { useWorkspaceScans } from '../../hooks/useWorkspaceScans'
import { useWorkspaceTargets } from '../../hooks/useWorkspaceTargets'
import { workspaceQueryKeys } from '../../lib/workspaceQueryKeys'

const ALL_TARGETS = 'all-targets'

const Dashboard = () => {
  const navigate = useNavigate()
  const [selectedTargetId, setSelectedTargetId] = useState(ALL_TARGETS)

  const scansQuery = useWorkspaceScans()
  const targetsQuery = useWorkspaceTargets()

  const allScans = useMemo(
    () => scansQuery.scans,
    [scansQuery.scans],
  )
  const targets = targetsQuery.targets

  const effectiveSelectedTargetId =
    selectedTargetId === ALL_TARGETS || targets.some((target) => String(target.id) === String(selectedTargetId))
      ? selectedTargetId
      : ALL_TARGETS

  const activeTargetId = effectiveSelectedTargetId === ALL_TARGETS ? undefined : Number(effectiveSelectedTargetId)

  const summaryQuery = useQuery({
    queryKey: workspaceQueryKeys.dashboardSummaryScope({ targetId: activeTargetId ?? ALL_TARGETS }),
    queryFn: () => getDashboardSummary(activeTargetId ? { targetId: activeTargetId } : {}),
    refetchInterval: scansQuery.activeScan ? 3000 : false,
  })

  const findingsQuery = useWorkspaceFindings({
    targetId: activeTargetId,
    completedOnly: false,
    scope: 'dashboard',
    queryOptions: {
      refetchInterval: scansQuery.activeScan ? 3000 : false,
    },
  })

  const summary = summaryQuery.data
  const findings = findingsQuery.findings
  const scopedRecentScans = activeTargetId
    ? allScans.filter((scan) => scan.target?.id === activeTargetId)
    : allScans
  const scopedTargets = activeTargetId
    ? targets.filter((target) => target.id === activeTargetId)
    : targets

  const normalizedFindings = findings
    .map((finding) => ({
      ...finding,
      severity: normalizeFindingSeverity(finding.severity),
      status: normalizeStatus(finding.status),
      title: sanitizeFindingTitle(finding.title),
    }))
    .sort(sortByLatest('createdAt', 'firstSeenAt'))

  const openFindings = normalizedFindings.filter((finding) => !isResolvedStatus(finding.status))
  const resolvedFindings = normalizedFindings.filter((finding) => isResolvedStatus(finding.status))

  const severityCounts = normalizedFindings.reduce(
    (accumulator, finding) => {
      const key = finding.severity.toLowerCase()
      accumulator[key] += 1
      return accumulator
    },
    { critical: 0, high: 0, medium: 0, low: 0, info: 0 },
  )

  const targetInventory = Array.from(
    scopedTargets.reduce((map, target) => {
      const targetFindings = normalizedFindings.filter((finding) => finding.target?.id === target.id)
      map.set(target.id, {
        id: target.id,
        name: target.name || `Target #${target.id}`,
        url: target.baseUrl || 'No base URL',
        verificationStatus: target.verificationStatus,
        scanCount: scopedRecentScans.filter((scan) => scan.target?.id === target.id).length,
        totalFindings: 0,
        openFindings: 0,
      })
      const current = map.get(target.id)
      current.totalFindings = targetFindings.length
      current.openFindings = targetFindings.filter((finding) => !isResolvedStatus(finding.status)).length
      return map
    }, new Map()).values(),
  ).sort((left, right) => {
    if (right.openFindings !== left.openFindings) {
      return right.openFindings - left.openFindings
    }
    if (right.totalFindings !== left.totalFindings) {
      return right.totalFindings - left.totalFindings
    }
    return right.scanCount - left.scanCount
  })

  const recentScans = scopedRecentScans.slice(0, 5).map((scan) => ({
    ...scan,
    displayTime: formatRelativeTime(scan.updatedAt || scan.completedAt || scan.createdAt),
  }))

  const latestFailingRows = openFindings.slice(0, 10).map((finding) => ({
    id: finding.id,
    status: normalizeStatus(finding.status) === 'PASS' ? 'Pass' : 'Fail',
    title: finding.title || getFindingCategoryLabel(finding),
    resource: finding.target?.name || finding.affectedUrl || finding.target?.baseUrl || 'Unknown target',
    severity: normalizeFindingSeverity(finding.severity),
    category: getFindingCategoryLabel(finding),
    time: formatDateTime(finding.createdAt || finding.firstSeenAt),
  }))

  const selectedTargetLabel =
    activeTargetId == null
      ? 'All targets'
      : targets.find((target) => target.id === activeTargetId)?.name || 'Selected target'

  return (
    <div className="page-shell">
      {scansQuery.isError ? (
        <section className="rounded-2xl border border-rose-500/16 bg-rose-500/8 px-4 py-3 text-sm text-rose-100">
          Dashboard scan status is temporarily unavailable. Findings and summary data are still shown where possible.
        </section>
      ) : null}

      {targetsQuery.isError ? (
        <section className="rounded-2xl border border-amber-500/16 bg-amber-500/8 px-4 py-3 text-sm text-amber-100">
          Target inventory is temporarily unavailable. Dashboard scope controls may be incomplete.
        </section>
      ) : null}

      {summaryQuery.isError ? (
        <section className="rounded-2xl border border-rose-500/16 bg-rose-500/8 px-4 py-3 text-sm text-rose-100">
          Dashboard summary refresh failed. Existing cards may be briefly stale.
        </section>
      ) : null}

      {findingsQuery.isError ? (
        <section className="rounded-2xl border border-rose-500/16 bg-rose-500/8 px-4 py-3 text-sm text-rose-100">
          Findings refresh failed. Latest live findings may be delayed until the next successful refresh.
        </section>
      ) : null}

      <section className="max-w-full">
        <DashboardFilter
          value={effectiveSelectedTargetId}
          onChange={setSelectedTargetId}
          selectedLabel={selectedTargetLabel}
          options={[
            { value: ALL_TARGETS, label: 'All targets' },
            ...targets.map((target) => ({
              value: String(target.id),
              label: target.name,
            })),
          ]}
        />
      </section>

      <div className="grid min-w-0 gap-6 xl:grid-cols-[minmax(0,312px)_minmax(0,1fr)] xl:items-stretch">
        <ThreatScore score={summary?.riskScore ?? 0} />

        <FindingsStatusChart
          openFindings={openFindings.length}
          resolvedFindings={resolvedFindings.length}
        />
      </div>

      <RiskSeverityChart counts={severityCounts} />
      <ResourceInventory items={targetInventory} />
      <LatestFindingsTable rows={latestFailingRows} onOpen={() => navigate(buildFindingsPath(activeTargetId))} />

      <div className="grid grid-cols-1 gap-6 xl:grid-cols-[minmax(0,1fr)_312px]">
        <FindingsTrendChart data={summary?.findingsTrend ?? []} />
        <RecentScans scans={recentScans} />
      </div>
    </div>
  )
}

function DashboardFilter({ value, onChange, options, selectedLabel }) {
  const controlWidth = `${Math.min(Math.max(selectedLabel.length + 6, 16), 34)}ch`

  return (
    <div className="surface-card inline-flex min-h-12 max-w-full items-center gap-4 rounded-xl px-4">
      <label className="shrink-0 text-sm text-zinc-500">Targets</label>
      <DarkSelect
        value={value}
        onChange={onChange}
        style={{ width: controlWidth }}
        className="max-w-full min-w-[12rem] border-none bg-transparent px-0"
        menuClassName="min-w-[12rem]"
        options={options}
      />
    </div>
  )
}

function LatestFindingsTable({ rows, onOpen }) {
  return (
    <section className="page-card">
      <div className="page-card-header flex-col gap-4 md:flex-row md:items-center md:justify-between">
        <div>
          <p className="text-[11px] font-semibold uppercase tracking-[0.24em] text-zinc-500">
            Latest New Failing Findings
          </p>
          <p className="mt-1.5 text-xs text-zinc-500">
            Showing the latest persisted findings arriving across your active workspace.
          </p>
        </div>

        <button onClick={onOpen} className="surface-button-primary h-8 shrink-0 px-4 text-sm">
          Open Findings
        </button>
      </div>

      {rows.length === 0 ? (
        <div className="surface-card-inner flex min-h-[240px] items-center justify-center">
          <p className="text-sm text-zinc-400">Live findings will appear here as they are persisted.</p>
        </div>
      ) : (
        <div className="min-w-0 overflow-x-auto overflow-y-hidden rounded-[16px] border border-white/6 bg-[#121010]">
          <table className="table-base table-auto min-w-[760px]">
            <thead className="table-head">
              <tr className="table-head-row">
                <th className="w-[96px] px-4 py-4 font-semibold">Status</th>
                <th className="min-w-[220px] px-4 py-4 font-semibold">Finding</th>
                <th className="w-[1%] min-w-[170px] max-w-[320px] px-4 py-4 font-semibold">Resource name</th>
                <th className="w-[118px] px-4 py-4 font-semibold">Severity</th>
                <th className="w-[140px] px-4 py-4 font-semibold">Category</th>
                <th className="w-[150px] px-4 py-4 font-semibold">Time</th>
              </tr>
            </thead>
            <tbody className="divide-y divide-white/6">
              {rows.map((row) => (
                <tr
                  key={row.id}
                  onClick={onOpen}
                  className="cursor-pointer transition-colors hover:bg-white/[0.02]"
                >
                  <td className="px-4 py-4">
                    <div className="flex items-center gap-2 text-rose-300">
                      <span className="h-2 w-2 rounded-full bg-rose-500" />
                      <span className="rounded-full border border-rose-500/30 px-2 py-0.5 text-sm">{row.status}</span>
                    </div>
                  </td>
                  <td className="px-4 py-4">
                    <div className="break-words text-[15px] leading-7 text-zinc-100">{row.title}</div>
                  </td>
                  <td className="px-4 py-4">
                    <span className="inline-flex min-w-0 max-w-[min(100%,22rem)] rounded-full border border-white/8 bg-white/[0.03] px-3 py-1.5 text-sm text-zinc-300">
                      <span className="truncate">{row.resource}</span>
                    </span>
                  </td>
                  <td className="px-4 py-4">
                    <div className={`flex items-center gap-2 text-sm ${getSeverityTone(row.severity)}`}>
                      <span className="h-3.5 w-3.5 rounded-[4px]" style={{ backgroundColor: getSeverityColor(row.severity) }} />
                      <span>{capitalize(row.severity.toLowerCase())}</span>
                    </div>
                  </td>
                  <td className="px-4 py-4 text-sm text-zinc-300">{row.category}</td>
                  <td className="px-4 py-4 text-sm text-zinc-400">{row.time}</td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      )}
    </section>
  )
}

function buildFindingsPath(targetId) {
  if (!targetId) {
    return '/findings'
  }

  const params = new URLSearchParams()
  params.set('targetId', String(targetId))
  return `/findings?${params.toString()}`
}

function normalizeStatus(status) {
  return String(status || 'OPEN').toUpperCase()
}

function isResolvedStatus(status) {
  return ['RESOLVED', 'CLOSED', 'FIXED', 'FALSE POSITIVE', 'PASS', 'COMPLETED'].includes(
    normalizeStatus(status),
  )
}

function sortByLatest(...keys) {
  return (left, right) => {
    const leftValue = keys.map((key) => left?.[key]).find(Boolean)
    const rightValue = keys.map((key) => right?.[key]).find(Boolean)
    return new Date(rightValue || 0).getTime() - new Date(leftValue || 0).getTime()
  }
}

function formatRelativeTime(value) {
  if (!value) {
    return 'No timestamp'
  }

  const timestamp = new Date(value).getTime()
  const diff = Date.now() - timestamp

  if (Number.isNaN(timestamp)) {
    return 'No timestamp'
  }
  if (diff < 60_000) {
    return 'Just now'
  }
  if (diff < 3_600_000) {
    return `${Math.floor(diff / 60_000)}m ago`
  }
  if (diff < 86_400_000) {
    return `${Math.floor(diff / 3_600_000)}h ago`
  }
  if (diff < 604_800_000) {
    return `${Math.floor(diff / 86_400_000)}d ago`
  }

  return new Date(value).toLocaleDateString()
}

function formatDateTime(value) {
  if (!value) {
    return 'No timestamp'
  }

  return new Date(value).toLocaleString()
}

function getSeverityTone(severity) {
  switch (normalizeFindingSeverity(severity)) {
    case 'CRITICAL':
      return 'text-rose-200'
    case 'HIGH':
      return 'text-orange-200'
    case 'MEDIUM':
      return 'text-amber-200'
    case 'INFO':
      return 'text-sky-200'
    default:
      return 'text-emerald-200'
  }
}

function getSeverityColor(severity) {
  switch (normalizeFindingSeverity(severity)) {
    case 'CRITICAL':
      return '#ff1d5c'
    case 'HIGH':
      return '#ff7b4f'
    case 'MEDIUM':
      return '#f8c65a'
    case 'INFO':
      return '#6aaeea'
    default:
      return '#9fd655'
  }
}

function capitalize(value) {
  return value.charAt(0).toUpperCase() + value.slice(1)
}

export default Dashboard
