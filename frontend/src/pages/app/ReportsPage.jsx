import { useState } from 'react'
import { Download, FileText } from 'lucide-react'
import { useQuery } from '@tanstack/react-query'
import { getReportSummary, downloadReportCsv, downloadReportJson, downloadReportPdf } from '../../api/reportApi'
import { getTargets } from '../../api/targetApi'
import SeverityBadge from '../../components/ui/SeverityBadge'
import StatusBadge from '../../components/ui/StatusBadge'
import { sanitizeFindingTitle } from '../../lib/findingUtils'

const ALL_TARGETS = 'all-targets'

const ReportsPage = () => {
  const [selectedTargetId, setSelectedTargetId] = useState(ALL_TARGETS)

  const { data: targets = [] } = useQuery({
    queryKey: ['targets'],
    queryFn: getTargets,
  })

  const targetId = selectedTargetId === ALL_TARGETS ? undefined : Number(selectedTargetId)

  const { data: summary, isLoading } = useQuery({
    queryKey: ['reportSummary', targetId ?? 'all-targets'],
    queryFn: () => getReportSummary({ targetId }),
  })

  const handleDownload = async (type) => {
    const params = { targetId }

    if (type === 'csv') {
      await downloadReportCsv(params)
      return
    }

    if (type === 'json') {
      await downloadReportJson(params)
      return
    }

    await downloadReportPdf(params)
  }

  return (
    <div className="page-shell">
      <div className="page-header">
        <div className="page-header-copy">
          <h2 className="page-title">Reports</h2>
          <p className="page-subtitle">Export completed scan results with scope-aware summaries and findings.</p>
        </div>
        <div className="flex flex-wrap items-center gap-3">
          <button onClick={() => handleDownload('pdf')} className="surface-button-primary h-11 px-5 text-sm">
            <Download size={16} />
            PDF
          </button>
          <button
            onClick={() => handleDownload('csv')}
            className="rounded-xl border border-white/10 px-4 py-2.5 text-sm font-medium text-slate-300 transition-colors hover:border-white/16 hover:bg-white/[0.04] hover:text-white"
          >
            CSV
          </button>
          <button
            onClick={() => handleDownload('json')}
            className="rounded-xl border border-white/10 px-4 py-2.5 text-sm font-medium text-slate-300 transition-colors hover:border-white/16 hover:bg-white/[0.04] hover:text-white"
          >
            JSON
          </button>
        </div>
      </div>

      <section className="section-filter-shell">
        <select
          value={selectedTargetId}
          onChange={(event) => setSelectedTargetId(event.target.value)}
          className="filter-control"
        >
          <option value={ALL_TARGETS}>All targets</option>
          {targets.map((target) => (
            <option key={target.id} value={String(target.id)}>
              {target.name}
            </option>
          ))}
        </select>

        <div className="surface-card flex min-h-12 items-center gap-3 px-4">
          <FileText size={18} className="text-prowler-green" />
          <span className="truncate text-sm text-zinc-200">{summary?.scopeLabel || 'All targets'}</span>
        </div>
      </section>

      <div className="grid gap-4 sm:grid-cols-2 xl:grid-cols-4">
        <StatCard label="Findings" value={summary?.totalFindings ?? 0} />
        <StatCard label="Open" value={summary?.openFindings ?? 0} />
        <StatCard label="Resolved" value={summary?.resolvedFindings ?? 0} />
        <StatCard label="Runs" value={summary?.totalScans ?? 0} />
      </div>

      <div className="page-card">
        <div className="page-card-header">
          <div>
            <p className="text-[11px] font-semibold uppercase tracking-[0.24em] text-zinc-500">Executive Summary</p>
            <h3 className="mt-3 text-[1.45rem] font-semibold text-white">{summary?.scopeLabel || 'All targets'}</h3>
          </div>
        </div>

        <div className="grid gap-4 sm:grid-cols-2 xl:grid-cols-5">
          <SeverityCard label="Critical" value={summary?.criticalFindings ?? 0} tone="critical" />
          <SeverityCard label="High" value={summary?.highFindings ?? 0} tone="high" />
          <SeverityCard label="Medium" value={summary?.mediumFindings ?? 0} tone="medium" />
          <SeverityCard label="Low" value={summary?.lowFindings ?? 0} tone="low" />
          <SeverityCard label="Info" value={summary?.informationalFindings ?? 0} tone="info" />
        </div>
      </div>

      <div className="table-shell">
        <div className="border-b border-white/8 bg-white/[0.03] px-4 py-4">
          <h3 className="text-lg font-medium text-white">Findings Preview</h3>
        </div>

        <div className="table-scroll">
          {isLoading ? (
            <div className="flex h-full items-center justify-center text-slate-400">Loading report data...</div>
          ) : !summary?.findings?.length ? (
            <div className="empty-state">
              <div className="empty-state-panel">
                <div className="empty-state-icon">
                  <FileText size={34} />
                </div>
                <p className="text-slate-400">No completed-scan findings available for this scope.</p>
              </div>
            </div>
          ) : (
            <table className="table-base table-fixed">
              <thead className="table-head">
                <tr className="table-head-row">
                  <th className="w-[18%] px-6 py-4 font-semibold">Target</th>
                  <th className="w-[34%] px-6 py-4 font-semibold">Finding</th>
                  <th className="w-[12%] px-6 py-4 font-semibold">Severity</th>
                  <th className="w-[12%] px-6 py-4 font-semibold">Status</th>
                  <th className="w-[24%] px-6 py-4 font-semibold">URL</th>
                </tr>
              </thead>
              <tbody className="divide-y divide-white/8">
                {summary.findings.map((finding) => (
                  <tr key={finding.id} className="transition-colors hover:bg-white/[0.03]">
                    <td className="px-6 py-4 text-sm text-slate-300">
                      <div className="table-cell-wrap">{finding.target?.name || summary.targetName}</div>
                    </td>
                    <td className="px-6 py-4">
                      <div className="table-cell-wrap leading-6 text-white line-clamp-2">
                        {sanitizeFindingTitle(finding.title)}
                      </div>
                    </td>
                    <td className="px-6 py-4">
                      <SeverityBadge severity={finding.severity} />
                    </td>
                    <td className="px-6 py-4">
                      <StatusBadge status={finding.status} />
                    </td>
                    <td className="px-6 py-4">
                      <div className="table-url">{finding.affectedUrl || 'N/A'}</div>
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          )}
        </div>
      </div>
    </div>
  )
}

const StatCard = ({ label, value }) => (
  <div className="metric-card">
    <p className="metric-card-label">{label}</p>
    <p className="metric-card-value">{value}</p>
  </div>
)

const SeverityCard = ({ label, value, tone }) => {
  const toneClass =
    tone === 'critical'
      ? 'text-severity-critical'
      : tone === 'high'
        ? 'text-severity-high'
        : tone === 'medium'
          ? 'text-severity-medium'
          : tone === 'info'
            ? 'text-sky-300'
            : 'text-severity-low'

  return (
    <div className="surface-card-inner px-4 py-4">
      <p className="text-sm text-slate-400">{label}</p>
      <p className={`mt-2 text-2xl font-bold ${toneClass}`}>{value}</p>
    </div>
  )
}

export default ReportsPage
