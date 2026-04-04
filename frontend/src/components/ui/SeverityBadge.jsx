export default function SeverityBadge({ severity, className = '' }) {
  const severityMap = {
    'CRITICAL': { bg: 'bg-severity-critical/10', text: 'text-severity-critical', border: 'border-severity-critical/30' },
    'critical': { bg: 'bg-severity-critical/10', text: 'text-severity-critical', border: 'border-severity-critical/30' },
    'HIGH': { bg: 'bg-severity-high/10', text: 'text-severity-high', border: 'border-severity-high/30' },
    'high': { bg: 'bg-severity-high/10', text: 'text-severity-high', border: 'border-severity-high/30' },
    'MEDIUM': { bg: 'bg-severity-medium/10', text: 'text-severity-medium', border: 'border-severity-medium/30' },
    'medium': { bg: 'bg-severity-medium/10', text: 'text-severity-medium', border: 'border-severity-medium/30' },
    'LOW': { bg: 'bg-severity-low/10', text: 'text-severity-low', border: 'border-severity-low/30' },
    'low': { bg: 'bg-severity-low/10', text: 'text-severity-low', border: 'border-severity-low/30' },
    'INFO': { bg: 'bg-sky-400/10', text: 'text-sky-300', border: 'border-sky-400/30' },
    'info': { bg: 'bg-sky-400/10', text: 'text-sky-300', border: 'border-sky-400/30' },
    'INFORMATIONAL': { bg: 'bg-sky-400/10', text: 'text-sky-300', border: 'border-sky-400/30' },
    'informational': { bg: 'bg-sky-400/10', text: 'text-sky-300', border: 'border-sky-400/30' },
  }

  const config = severityMap[severity] || severityMap['low']

  return (
    <span className={`inline-flex items-center px-3 py-1 rounded-md font-semibold text-xs uppercase tracking-wider border ${config.bg} ${config.text} ${config.border} ${className}`}>
      {severity}
    </span>
  )
}
