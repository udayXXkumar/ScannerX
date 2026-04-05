import { useEffect, useState } from 'react'
import { createPortal } from 'react-dom'
import { Target, Search, Plus, X, CheckCircle2, Shield, Edit2, Trash2, Globe, Play, AlertTriangle, Clock3, CheckCircle, XCircle, PauseCircle, Eye } from 'lucide-react'
import { useMutation, useQueryClient } from '@tanstack/react-query'
import { useNavigate } from 'react-router-dom'
import { isConfirmedUnauthorizedError } from '../../api/axios'
import { createTarget, updateTarget, deleteTarget } from '../../api/targetApi'
import { createScan, resumeScan } from '../../api/scanApi'
import { createSchedule } from '../../api/scheduleApi'
import { getScanDisplayName } from '../../lib/scanUtils'
import { beginAppProgress, endAppProgress } from '../../lib/appNavigation'
import { useWorkspaceScans } from '../../hooks/useWorkspaceScans'
import { useWorkspaceTargets } from '../../hooks/useWorkspaceTargets'
import DarkSelect from '../../components/ui/DarkSelect'
import {
  invalidateWorkspaceData,
  mergeScanIntoWorkspace,
  mergeTargetIntoWorkspace,
  removeTargetFromWorkspace,
} from '../../lib/workspaceCache'
import { workspaceQueryKeys } from '../../lib/workspaceQueryKeys'

