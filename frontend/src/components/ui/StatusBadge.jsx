import { CheckCircle2, Clock, AlertCircle, XCircle, Pause } from 'lucide-react'
import { getScanStatusLabel, normalizeScanStatus } from '../../lib/scanUtils'

export default function StatusBadge({ status, className = '' }) {
  const statusMap = {
    'COMPLETED': { bg: 'bg-system-success/10', text: 'text-system-success', icon: CheckCircle2, label: 'Completed' },
    'RUNNING': { bg: 'bg-white/[0.04]', text: 'text-zinc-300', icon: Clock, label: 'In Progress' },
    'QUEUED': { bg: 'bg-white/[0.04]', text: 'text-slate-400', icon: Clock, label: 'Queued' },
    'PAUSING': { bg: 'bg-white/[0.04]', text: 'text-zinc-300', icon: Clock, label: 'Pausing' },
    'PAUSED': { bg: 'bg-amber-500/10', text: 'text-amber-200', icon: Pause, label: 'Paused' },
    'FAILED': { bg: 'bg-system-error/10', text: 'text-system-error', icon: XCircle, label: 'Failed' },
    'CANCELLED': { bg: 'bg-white/[0.04]', text: 'text-slate-400', icon: Pause, label: 'Cancelled' },
    'OPEN': { bg: 'bg-severity-high/10', text: 'text-severity-high', icon: AlertCircle },
    'Open': { bg: 'bg-severity-high/10', text: 'text-severity-high', icon: AlertCircle },
    'IN PROGRESS': { bg: 'bg-white/[0.04]', text: 'text-zinc-300', icon: Clock, label: 'In Progress' },
    'In Progress': { bg: 'bg-white/[0.04]', text: 'text-zinc-300', icon: Clock, label: 'In Progress' },
    'RESOLVED': { bg: 'bg-system-success/10', text: 'text-system-success', icon: CheckCircle2 },
    'Resolved': { bg: 'bg-system-success/10', text: 'text-system-success', icon: CheckCircle2 },
    'FALSE POSITIVE': { bg: 'bg-white/[0.04]', text: 'text-slate-300', icon: Pause, label: 'False Positive' },
    'False Positive': { bg: 'bg-white/[0.04]', text: 'text-slate-300', icon: Pause, label: 'False Positive' },
    'CLOSED': { bg: 'bg-system-success/10', text: 'text-system-success', icon: CheckCircle2, label: 'Closed' },
    'Closed': { bg: 'bg-system-success/10', text: 'text-system-success', icon: CheckCircle2, label: 'Closed' },
    'ACTIVE': { bg: 'bg-system-success/10', text: 'text-system-success', icon: CheckCircle2 },
    'Active': { bg: 'bg-system-success/10', text: 'text-system-success', icon: CheckCircle2 },
    'INACTIVE': { bg: 'bg-white/[0.04]', text: 'text-slate-400', icon: Pause },
    'Inactive': { bg: 'bg-white/[0.04]', text: 'text-slate-400', icon: Pause },
  }

  const normalizedStatus = normalizeScanStatus(status)
  const config = statusMap[normalizedStatus] || statusMap[status] || { bg: 'bg-white/[0.04]', text: 'text-slate-400', icon: Clock, label: getScanStatusLabel(status) }
  const Icon = config.icon

  return (
    <span className={`inline-flex items-center gap-2 rounded-md border border-white/8 px-3 py-1 font-semibold text-xs uppercase tracking-wider ${config.bg} ${config.text} ${className}`}>
      <Icon size={14} />
      {config.label || status}
    </span>
  )
}
