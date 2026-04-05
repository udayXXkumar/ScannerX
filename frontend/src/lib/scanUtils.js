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

export function getScanBaseUrl(scan) {
  if (scan?.target?.baseUrl?.trim()) {
    return scan.target.baseUrl.trim()
  }

  if (scan?.target?.domain?.trim()) {
    return scan.target.domain.trim()
  }

  return 'No target URL'
}

export function getScanRunLabel(scan) {
  const targetName = getScanDisplayName(scan)
  const timestamp = scan?.createdAt || scan?.updatedAt || scan?.completedAt
  const timeLabel = timestamp ? new Date(timestamp).toLocaleString() : `#${scan?.id ?? ''}`
  return `${targetName} · ${getScanStatusLabel(scan?.status)} · ${timeLabel}`
}

export function compareScansByMostRecent(left, right) {
  return new Date(right?.updatedAt || right?.createdAt || right?.completedAt || 0)
    - new Date(left?.updatedAt || left?.createdAt || left?.completedAt || 0)
}

export function buildLatestScanByTargetId(scans) {
  return [...(scans ?? [])]
    .sort(compareScansByMostRecent)
    .reduce((map, scan) => {
      const targetId = scan?.target?.id
      if (targetId == null || map.has(String(targetId))) {
        return map
      }

      map.set(String(targetId), scan)
      return map
    }, new Map())
}

export function getTargetScanState({
  targetId,
  activeScan,
  latestScan,
  isScanStateLoading = false,
  isScanStateError = false,
}) {
  if (isScanStateLoading) {
    return { statusKey: 'CHECKING', label: 'Checking status', scan: latestScan ?? null }
  }

  if (isScanStateError && !latestScan) {
    return { statusKey: 'UNAVAILABLE', label: 'Status unavailable', scan: null }
  }

  const activeTargetId = activeScan?.target?.id

  if (activeTargetId && activeTargetId === targetId) {
    return { statusKey: 'IN_PROGRESS', label: 'In Progress', scan: latestScan ?? activeScan }
  }

  if (activeTargetId && activeTargetId !== targetId) {
    return { statusKey: 'LOCKED', label: 'Locked', scan: latestScan ?? null }
  }

  if (!latestScan) {
    return { statusKey: 'READY', label: 'Ready', scan: null }
  }

  const normalized = normalizeScanStatus(latestScan.status)
  if (normalized === 'QUEUED' || normalized === 'RUNNING' || normalized === 'PAUSING') {
    return { statusKey: 'IN_PROGRESS', label: 'In Progress', scan: latestScan }
  }
  if (normalized === 'PAUSED') {
    return { statusKey: 'PAUSED', label: 'Paused', scan: latestScan }
  }
  if (normalized === 'COMPLETED') {
    return { statusKey: 'COMPLETED', label: 'Completed', scan: latestScan }
  }
  if (normalized === 'FAILED' || normalized === 'CANCELLED') {
    return { statusKey: 'FAILED', label: 'Failed', scan: latestScan }
  }

  return { statusKey: 'READY', label: getScanStatusLabel(latestScan.status), scan: latestScan }
}

export function mergeScanIntoCollection(scans, nextScan) {
  if (!nextScan) {
    return scans ?? []
  }

  const existingScans = Array.isArray(scans) ? scans : []
  const previousScan = existingScans.find((scan) => scan?.id === nextScan.id)
  const mergedScan = previousScan
    ? {
        ...previousScan,
        ...nextScan,
        target: {
          ...(previousScan.target ?? {}),
          ...(nextScan.target ?? {}),
        },
      }
    : nextScan
  const withoutPrevious = existingScans.filter((scan) => scan?.id !== nextScan.id)
  return [mergedScan, ...withoutPrevious].sort(compareScansByMostRecent)
}

export function removeScanFromCollection(scans, scanId) {
  return (Array.isArray(scans) ? scans : []).filter((scan) => scan?.id !== scanId)
}

export function getScanCountByTargetId(scans, targetId) {
  return (Array.isArray(scans) ? scans : []).filter((scan) => scan?.target?.id === targetId).length
}
