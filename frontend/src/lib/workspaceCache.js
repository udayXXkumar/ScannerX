import { mergeScanIntoCollection, removeScanFromCollection } from './scanUtils'
import { workspaceQueryKeys } from './workspaceQueryKeys'

export function sortTargetsByName(targets) {
  return [...(targets ?? [])].sort((left, right) =>
    String(left?.name || '').localeCompare(String(right?.name || '')),
  )
}

export function mergeTargetIntoCollection(targets, nextTarget) {
  if (!nextTarget) {
    return targets ?? []
  }

  const existingTargets = Array.isArray(targets) ? targets : []
  const previousTarget = existingTargets.find((target) => target?.id === nextTarget.id)
  const mergedTarget = previousTarget ? { ...previousTarget, ...nextTarget } : nextTarget
  const withoutPrevious = existingTargets.filter((target) => target?.id !== nextTarget.id)

  return sortTargetsByName([mergedTarget, ...withoutPrevious])
}

export function removeTargetFromCollection(targets, targetId) {
  return (Array.isArray(targets) ? targets : []).filter((target) => target?.id !== targetId)
}

export function mergeScanIntoWorkspace(queryClient, scan) {
  queryClient.setQueryData(workspaceQueryKeys.scans, (currentScans) => mergeScanIntoCollection(currentScans, scan))
}

export function removeScanFromWorkspace(queryClient, scanId) {
  queryClient.setQueryData(workspaceQueryKeys.scans, (currentScans) => removeScanFromCollection(currentScans, scanId))
}

export function mergeTargetIntoWorkspace(queryClient, target) {
  queryClient.setQueryData(workspaceQueryKeys.targets, (currentTargets) => mergeTargetIntoCollection(currentTargets, target))
}

export function removeTargetFromWorkspace(queryClient, targetId) {
  queryClient.setQueryData(workspaceQueryKeys.targets, (currentTargets) => removeTargetFromCollection(currentTargets, targetId))
}

export function invalidateWorkspaceData(queryClient, options = {}) {
  const {
    includeFindings = false,
    includeReports = false,
    includeNotifications = false,
    includeSchedules = false,
    scanId = null,
  } = options

  queryClient.invalidateQueries({ queryKey: workspaceQueryKeys.scans })
  queryClient.invalidateQueries({ queryKey: workspaceQueryKeys.targets })
  queryClient.invalidateQueries({ queryKey: workspaceQueryKeys.dashboardSummary })

  if (scanId != null) {
    queryClient.invalidateQueries({ queryKey: workspaceQueryKeys.scan(scanId) })
    queryClient.invalidateQueries({ queryKey: workspaceQueryKeys.scanReport(scanId) })
    queryClient.invalidateQueries({ queryKey: workspaceQueryKeys.scanActivity(scanId) })
  }

  if (includeFindings) {
    queryClient.invalidateQueries({ queryKey: workspaceQueryKeys.findings })
  }

  if (includeReports) {
    queryClient.invalidateQueries({ queryKey: workspaceQueryKeys.reportSummary })
  }

  if (includeNotifications) {
    queryClient.invalidateQueries({ queryKey: workspaceQueryKeys.notifications })
    queryClient.invalidateQueries({ queryKey: workspaceQueryKeys.unreadNotifications })
  }

  if (includeSchedules) {
    queryClient.invalidateQueries({ queryKey: workspaceQueryKeys.schedules })
  }
}
