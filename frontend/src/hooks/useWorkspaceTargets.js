import { useMemo } from 'react'
import { useQuery } from '@tanstack/react-query'
import { getTargets } from '../api/targetApi'
import { sortTargetsByName } from '../lib/workspaceCache'
import { workspaceQueryKeys } from '../lib/workspaceQueryKeys'

export const useWorkspaceTargets = (queryOptions = {}) => {
  const targetsQuery = useQuery({
    queryKey: workspaceQueryKeys.targets,
    queryFn: getTargets,
    ...queryOptions,
  })

  const targets = useMemo(
    () => sortTargetsByName(targetsQuery.data ?? []),
    [targetsQuery.data],
  )

  return {
    ...targetsQuery,
    targets,
  }
}