const TargetsList = () => {
  const navigate = useNavigate()
  const queryClient = useQueryClient()
  const [isModalOpen, setIsModalOpen] = useState(false)
  const [scheduleModalOpen, setScheduleModalOpen] = useState(false)
  const [targetToSchedule, setTargetToSchedule] = useState(null)
  const [editModalOpen, setEditModalOpen] = useState(false)
  const [targetToEdit, setTargetToEdit] = useState(null)
  const [launchingTargetId, setLaunchingTargetId] = useState(null)
  const [searchQuery, setSearchQuery] = useState('')
  const [scheduleForm, setScheduleForm] = useState({
    cronExpression: '0 0 * * *',
  })
  const [formData, setFormData] = useState({
    name: '',
    baseUrl: '',
    defaultTier: 'MEDIUM',
    timeoutsEnabled: true,
  })

  const { targets, isLoading, isError, error } = useWorkspaceTargets()

  const {
    scans,
    activeScan,
    hasActiveScan,
    getScanCountForTarget,
    getTargetScanState,
    isPending: isScanStateLoading,
    isError: isScanStateError,
  } = useWorkspaceScans()

  const createMutation = useMutation({
    mutationFn: createTarget,
    onSuccess: (target) => {
      mergeTargetIntoWorkspace(queryClient, target)
      queryClient.invalidateQueries({ queryKey: workspaceQueryKeys.targets })
      queryClient.invalidateQueries({ queryKey: workspaceQueryKeys.dashboardSummary })
      queryClient.invalidateQueries({ queryKey: workspaceQueryKeys.reportSummary })
      setIsModalOpen(false)
      setFormData({ name: '', baseUrl: '', defaultTier: 'MEDIUM', timeoutsEnabled: true })
    },
  })

  const scheduleMutation = useMutation({
    mutationFn: createSchedule,
    onSuccess: () => {
      setScheduleModalOpen(false)
      setTargetToSchedule(null)
      queryClient.invalidateQueries({ queryKey: workspaceQueryKeys.targets })
      queryClient.invalidateQueries({ queryKey: workspaceQueryKeys.schedules })
    },
  })

  const updateMutation = useMutation({
    mutationFn: ({ id, data }) => updateTarget(id, data),
    onSuccess: (target) => {
      mergeTargetIntoWorkspace(queryClient, target)
      queryClient.invalidateQueries({ queryKey: workspaceQueryKeys.targets })
      queryClient.invalidateQueries({ queryKey: workspaceQueryKeys.dashboardSummary })
      queryClient.invalidateQueries({ queryKey: workspaceQueryKeys.reportSummary })
      setEditModalOpen(false)
      setTargetToEdit(null)
    },
  })

  const deleteMutation = useMutation({
    mutationFn: deleteTarget,
    onSuccess: (_, targetId) => {
      removeTargetFromWorkspace(queryClient, targetId)
      invalidateWorkspaceData(queryClient, {
        includeFindings: true,
        includeReports: true,
        includeNotifications: true,
      })
    },
  })

  const scanMutation = useMutation({
    mutationFn: createScan,
    onMutate: (variables) => {
      setLaunchingTargetId(variables.target.id)
      beginAppProgress('scan-launch')
    },
    onSuccess: (scan) => {
      mergeScanIntoWorkspace(queryClient, scan)
      queryClient.invalidateQueries({ queryKey: workspaceQueryKeys.scans })
      queryClient.invalidateQueries({ queryKey: workspaceQueryKeys.targets })
      queryClient.invalidateQueries({ queryKey: workspaceQueryKeys.dashboardSummary })
      navigate(`/scans/${scan.id}`)
    },
    onError: (error) => {
      if (isConfirmedUnauthorizedError(error)) {
        return
      }
      window.alert(getMutationErrorMessage(error, 'Unable to start scan right now.'))
    },
    onSettled: () => {
      setLaunchingTargetId(null)
      endAppProgress('scan-launch')
    },
  })

  const resumeMutation = useMutation({
    mutationFn: resumeScan,
    onMutate: () => {
      beginAppProgress('scan-resume')
    },
    onSuccess: (scan) => {
      mergeScanIntoWorkspace(queryClient, scan)
      queryClient.invalidateQueries({ queryKey: workspaceQueryKeys.scans })
      queryClient.invalidateQueries({ queryKey: workspaceQueryKeys.targets })
      queryClient.invalidateQueries({ queryKey: workspaceQueryKeys.dashboardSummary })
      navigate(`/scans/${scan.id}`)
    },
    onSettled: () => {
      endAppProgress('scan-resume')
    },
  })

  const handleSubmit = (event) => {
    event.preventDefault()
    createMutation.mutate(formData)
  }

  const handleEditSubmit = (event) => {
    event.preventDefault()
    updateMutation.mutate({ id: targetToEdit.id, data: targetToEdit })
  }

  const handleScheduleClick = (target) => {
    setTargetToSchedule(target)
    setScheduleModalOpen(true)
  }

  const handleEditClick = (target) => {
    setTargetToEdit({
      ...target,
      defaultTier: target.defaultTier || 'MEDIUM',
      timeoutsEnabled: target.timeoutsEnabled ?? true,
    })
    setEditModalOpen(true)
  }

  const handleDeleteClick = (id) => {
    if (window.confirm('Are you sure you want to delete this target? All related scans will be lost.')) {
      deleteMutation.mutate(id)
    }
  }

  const submitSchedule = (event) => {
    event.preventDefault()
    scheduleMutation.mutate({
      targetId: targetToSchedule.id,
      cronExpression: scheduleForm.cronExpression,
    })
  }

  const activeTargetId = activeScan?.target?.id
  const activeScanMessage = 'A scan is already running. Let it complete or cancel it before starting another.'
  const scanStateUnavailableMessage = 'Latest scan state is unavailable. Refresh or sign in again before launching another scan.'
  const isTargetStatusPending = isScanStateLoading && scans.length === 0

  const readyCount =
    isTargetStatusPending || isScanStateError
      ? 0
      : targets.filter((target) => getTargetScanState(target.id).statusKey === 'READY').length
  const trackedDomains = new Set(targets.map((target) => target.domain).filter(Boolean)).size
  const filteredTargets = targets.filter((target) => {
    const query = searchQuery.trim().toLowerCase()
    if (!query) {
      return true
    }

    return [
      target.name,
      target.baseUrl,
      target.projectGroupName,
      target.tags,
    ].some((value) => String(value || '').toLowerCase().includes(query))
  })

  return (
    <div className="page-shell relative">
      <div className="space-y-4">
        <div className="page-header relative z-10">
          <div className="page-header-copy">
            <h2 className="page-title">Targets</h2>
            <p className="page-subtitle">Manage scanning targets and launch tools directly against each URL.</p>
          </div>

          <button
            type="button"
            onClick={() => {
              createMutation.reset()
              setIsModalOpen(true)
            }}
            className="surface-button-primary h-12 px-6 shadow-[0_14px_36px_rgba(96,223,178,0.18)]"
          >
            <Plus size={20} />
            Add Target
          </button>
        </div>

        <div className="page-stats-grid">
          <StatCard label="Total Targets" value={targets.length} tone="mint" icon={Target} />
          <StatCard label="Ready to Scan" value={readyCount} tone="emerald" icon={CheckCircle2} />
          <StatCard label="Tracked Domains" value={trackedDomains} tone="cyan" icon={Globe} />
        </div>
      </div>

      <div className="table-shell">
        <div className="table-toolbar">
          <div className="search-control w-full max-w-xs">
            <Search className="w-4 h-4 text-slate-400 mr-2" />
            <input
              type="text"
              placeholder="Filter targets..."
              value={searchQuery}
              onChange={(event) => setSearchQuery(event.target.value)}
              className="w-full border-none bg-transparent text-sm text-slate-200 outline-none placeholder:text-slate-500"
            />
          </div>
        </div>

        {hasActiveScan ? (
          <div className="border-b border-amber-500/12 bg-amber-500/6 px-4 py-3">
            <div className="flex items-center gap-3 text-sm text-amber-100">
              <AlertTriangle className="h-4 w-4 shrink-0 text-amber-300" />
              <span>
                {activeScanMessage} Active scan: <span className="font-semibold">{getScanDisplayName(activeScan)}</span>
              </span>
            </div>
          </div>
        ) : null}

        {isTargetStatusPending ? (
          <div className="border-b border-white/8 bg-white/[0.03] px-4 py-3">
            <div className="flex items-center gap-3 text-sm text-slate-300">
              <Clock3 className="h-4 w-4 shrink-0 text-slate-400" />
              <span>Checking latest scan state before enabling launch actions.</span>
            </div>
          </div>
        ) : null}

        {isScanStateError ? (
          <div className="border-b border-rose-500/16 bg-rose-500/8 px-4 py-3">
            <div className="flex items-center gap-3 text-sm text-rose-100">
              <AlertTriangle className="h-4 w-4 shrink-0 text-rose-300" />
              <span>{scanStateUnavailableMessage}</span>
            </div>
          </div>
        ) : null}

        <div className="table-scroll">
          {isLoading ? (
            <div className="flex items-center justify-center h-full text-slate-400">Loading targets...</div>
          ) : isError ? (
            <div className="empty-state">
              <div className="empty-state-panel">
                <div className="empty-state-icon text-amber-300">
                  <AlertTriangle size={34} />
                </div>
                <p className="text-slate-300">Unable to load targets right now.</p>
                <p className="mt-2 text-sm text-slate-500">
                  {getMutationErrorMessage(error, 'Please refresh and try again.')}
                </p>
              </div>
            </div>
          ) : filteredTargets.length === 0 ? (
            <div className="empty-state">
              <div className="empty-state-panel">
                <div className="empty-state-icon text-zinc-400">
                  <Target size={34} />
                </div>
                <p className="text-slate-400">{searchQuery ? 'No targets match your filter.' : 'No targets yet. Create one to get started.'}</p>
              </div>
            </div>
          ) : (
            <div className="divide-y divide-white/8">
              {filteredTargets.map((target) => {
                const targetScansCount = getScanCountForTarget(target.id)
                const targetStatus = getTargetScanState(target.id)
                const isCurrentActiveTarget = activeTargetId === target.id
                const scanStateUnavailable = targetStatus.statusKey === 'CHECKING' || targetStatus.statusKey === 'UNAVAILABLE'
                const canResumePausedScan =
                  targetStatus.statusKey === 'PAUSED' &&
                  !hasActiveScan &&
                  !scanStateUnavailable &&
                  targetStatus.scan?.id
                return (
                <div
                  key={target.id}
                  className="group border-b border-white/8 p-6 transition-colors last:border-0 hover:bg-white/[0.025]"
                >
                  <div className="flex flex-col gap-6 xl:flex-row xl:items-start xl:justify-between">
                    <div className="flex min-w-0 flex-1 items-start gap-4">
                      <div className="flex h-12 w-12 flex-shrink-0 items-center justify-center rounded-xl border border-white/10 bg-white/[0.04] text-[#60dfb2] backdrop-blur-xl transition-colors group-hover:border-[#60dfb2]/30">
                        <Target className="w-6 h-6 text-prowler-green" />
                      </div>

                      <div className="flex-1 min-w-0">
                        <div className="mb-2 flex flex-wrap items-center gap-3">
                          <h3 className="font-semibold text-white text-lg group-hover:text-prowler-green transition-colors truncate">
                            {target.name}
                          </h3>
                          {target.projectGroupName ? (
                            <span className="rounded-full border border-[#60dfb2]/25 bg-[#60dfb2]/10 px-3 py-1 text-xs font-semibold uppercase tracking-wider text-[#7be7c0]">
                              {target.projectGroupName}
                            </span>
                          ) : null}
                        </div>

                        <div className="flex flex-col gap-3 lg:flex-row lg:items-center lg:gap-4">
                          <p className="break-all text-sm text-slate-400 font-mono">{target.baseUrl}</p>
                          {target.tags ? (
                            <div className="flex flex-wrap gap-2">
                              {target.tags.split(',').map((tag) => (
                                <span
                                  key={tag}
                                  className="rounded-md border border-white/8 bg-white/[0.03] px-2 py-1 text-xs font-medium text-slate-400"
                                >
                                  #{tag.trim()}
                                </span>
                              ))}
                            </div>
                          ) : null}
                        </div>
                      </div>
                    </div>

                    <div className="flex flex-wrap items-center gap-5 xl:ml-6 xl:justify-end">
                      <div className="text-left xl:text-right">
                        <p className="text-xs font-medium text-slate-500 uppercase">Scans</p>
                        <p className="text-2xl font-bold text-white mt-1">{targetScansCount}</p>
                      </div>

                      <div className="text-left xl:text-right">
                        <p className="text-xs font-medium text-slate-500 uppercase">Status</p>
                        <div className="mt-1">
                          <TargetStatusPill targetStatus={targetStatus} />
                        </div>
                      </div>

                      <div className="flex flex-wrap items-center gap-2">
                        {isCurrentActiveTarget ? (
                          <button
                            type="button"
                            onClick={() => navigate(`/findings?targetId=${target.id}`)}
                            className="surface-button-primary h-10 px-4 text-sm"
                            title="View Findings"
                          >
                            <Eye size={15} />
                            View Findings
                          </button>
                        ) : canResumePausedScan ? (
                          <button
                            type="button"
                            onClick={() => resumeMutation.mutate(targetStatus.scan.id)}
                            disabled={resumeMutation.isPending || scanStateUnavailable}
                            className="surface-button-primary h-10 px-4 text-sm disabled:cursor-not-allowed disabled:opacity-60"
                            title={scanStateUnavailable ? scanStateUnavailableMessage : 'Resume Scan'}
                          >
                            <Play size={15} className="fill-current" />
                            {resumeMutation.isPending ? 'Resuming...' : 'Resume Scan'}
                          </button>
                        ) : (
                          <button
                            type="button"
                            onClick={() =>
                              scanMutation.mutate({
                                name: target.name,
                                target: { id: target.id, name: target.name },
                              })
                            }
                            disabled={
                              scanStateUnavailable ||
                              hasActiveScan ||
                              (scanMutation.isPending && launchingTargetId === target.id)
                            }
                            className="surface-button-primary h-10 px-4 text-sm disabled:cursor-not-allowed disabled:opacity-60"
                            title={
                              scanStateUnavailable
                                ? scanStateUnavailableMessage
                                : hasActiveScan
                                  ? activeScanMessage
                                  : 'Start Scan'
                            }
                          >
                            <Play size={15} className="fill-current" />
                            {scanStateUnavailable
                              ? targetStatus.statusKey === 'CHECKING'
                                ? 'Checking...'
                                : 'Unavailable'
                              : hasActiveScan
                                ? 'Scan Locked'
                                : 'Scan Now'}
                          </button>
                        )}
                        <button
                          type="button"
                          onClick={() => handleScheduleClick(target)}
                          className="rounded-xl border border-transparent p-2 text-slate-400 transition-colors hover:border-white/8 hover:bg-white/[0.04] hover:text-prowler-green"
                          title="Schedule Scans"
                        >
                          <Shield size={18} />
                        </button>
                        <button
                          type="button"
                          onClick={() => handleEditClick(target)}
                          className="rounded-xl border border-transparent p-2 text-slate-400 transition-colors hover:border-white/8 hover:bg-white/[0.04] hover:text-prowler-green"
                          title="Edit Target"
                        >
                          <Edit2 size={18} />
                        </button>
                        <button
                          type="button"
                          onClick={() => handleDeleteClick(target.id)}
                          className="rounded-xl border border-transparent p-2 text-slate-400 transition-colors hover:border-white/8 hover:bg-white/[0.04] hover:text-severity-high"
                          title="Delete Target"
                        >
                          <Trash2 size={18} />
                        </button>
                      </div>
                    </div>
                  </div>
                </div>
              )})}
            </div>
          )}
        </div>
      </div>

      {isModalOpen ? (
        <CreateTargetModal
          onClose={() => {
            createMutation.reset()
            setIsModalOpen(false)
          }}
          formData={formData}
          setFormData={setFormData}
          onSubmit={handleSubmit}
          isLoading={createMutation.isPending}
          errorMessage={getMutationErrorMessage(createMutation.error, 'Unable to add target right now.')}
        />
      ) : null}

      {scheduleModalOpen && targetToSchedule ? (
        <ScheduleModal
          target={targetToSchedule}
          scheduleForm={scheduleForm}
          setScheduleForm={setScheduleForm}
          onSubmit={submitSchedule}
          isLoading={scheduleMutation.isPending}
          onClose={() => setScheduleModalOpen(false)}
        />
      ) : null}

      {editModalOpen && targetToEdit ? (
        <EditTargetModal
          target={targetToEdit}
          setTarget={setTargetToEdit}
          onSubmit={handleEditSubmit}
          isLoading={updateMutation.isPending}
          errorMessage={getMutationErrorMessage(updateMutation.error, 'Unable to save target changes right now.')}
          onClose={() => setEditModalOpen(false)}
        />
      ) : null}
    </div>
  )
}

