import { useMemo } from 'react'
import { useQuery } from '@tanstack/react-query'
import { getFindings } from '../api/findingApi'
import { workspaceQueryKeys, workspaceScopeDefaults } from '../lib/workspaceQueryKeys'

export const useWorkspaceFindings = ({
  targetId,
  scanId,
  completedOnly = false,
  scope = 'list',
  queryOptions = {},
} = {}) => {
  const findingsQuery = useQuery({
    queryKey: workspaceQueryKeys.findingsList({
      scope,
      targetId: targetId ?? workspaceScopeDefaults.ALL_TARGETS,
      scanId: scanId ?? workspaceScopeDefaults.ALL_SCANS,
      completedOnly,
    }),
    queryFn: () => getFindings({ targetId, scanId, completedOnly }),
    ...queryOptions,
  })

  const findings = useMemo(
    () => findingsQuery.data ?? [],
    [findingsQuery.data],
  )

  return {
    ...findingsQuery,
    findings,
  }
}
