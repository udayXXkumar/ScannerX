const ALL_TARGETS = 'all-targets'
const ALL_SCANS = 'all-scans'

export const workspaceQueryKeys = {
  scans: ['scans'],
  targets: ['targets'],
  findings: ['findings'],
  dashboardSummary: ['dashboardSummary'],
  reportSummary: ['reportSummary'],
  notifications: ['notifications'],
  unreadNotifications: ['notifications', 'unreadCount'],
  schedules: ['schedules'],
  scan: (scanId) => ['scan', String(scanId)],
  scanReport: (scanId) => ['scan-report', String(scanId)],
  scanActivity: (scanId) => ['scan-activity', String(scanId)],
  findingsList: ({
    scope = 'list',
    targetId = ALL_TARGETS,
    scanId = ALL_SCANS,
    completedOnly = false,
  } = {}) => [
    'findings',
    scope,
    targetId ?? ALL_TARGETS,
    scanId ?? ALL_SCANS,
    completedOnly ? 'completed' : 'live',
  ],
  dashboardSummaryScope: ({ targetId = ALL_TARGETS, scanId = ALL_SCANS } = {}) => [
    'dashboardSummary',
    targetId ?? ALL_TARGETS,
    scanId ?? ALL_SCANS,
  ],
  reportSummaryScope: ({ targetId = ALL_TARGETS, scanId = ALL_SCANS } = {}) => [
    'reportSummary',
    targetId ?? ALL_TARGETS,
    scanId ?? ALL_SCANS,
  ],
}

export const workspaceScopeDefaults = {
  ALL_TARGETS,
  ALL_SCANS,
}