const TargetStatusPill = ({ targetStatus }) => {
  const tone =
    targetStatus.statusKey === 'IN_PROGRESS'
      ? 'border-white/12 bg-white/[0.04] text-zinc-300'
      : targetStatus.statusKey === 'CHECKING'
        ? 'border-white/10 bg-white/[0.03] text-slate-300'
        : targetStatus.statusKey === 'UNAVAILABLE'
          ? 'border-rose-500/25 bg-rose-500/10 text-rose-200'
      : targetStatus.statusKey === 'PAUSED'
        ? 'border-amber-500/25 bg-amber-500/10 text-amber-200'
        : targetStatus.statusKey === 'LOCKED'
          ? 'border-white/10 bg-white/[0.03] text-slate-400'
          : targetStatus.statusKey === 'COMPLETED'
            ? 'border-system-success/30 bg-system-success/10 text-system-success'
            : targetStatus.statusKey === 'FAILED'
              ? 'border-rose-500/25 bg-rose-500/10 text-rose-200'
              : 'border-system-success/30 bg-system-success/10 text-system-success'

  const Icon =
    targetStatus.statusKey === 'IN_PROGRESS'
      ? Clock3
      : targetStatus.statusKey === 'CHECKING'
        ? Clock3
        : targetStatus.statusKey === 'UNAVAILABLE'
          ? AlertTriangle
      : targetStatus.statusKey === 'PAUSED'
        ? PauseCircle
        : targetStatus.statusKey === 'LOCKED'
          ? Clock3
          : targetStatus.statusKey === 'COMPLETED'
            ? CheckCircle
            : targetStatus.statusKey === 'FAILED'
              ? XCircle
              : CheckCircle2

  return (
    <span className={`inline-flex items-center gap-2 rounded-full border px-3 py-1 text-sm font-semibold backdrop-blur-xl ${tone}`}>
      <Icon size={14} />
      {targetStatus.label}
    </span>
  )
}

