import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { Bell, CheckCircle2, AlertTriangle, Info, Trash2 } from 'lucide-react'
import { useNavigate } from 'react-router-dom'
import {
  deleteNotification,
  getNotifications,
  markNotificationRead,
} from '../../api/notificationApi'

const Notifications = () => {
  const navigate = useNavigate()
  const queryClient = useQueryClient()

  const { data: notifications = [], isLoading } = useQuery({
    queryKey: ['notifications'],
    queryFn: getNotifications,
  })

  const markAsReadMutation = useMutation({
    mutationFn: markNotificationRead,
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['notifications'] })
      queryClient.invalidateQueries({ queryKey: ['notifications', 'unreadCount'] })
    },
  })

  const deleteNotificationMutation = useMutation({
    mutationFn: deleteNotification,
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['notifications'] })
      queryClient.invalidateQueries({ queryKey: ['notifications', 'unreadCount'] })
    },
  })

  const unreadCount = notifications.filter((notification) => !notification.read).length

  const handleNotificationClick = async (notification) => {
    if (!notification.read) {
      await markAsReadMutation.mutateAsync(notification.id)
    }

    if (notification.scanId || notification.targetId) {
      const params = new URLSearchParams()
      if (notification.targetId) {
        params.set('targetId', String(notification.targetId))
      }
      if (notification.scanId) {
        params.set('scanId', String(notification.scanId))
      }
      navigate(`/findings?${params.toString()}`)
    }
  }

  return (
    <div className="page-shell">
      <div className="space-y-4">
        <div className="page-header">
          <div className="page-header-copy">
            <h2 className="flex items-center gap-3 text-3xl font-bold text-white">
              <div className="flex h-10 w-10 items-center justify-center rounded-xl border border-white/10 bg-white/[0.04] backdrop-blur-xl">
                <Bell className="h-6 w-6 text-prowler-green" />
              </div>
              Notifications
            </h2>
            <p className="page-subtitle">Review completed scan updates and open the linked findings directly.</p>
          </div>
          {unreadCount > 0 ? (
            <div className="rounded-lg border border-severity-high/30 bg-severity-high/10 px-4 py-2">
              <p className="font-semibold text-severity-high">{unreadCount} Unread</p>
            </div>
          ) : null}
        </div>
      </div>

      <div className="table-shell">
        {isLoading ? (
          <div className="flex h-full items-center justify-center">
            <p className="text-slate-400">Loading notifications...</p>
          </div>
        ) : notifications.length === 0 ? (
          <div className="empty-state">
            <div className="empty-state-panel">
              <div className="empty-state-icon">
                <Bell size={34} />
              </div>
              <p className="text-lg text-slate-400">No notifications yet.</p>
              <p className="mt-2 text-sm text-slate-500">Completed scans will appear here.</p>
            </div>
          </div>
        ) : (
          <div className="flex-1 space-y-3 overflow-auto p-6">
            {notifications.map((notification) => (
              <div
                key={notification.id}
                role="button"
                tabIndex={0}
                onClick={() => handleNotificationClick(notification)}
                onKeyDown={(event) => {
                  if (event.key === 'Enter' || event.key === ' ') {
                    event.preventDefault()
                    handleNotificationClick(notification)
                  }
                }}
                className={`rounded-lg border p-4 transition-colors ${
                  notification.read
                    ? 'border-white/8 bg-white/[0.025] opacity-75'
                    : `bg-white/[0.04] ${getTypeColor(notification.type)}`
                } ${notification.scanId || notification.targetId ? 'cursor-pointer hover:border-white/12 hover:bg-white/[0.05]' : ''}`}
              >
                <div className="flex items-start justify-between gap-4">
                  <div className="flex min-w-0 flex-1 items-start gap-3">
                    <div className="mt-1 shrink-0">{getIcon(notification.type)}</div>
                    <div className="min-w-0 flex-1">
                      <div className="flex flex-wrap items-center gap-2">
                        <h4 className={`font-semibold ${notification.read ? 'text-slate-300' : 'text-white'}`}>
                          {notification.title}
                        </h4>
                        {notification.findingCount !== null && notification.findingCount !== undefined ? (
                          <span className="rounded-full border border-white/8 bg-white/[0.04] px-2 py-0.5 text-xs text-slate-300">
                            {notification.findingCount} findings
                          </span>
                        ) : null}
                      </div>
                      <p className="mt-1 text-sm text-slate-400">{notification.message}</p>
                      <span className="mt-2 block text-xs text-slate-500">
                        {new Date(notification.createdAt).toLocaleString()}
                      </span>
                    </div>
                  </div>

                  <div className="flex shrink-0 items-center gap-2">
                    {!notification.read ? (
                      <button
                        onClick={(event) => {
                          event.stopPropagation()
                          markAsReadMutation.mutate(notification.id)
                        }}
                        disabled={markAsReadMutation.isPending}
                        className="rounded-lg border border-prowler-green/30 bg-prowler-green/10 px-3 py-1 text-xs font-semibold text-prowler-green transition-colors hover:bg-prowler-green/20 disabled:opacity-50"
                      >
                        {markAsReadMutation.isPending ? 'Marking...' : 'Mark Read'}
                      </button>
                    ) : null}
                    <button
                      onClick={(event) => {
                        event.stopPropagation()
                        deleteNotificationMutation.mutate(notification.id)
                      }}
                      disabled={deleteNotificationMutation.isPending}
                      className="rounded-lg p-2 text-slate-400 transition-colors hover:bg-white/[0.04] hover:text-system-error"
                      title="Delete notification"
                    >
                      <Trash2 size={16} />
                    </button>
                  </div>
                </div>
              </div>
            ))}
          </div>
        )}
      </div>
    </div>
  )
}

function getIcon(type) {
  switch (type) {
    case 'SUCCESS':
      return <CheckCircle2 className="h-5 w-5 text-system-success" />
    case 'ERROR':
      return <AlertTriangle className="h-5 w-5 text-system-error" />
    case 'WARNING':
      return <AlertTriangle className="h-5 w-5 text-system-warning" />
    default:
      return <Info className="h-5 w-5 text-zinc-300" />
  }
}

function getTypeColor(type) {
  switch (type) {
    case 'SUCCESS':
      return 'border-system-success/30 bg-system-success/10'
    case 'ERROR':
      return 'border-system-error/30 bg-system-error/10'
    case 'WARNING':
      return 'border-system-warning/30 bg-system-warning/10'
    default:
      return 'border-white/10 bg-white/[0.04]'
  }
}

export default Notifications
