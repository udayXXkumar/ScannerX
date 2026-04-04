export function normalizeScanStatus(status) {
  const normalized = String(status || '').trim().toUpperCase()

  switch (normalized) {
    case 'IN_PROGRESS':
      return 'RUNNING'
    case 'PENDING':
      return 'QUEUED'
    default:
      return normalized || 'QUEUED'
  }
}

export function getScanStatusLabel(status) {
  switch (normalizeScanStatus(status)) {
    case 'QUEUED':
      return 'Queued'
    case 'RUNNING':
      return 'In Progress'
    case 'PAUSING':
      return 'Pausing'
    case 'PAUSED':
      return 'Paused'
    case 'COMPLETED':
      return 'Completed'
    case 'FAILED':
      return 'Failed'
    case 'CANCELLED':
      return 'Cancelled'
    default:
      return String(status || 'Queued')
  }
}

export function normalizeScanTier(tier) {
  const normalized = String(tier || '').trim().toUpperCase()

  switch (normalized) {
    case 'QUICK':
      return 'FAST'
    case 'COMPREHENSIVE':
      return 'DEEP'
    case 'STANDARD':
      return 'MEDIUM'
    case 'FAST':
    case 'MEDIUM':
    case 'DEEP':
      return normalized
    default:
      return 'MEDIUM'
  }
}

export function getScanTierLabel(tier) {
  switch (normalizeScanTier(tier)) {
    case 'FAST':
      return 'Fast'
    case 'DEEP':
      return 'Deep'
    default:
      return 'Medium'
  }
}

export function isActiveScanStatus(status) {
  const normalized = normalizeScanStatus(status)
  return normalized === 'QUEUED' || normalized === 'RUNNING' || normalized === 'PAUSING'
}

export function isCompletedScan(status) {
  return normalizeScanStatus(status) === 'COMPLETED'
}

export function isPausedScan(status) {
  return normalizeScanStatus(status) === 'PAUSED'
}

export function isFailedScan(status) {
  return normalizeScanStatus(status) === 'FAILED'
}

export function getScanDisplayName(scan) {
  if (scan?.target?.name?.trim()) {
    return scan.target.name.trim()
  }

  if (scan?.name?.trim()) {
    return scan.name.trim()
  }

  return scan?.target?.baseUrl || `Target #${scan?.id ?? ''}`
}

export function getScanRunLabel(scan) {
  const targetName = getScanDisplayName(scan)
  const timestamp = scan?.createdAt || scan?.updatedAt || scan?.completedAt
  const timeLabel = timestamp ? new Date(timestamp).toLocaleString() : `#${scan?.id ?? ''}`
  return `${targetName} · ${getScanStatusLabel(scan?.status)} · ${timeLabel}`
}