const StatCard = ({ label, value, tone, icon }) => {
  const Icon = icon
  const toneClasses =
    tone === 'emerald'
      ? 'border-emerald-400/16 bg-[linear-gradient(135deg,rgba(27,37,31,0.88),rgba(14,19,18,0.9))] text-emerald-200'
      : tone === 'cyan'
        ? 'border-cyan-300/14 bg-[linear-gradient(135deg,rgba(24,35,45,0.88),rgba(14,18,23,0.9))] text-cyan-100'
        : 'border-[#60dfb2]/16 bg-[linear-gradient(135deg,rgba(22,34,31,0.88),rgba(13,17,16,0.9))] text-[#7be7c0]'

  return (
    <div className="metric-card">
      <div className="metric-card-body">
        <div>
          <p className="metric-card-label">{label}</p>
          <p className="metric-card-value">{value}</p>
        </div>
        <div className={`metric-icon-shell ${toneClasses}`}>
          <Icon size={24} />
        </div>
      </div>
    </div>
  )
}

const CreateTargetModal = ({ onClose, formData, setFormData, onSubmit, isLoading, errorMessage }) => (
  <ModalShell onClose={onClose}>
    <FormDialog
      formId="create-target-form"
      onClose={onClose}
      eyebrow="Create Scan Target"
      title="Add a URL once and keep it ready for every scan."
      description="Create a clean target record so launches, schedules, and findings all stay attached to the same destination."
      summaryTitle="What gets saved"
      summaryItems={[
        'A clear operator-friendly target name',
        'The primary base URL used for direct scan launches',
        'A simple timeout toggle for future launches',
      ]}
      errorMessage={errorMessage}
      footer={
        <ModalActions
          formId="create-target-form"
          onClose={onClose}
          submitLabel={isLoading ? 'Adding...' : 'Add Target'}
          disabled={isLoading}
        />
      }
    >
      <form id="create-target-form" onSubmit={onSubmit} className="space-y-5">
        <Field label="Target Name" hint="Use a name your team will recognize immediately.">
          <input
            required
            type="text"
            value={formData.name}
            onChange={(event) => setFormData({ ...formData, name: event.target.value })}
            className="modal-input"
            placeholder="e.g. Production API"
          />
        </Field>

        <Field label="Base URL" hint="ScannerX will use this as the primary scan destination.">
          <input
            required
            type="url"
            value={formData.baseUrl}
            onChange={(event) => setFormData({ ...formData, baseUrl: event.target.value })}
            className="modal-input"
            placeholder="https://api.example.com"
          />
        </Field>

        <div className="grid gap-5 md:grid-cols-2">
          <Field label="Tier" hint="All launches for this target inherit this depth.">
            <DarkSelect
              value={formData.defaultTier}
              onChange={(defaultTier) => setFormData({ ...formData, defaultTier })}
              variant="field"
              options={TIER_OPTIONS}
            />
          </Field>

          <TimeoutToggleField
            value={formData.timeoutsEnabled}
            onChange={(timeoutsEnabled) => setFormData({ ...formData, timeoutsEnabled })}
          />
        </div>
      </form>
    </FormDialog>
  </ModalShell>
)

