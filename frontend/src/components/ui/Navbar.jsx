import { Bell, LogOut, Menu, PanelLeftClose, PanelLeftOpen } from 'lucide-react'
import { useQuery } from '@tanstack/react-query'
import { useNavigate } from 'react-router-dom'
import { useAuth } from '../../context/AuthContext'
import { useSidebar } from '../../context/SidebarContext'
import { getUnreadNotificationCount } from '../../api/notificationApi'
import HoverTooltip from './HoverTooltip'

export default function Navbar({ title = 'Dashboard', icon: IconComponent = null }) {
  const { user, logoutUser } = useAuth()
  const { isOpen, toggleOpen, openMobile } = useSidebar()
  const navigate = useNavigate()
  const { data: unreadCount = 0 } = useQuery({
    queryKey: ['notifications', 'unreadCount'],
    queryFn: getUnreadNotificationCount,
    enabled: Boolean(user),
    staleTime: 15_000,
  })

  const initials = user?.fullName
    ?.split(' ')
    .filter(Boolean)
    .slice(0, 2)
    .map((part) => part[0])
    .join('')
    .toUpperCase()

  const handleLogout = () => {
    logoutUser()
    navigate('/login')
  }

  return (
    <header className="sticky top-0 z-20 bg-transparent px-4 pt-4 sm:px-6 lg:px-8">
      <div className="mx-auto flex h-12 w-full max-w-7xl items-center gap-3">
        <button
          onClick={openMobile}
          className="flex h-9 w-9 items-center justify-center rounded-[10px] border border-white/10 bg-white/[0.03] text-zinc-200 transition-colors hover:bg-white/[0.06] lg:hidden"
          aria-label="Open menu"
        >
          <Menu size={18} />
        </button>

        <HoverTooltip label={isOpen ? 'Collapse Sidebar' : 'Expand Sidebar'}>
          <button
            onClick={toggleOpen}
            className="hidden h-9 w-9 items-center justify-center rounded-[10px] border border-white/10 bg-white/[0.03] text-zinc-200 transition-colors hover:bg-white/[0.06] lg:flex"
            aria-label={isOpen ? 'Collapse sidebar' : 'Expand sidebar'}
          >
            {isOpen ? <PanelLeftClose size={18} /> : <PanelLeftOpen size={18} />}
          </button>
        </HoverTooltip>

        <div className="flex min-w-0 items-center gap-3">
          <div className="hidden h-8 w-8 items-center justify-center rounded-[10px] border border-white/10 bg-white/[0.03] text-zinc-100 sm:flex">
            {IconComponent ? <IconComponent size={18} /> : <PanelLeftOpen size={18} />}
          </div>
          <div className="min-w-0">
            <p className="truncate text-base font-semibold text-white">{title}</p>
          </div>
        </div>

        <div className="ml-auto flex items-center gap-3">
          <HoverTooltip label="Latest Updates">
            <button
              onClick={() => navigate('/notifications')}
              className="relative flex h-9 w-9 items-center justify-center rounded-full border border-[#60dfb2]/40 text-zinc-300 transition-colors hover:border-[#60dfb2]/60 hover:text-white"
              aria-label="Open notifications"
            >
              <Bell size={18} />
              {unreadCount > 0 ? <span className="absolute right-2 top-2 h-2 w-2 rounded-full bg-[#60dfb2]" /> : null}
            </button>
          </HoverTooltip>

          <HoverTooltip label="Account Settings">
            <button
              type="button"
              onClick={() => navigate('/profile')}
              className="flex h-9 min-w-9 items-center justify-center rounded-full border border-white/20 px-3 text-sm font-semibold text-white transition-colors hover:border-white/30 hover:bg-white/[0.04]"
              aria-label="Open user profile"
            >
              {initials || 'SX'}
            </button>
          </HoverTooltip>

          <HoverTooltip label="Sign Out" align="right">
            <button
              onClick={handleLogout}
              className="flex h-9 w-9 items-center justify-center rounded-full border border-white/10 text-zinc-300 transition-colors hover:border-white/20 hover:text-white"
              aria-label="Sign out"
            >
              <LogOut size={16} />
            </button>
          </HoverTooltip>
        </div>
      </div>
    </header>
  )
}
