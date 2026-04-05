import { useMemo } from 'react'
import { useQuery } from '@tanstack/react-query'
import { getScans } from '../api/scanApi'
import {
  buildLatestScanByTargetId,
  compareScansByMostRecent,
  getScanCountByTargetId,
  getTargetScanState,
  isActiveScanStatus,
} from '../lib/scanUtils'
import { workspaceQueryKeys } from '../lib/workspaceQueryKeys'

export const useWorkspaceScans = (queryOptions = {}) => {
  const scansQuery = useQuery({
    queryKey: workspaceQueryKeys.scans,
    queryFn: getScans,
    refetchInterval: (query) =>
      (query.state.data ?? []).some((scan) => isActiveScanStatus(scan.status)) ? 3000 : false,
    retry: (failureCount, error) => {
      const status = error?.response?.status
      if (status === 401 || status === 403) {
        return false
      }

      return failureCount < 1
    },
    ...queryOptions,
  })

  const scans = useMemo(
    () => [...(scansQuery.data ?? [])].sort(compareScansByMostRecent),
    [scansQuery.data],
  )

  const latestScanByTargetId = useMemo(
    () => buildLatestScanByTargetId(scans),
    [scans],
  )

  const activeScan = useMemo(
    () => scans.find((scan) => isActiveScanStatus(scan.status)) ?? null,
    [scans],
  )

  const getLatestScanForTarget = (targetId) => latestScanByTargetId.get(String(targetId)) ?? null
  const getScanCountForTarget = (targetId) => getScanCountByTargetId(scans, targetId)

  const resolveTargetScanState = (targetId) =>
    getTargetScanState({
      targetId,
      activeScan,
      latestScan: getLatestScanForTarget(targetId),
      isScanStateLoading: scansQuery.isPending && scans.length === 0,
      isScanStateError: scansQuery.isError && scans.length === 0,
    })

  return {
    ...scansQuery,
    scans,
    activeScan,
    hasActiveScan: Boolean(activeScan),
    latestScanByTargetId,
    getLatestScanForTarget,
    getScanCountForTarget,
    getTargetScanState: resolveTargetScanState,
  }
}