const ScheduleModal = ({ scheduleForm, setScheduleForm, onSubmit, isLoading, onClose }) => (
  <ModalShell onClose={onClose}>
    <FormDialog
      formId="schedule-target-form"
      onClose={onClose}
      eyebrow="Schedule Target"
      title="Create a repeatable scan cadence for this target."
      description="Set a recurring schedule so ScannerX can re-run scans automatically and keep this target under watch."
      summaryTitle="Scheduling notes"
      summaryItems={[
        'Use standard cron syntax for timing',
        'Scheduled runs inherit the target tier automatically',
        'Schedules can be revised later without recreating the target',
      ]}
      footer={
        <ModalActions
          formId="schedule-target-form"
          onClose={onClose}
          submitLabel={isLoading ? 'Scheduling...' : 'Schedule'}
          disabled={isLoading}
        />
      }
    >
      <form id="schedule-target-form" onSubmit={onSubmit} className="space-y-5">
        <Field label="Cron Expression" hint="Example: `0 0 * * *` runs daily at midnight.">
          <input
            type="text"
            value={scheduleForm.cronExpression}
            onChange={(event) => setScheduleForm({ ...scheduleForm, cronExpression: event.target.value })}
            className="modal-input font-mono text-sm"
            placeholder="0 0 * * *"
          />
        </Field>

        <div className="rounded-2xl border border-white/10 bg-white/[0.03] px-4 py-3 text-sm text-slate-300">
          This schedule will use the tier already configured on the target.
        </div>
      </form>
    </FormDialog>
  </ModalShell>
)

