import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { Shield, UserCheck, UserCog, UserX, Trash2 } from 'lucide-react'
import { deleteUser, getAllUsers, updateUserRole, updateUserStatus } from '../../api/adminApi'
import { useAuth } from '../../context/AuthContext'

const AdminDashboard = () => {
  const queryClient = useQueryClient()
  const { user: currentUser } = useAuth()

  const { data: users = [], isLoading } = useQuery({
    queryKey: ['adminUsers'],
    queryFn: getAllUsers,
  })

  const invalidateUsers = () => {
    queryClient.invalidateQueries({ queryKey: ['adminUsers'] })
  }

  const statusMutation = useMutation({
    mutationFn: ({ userId, status }) => updateUserStatus(userId, status),
    onSuccess: invalidateUsers,
    onError: (error) => window.alert(getErrorMessage(error, 'Unable to update user status.')),
  })

  const roleMutation = useMutation({
    mutationFn: ({ userId, role }) => updateUserRole(userId, role),
    onSuccess: invalidateUsers,
    onError: (error) => window.alert(getErrorMessage(error, 'Unable to update user role.')),
  })

  const deleteMutation = useMutation({
    mutationFn: deleteUser,
    onSuccess: invalidateUsers,
    onError: (error) => window.alert(getErrorMessage(error, 'Unable to delete this user.')),
  })

  const activeUsers = users.filter((user) => String(user.status).toUpperCase() === 'ACTIVE').length
  const adminUsers = users.filter((user) => String(user.role).toUpperCase() === 'ADMIN').length

  if (isLoading) {
    return <div className="text-gray-400">Loading admin panel...</div>
  }

  return (
    <div className="page-shell">
      <div className="page-header">
        <div className="page-header-copy">
          <h1 className="flex items-center text-2xl font-semibold text-gray-100">
            <Shield className="mr-2 h-6 w-6 text-primary" />
            Admin Dashboard
          </h1>
          <p className="page-subtitle">Manage users, roles, account status, and hard-delete workspace accounts.</p>
        </div>
      </div>

      <div className="page-stats-grid">
        <StatCard label="Total Users" value={users.length} icon={UserCog} />
        <StatCard label="Active Users" value={activeUsers} icon={UserCheck} />
        <StatCard label="Admins" value={adminUsers} icon={Shield} />
      </div>

      <div className="table-shell min-h-0">
        <div className="border-b border-white/8 px-6 py-5">
          <h3 className="text-lg font-medium text-gray-200">User Management</h3>
        </div>

        <div className="table-scroll">
          <table className="table-base min-w-[920px] text-sm text-gray-400">
            <thead className="table-head border-b border-border-subtle bg-bg-panel text-xs uppercase tracking-wider text-gray-400">
              <tr>
                <th className="px-6 py-3 font-medium">Name</th>
                <th className="px-6 py-3 font-medium">Email</th>
                <th className="px-6 py-3 font-medium">Role</th>
                <th className="px-6 py-3 font-medium">Status</th>
                <th className="px-6 py-3 font-medium text-right">Actions</th>
              </tr>
            </thead>
            <tbody>
              {users.map((user) => {
                const isSelf = currentUser?.id === user.id
                const isSuspended = String(user.status).toUpperCase() === 'SUSPENDED'
                const isAdmin = String(user.role).toUpperCase() === 'ADMIN'

                return (
                  <tr key={user.id} className="border-b border-border-subtle transition-colors hover:bg-white/5">
                    <td className="px-6 py-4 font-medium text-gray-200">
                      <div className="flex items-center gap-3">
                        <div className="flex h-10 w-10 items-center justify-center rounded-full border border-white/10 bg-white/[0.04] text-sm font-semibold text-white">
                          {getInitials(user.fullName)}
                        </div>
                        <div>
                          <p>{user.fullName}</p>
                          {isSelf ? <p className="text-xs text-slate-500">Current session</p> : null}
                        </div>
                      </div>
                    </td>
                    <td className="px-6 py-4">{user.email}</td>
                    <td className="px-6 py-4">
                      <span className={`rounded-full px-2 py-1 text-xs font-medium ${isAdmin ? 'bg-primary/20 text-primary' : 'bg-white/[0.04] text-gray-300'}`}>
                        {user.role}
                      </span>
                    </td>
                    <td className="px-6 py-4">
                      <span className={`rounded-full px-2 py-1 text-xs font-medium ${isSuspended ? 'bg-red-500/20 text-red-400' : 'bg-green-500/20 text-green-400'}`}>
                        {user.status}
                      </span>
                    </td>
                    <td className="px-6 py-4">
                      <div className="flex justify-end gap-2">
                        <button
                          type="button"
                          onClick={() =>
                            statusMutation.mutate({
                              userId: user.id,
                              status: isSuspended ? 'ACTIVE' : 'SUSPENDED',
                            })
                          }
                          disabled={statusMutation.isPending}
                          className={`rounded-lg px-3 py-2 text-xs font-semibold transition-colors ${
                            isSuspended
                              ? 'border border-green-500/20 bg-green-500/10 text-green-300 hover:bg-green-500/18'
                              : 'border border-red-500/20 bg-red-500/10 text-red-300 hover:bg-red-500/18'
                          }`}
                        >
                          {isSuspended ? 'Activate' : 'Suspend'}
                        </button>
                        <button
                          type="button"
                          onClick={() =>
                            roleMutation.mutate({
                              userId: user.id,
                              role: isAdmin ? 'USER' : 'ADMIN',
                            })
                          }
                          disabled={roleMutation.isPending}
                          className="rounded-lg border border-white/10 bg-white/[0.04] px-3 py-2 text-xs font-semibold text-slate-200 transition-colors hover:bg-white/[0.08]"
                        >
                          {isAdmin ? 'Demote' : 'Promote'}
                        </button>
                        <button
                          type="button"
                          onClick={() => {
                            if (window.confirm(`Delete ${user.fullName} and all owned data?`)) {
                              deleteMutation.mutate(user.id)
                            }
                          }}
                          disabled={deleteMutation.isPending}
                          className="inline-flex items-center gap-2 rounded-lg border border-rose-500/20 bg-rose-500/10 px-3 py-2 text-xs font-semibold text-rose-200 transition-colors hover:bg-rose-500/18"
                        >
                          <Trash2 size={14} />
                          Delete
                        </button>
                      </div>
                    </td>
                  </tr>
                )
              })}
            </tbody>
          </table>
        </div>
      </div>
    </div>
  )
}

const StatCard = ({ label, value, icon }) => {
  const Icon = icon

  return (
    <div className="metric-card">
      <div className="metric-card-body">
        <div>
          <p className="metric-card-label">{label}</p>
          <p className="metric-card-value">{value}</p>
        </div>
        <div className="metric-icon-shell border-white/10 bg-white/[0.04] text-zinc-200">
          <Icon size={24} />
        </div>
      </div>
    </div>
  )
}

function getInitials(name) {
  return String(name || 'SX')
    .split(' ')
    .filter(Boolean)
    .slice(0, 2)
    .map((part) => part[0])
    .join('')
    .toUpperCase()
}

function getErrorMessage(error, fallbackMessage) {
  const responseData = error?.response?.data

  if (typeof responseData === 'string' && responseData.trim()) {
    return responseData
  }

  if (typeof responseData?.message === 'string' && responseData.message.trim()) {
    return responseData.message
  }

  if (typeof error?.message === 'string' && error.message.trim()) {
    return error.message
  }

  return fallbackMessage
}

export default AdminDashboard