const EditTargetModal = ({ target, setTarget, onSubmit, isLoading, errorMessage, onClose }) => (
  <ModalShell onClose={onClose}>
    <FormDialog
      formId="edit-target-form"
      onClose={onClose}
      eyebrow="Edit Target"
      title="Refine target details without breaking the workflow."
      description="Update naming, URL, and scan tier while keeping the same target connected to its scans and findings."
      summaryTitle="Editing scope"
      summaryItems={[
        'Adjust operator-facing target details',
        'Update URL metadata when environments change',
        'Change the default tier and timeout toggle for future launches',
      ]}
      errorMessage={errorMessage}
      footer={
        <ModalActions
          formId="edit-target-form"
          onClose={onClose}
          submitLabel={isLoading ? 'Saving...' : 'Save Changes'}
          disabled={isLoading}
        />
      }
    >
      <form id="edit-target-form" onSubmit={onSubmit} className="space-y-5">
        <Field label="Target Name" hint="Keep naming consistent with the rest of your workspace.">
          <input
            type="text"
            value={target.name}
            onChange={(event) => setTarget({ ...target, name: event.target.value })}
            className="modal-input"
          />
        </Field>

        <Field label="Base URL" hint="Future launches will use this updated URL.">
          <input
            type="url"
            value={target.baseUrl}
            onChange={(event) => setTarget({ ...target, baseUrl: event.target.value })}
            className="modal-input"
          />
        </Field>

        <div className="grid gap-5 md:grid-cols-2">
          <Field label="Tier">
            <DarkSelect
              value={target.defaultTier || 'MEDIUM'}
              onChange={(defaultTier) => setTarget({ ...target, defaultTier })}
              variant="field"
              options={TIER_OPTIONS}
            />
          </Field>

          <TimeoutToggleField
            value={target.timeoutsEnabled ?? true}
            onChange={(timeoutsEnabled) => setTarget({ ...target, timeoutsEnabled })}
          />
        </div>
      </form>
    </FormDialog>
  </ModalShell>
)

const FormDialog = ({
  onClose,
  eyebrow,
  title,
  description,
  summaryTitle,
  summaryItems,
  children,
  errorMessage,
  footer,
}) => (
  <div className="surface-card w-full max-w-5xl overflow-hidden shadow-[0_42px_120px_rgba(0,0,0,0.48)]">
    <div className="flex items-start justify-between gap-6 border-b border-white/8 bg-[linear-gradient(180deg,rgba(255,255,255,0.035),rgba(255,255,255,0.015))] px-6 py-6 sm:px-8">
      <div className="max-w-2xl">
        <p className="text-[11px] font-semibold uppercase tracking-[0.24em] text-[#7be7c0]">{eyebrow}</p>
        <h2 className="mt-3 text-[1.9rem] font-semibold leading-tight text-white">{title}</h2>
        <p className="mt-3 max-w-[52ch] text-[15px] leading-7 text-zinc-400">{description}</p>
      </div>
      <button
        type="button"
        onClick={onClose}
        className="flex h-11 w-11 shrink-0 items-center justify-center rounded-full border border-white/10 bg-white/[0.03] text-zinc-400 transition-colors hover:border-white/16 hover:bg-white/[0.06] hover:text-white"
      >
        <X size={18} />
      </button>
    </div>

    <div className="grid gap-0 lg:grid-cols-[300px_minmax(0,1fr)]">
      <aside className="border-b border-white/8 bg-[linear-gradient(180deg,rgba(13,31,27,0.44),rgba(11,12,13,0.28))] px-6 py-7 sm:px-8 lg:border-r lg:border-b-0">
        <div className="rounded-[18px] border border-white/8 bg-white/[0.03] p-5 backdrop-blur-xl">
          <p className="text-sm font-semibold text-white">{summaryTitle}</p>
          <div className="mt-5 space-y-4">
            {summaryItems.map((item) => (
              <div key={item} className="flex items-start gap-3">
                <span className="mt-2 h-2.5 w-2.5 shrink-0 rounded-full bg-[#60dfb2]" />
                <p className="text-sm leading-6 text-zinc-300">{item}</p>
              </div>
            ))}
          </div>
        </div>
      </aside>

      <div className="px-6 py-7 sm:px-8">
        {children}
        {errorMessage ? (
          <div className="mt-5 rounded-2xl border border-rose-500/20 bg-rose-500/10 px-4 py-3 text-sm text-rose-200">
            {errorMessage}
          </div>
        ) : null}
        <div className="mt-8 border-t border-white/8 pt-5">{footer}</div>
      </div>
    </div>
  </div>
)

const ModalShell = ({ children, onClose }) => {
  useEffect(() => {
    const handleKeyDown = (event) => {
      if (event.key === 'Escape') {
        onClose()
      }
    }

    window.addEventListener('keydown', handleKeyDown)
    return () => window.removeEventListener('keydown', handleKeyDown)
  }, [onClose])

  if (typeof document === 'undefined') {
    return null
  }

  return createPortal(
    <div className="fixed inset-0 z-[100] flex items-center justify-center bg-black/72 p-4 backdrop-blur-md">
      <div className="pointer-events-none absolute inset-0 bg-[radial-gradient(42%_56%_at_18%_28%,rgba(53,208,128,0.12)_0%,rgba(5,6,7,0)_74%),radial-gradient(32%_48%_at_84%_18%,rgba(100,226,231,0.12)_0%,rgba(5,6,7,0)_76%)]" />
      <button
        type="button"
        aria-label="Close modal"
        onClick={onClose}
        className="absolute inset-0 cursor-pointer"
      />
      <div className="relative z-[101]">{children}</div>
    </div>,
    document.body,
  )
}

const Field = ({ label, hint, children }) => (
  <div className="form-field">
    <div className="form-label-row">
      <label className="form-label">{label}</label>
      {hint ? <span className="form-hint">{hint}</span> : null}
    </div>
    {children}
  </div>
)

const TimeoutToggleField = ({ value, onChange }) => (
  <div className="flex min-h-[52px] items-center justify-between gap-4 md:mt-[28px] md:justify-center md:gap-5">
    <span className="text-base font-semibold text-slate-100">Timeouts</span>
    <button
      type="button"
      role="switch"
      aria-checked={value}
      aria-label={`Timeouts ${value ? 'enabled' : 'disabled'}`}
      onClick={() => onChange(!value)}
      className={`relative inline-flex h-7 w-12 items-center rounded-full transition-colors ${
        value ? 'bg-[#60dfb2]/80' : 'bg-white/10'
      }`}
    >
      <span
        className={`inline-block h-5 w-5 transform rounded-full bg-white transition-transform ${
          value ? 'translate-x-6' : 'translate-x-1'
        }`}
      />
    </button>
  </div>
)

const ModalActions = ({ formId, onClose, submitLabel, disabled }) => (
  <div className="flex items-center justify-between gap-3">
    <p className="hidden text-sm text-zinc-500 md:block">Changes are applied immediately after submission.</p>
    <div className="ml-auto flex items-center gap-3">
      <button
        type="button"
        onClick={onClose}
        className="rounded-xl border border-white/10 px-4 py-2.5 font-medium text-slate-400 transition-colors hover:border-white/16 hover:bg-white/[0.04] hover:text-slate-200"
      >
        Cancel
      </button>
      <button
        type="submit"
        form={formId}
        disabled={disabled}
        className="surface-button-primary min-w-[140px] px-6 py-2.5 disabled:cursor-not-allowed disabled:opacity-50"
      >
        {submitLabel}
      </button>
    </div>
  </div>
)

const getMutationErrorMessage = (error, fallbackMessage) => {
  if (!error) {
    return ''
  }

  const responseData = error.response?.data

  if (typeof responseData === 'string' && responseData.trim()) {
    return responseData
  }

  if (typeof responseData?.message === 'string' && responseData.message.trim()) {
    return responseData.message
  }

  if (typeof error.message === 'string' && error.message.trim()) {
    return error.message
  }

  return fallbackMessage
}

const TIER_OPTIONS = [
  { value: 'FAST', label: 'Fast' },
  { value: 'MEDIUM', label: 'Medium' },
  { value: 'DEEP', label: 'Deep' },
]

export default TargetsList
