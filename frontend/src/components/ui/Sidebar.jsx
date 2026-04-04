import { useState } from 'react'
import { ChevronDown, ExternalLink, LogOut, Plus, X } from 'lucide-react'
import { NavLink, useLocation, useNavigate } from 'react-router-dom'
import { useAuth } from '../../context/AuthContext'
import { useSidebar } from '../../context/SidebarContext'
import { navigationGroups, isRouteActive } from '../../config/navigation'
import { cn } from '../../lib/utils'
import { ScannerXMark, ScannerXWordmark } from '../icons/ProwlerIcons'
import HoverTooltip from './HoverTooltip'

export default function Sidebar() {
  const { logoutUser } = useAuth()
  const navigate = useNavigate()
  const { getOpenState, isMobileOpen, setIsHover, closeMobile } = useSidebar()

  const handleLogout = () => {
    closeMobile()
    logoutUser()
    navigate('/login')
  }

  return (
    <>
      <div
        className={cn(
          'fixed inset-0 z-40 bg-black/60 transition-opacity lg:hidden',
          isMobileOpen ? 'opacity-100' : 'pointer-events-none opacity-0',
        )}
        onClick={closeMobile}
      />

      <aside
        className={cn(
          'fixed left-0 top-0 z-50 h-screen w-72 bg-[#0a0f0f]/94 backdrop-blur-xl transition-transform duration-300 ease-in-out lg:hidden',
          isMobileOpen ? 'translate-x-0' : '-translate-x-full',
        )}
      >
        <SidebarContent isOpen onClose={closeMobile} onLogout={handleLogout} mobile />
      </aside>

      <aside
        className={cn(
          'fixed left-0 top-0 z-30 hidden h-screen bg-transparent transition-[width] duration-300 ease-in-out lg:block',
          getOpenState() ? 'w-72' : 'w-24',
        )}
      >
        <div
          onMouseEnter={() => setIsHover(true)}
          onMouseLeave={() => setIsHover(false)}
          className="no-scrollbar h-full"
        >
          <SidebarContent isOpen={getOpenState()} onLogout={handleLogout} />
        </div>
      </aside>
    </>
  )
}

function SidebarContent({ isOpen, onClose, onLogout, mobile = false }) {
  const { isAdmin } = useAuth()
  const navigate = useNavigate()
  const visibleNavigationGroups = navigationGroups.filter((group) => !group.adminOnly || isAdmin)

  const handleLaunchScan = () => {
    onClose?.()
    navigate('/targets')
  }

  return (
    <div
      className={cn(
        'relative flex h-full flex-col overflow-hidden px-4 py-6',
        mobile ? '' : 'bg-transparent',
      )}
    >
      <div
        className={cn(
          'mb-8 grid items-center gap-3',
          mobile ? 'grid-cols-[minmax(0,1fr)_auto]' : 'grid-cols-1',
        )}
      >
        <NavLink
          to="/dashboard"
          onClick={onClose}
          className={cn(
            'flex w-full max-w-full items-center px-2 text-white transition-opacity hover:opacity-90',
            mobile ? 'justify-start' : 'justify-center',
          )}
        >
          <div className={cn(isOpen ? 'hidden' : 'block')}>
            <ScannerXMark className="mx-auto h-auto w-12" />
          </div>
          <div className={cn(isOpen ? 'block' : 'hidden')}>
            <ScannerXWordmark className="mx-auto h-auto w-60" />
          </div>
        </NavLink>

        {mobile ? (
          <button
            onClick={onClose}
            className="flex h-10 w-10 items-center justify-center rounded-xl border border-white/10 bg-white/5 text-zinc-300 transition-colors hover:bg-white/10 hover:text-white"
            aria-label="Close menu"
          >
            <X size={18} />
          </button>
        ) : null}
      </div>

      <button
        onClick={handleLaunchScan}
        className={cn(
          'mb-6 flex items-center justify-center rounded-[6px] bg-[#60dfb2] px-4 py-3 text-sm font-semibold text-[#070809] transition-transform hover:-translate-y-0.5 hover:bg-[#74e8bf]',
          isOpen ? 'gap-2' : 'h-12 px-0',
        )}
      >
        <Plus size={18} />
        {isOpen ? <span>Launch Scan</span> : null}
      </button>

      <div className="no-scrollbar flex-1 overflow-y-auto">
        <nav className="space-y-2 pb-3">
          {visibleNavigationGroups.map((group, groupIndex) => (
            <div key={groupIndex} className="space-y-1">
              {group.items.map((item) =>
                item.children ? (
                  <CollapsibleMenu
                    key={item.label}
                    item={item}
                    isOpen={isOpen}
                    onNavigate={onClose}
                  />
                ) : (
                  <SidebarLink
                    key={item.href}
                    item={item}
                    isOpen={isOpen}
                    onNavigate={onClose}
                  />
                ),
              )}
            </div>
          ))}
        </nav>
      </div>

      <div className="mt-5 border-t border-white/8 pt-4">
        <div
          className={cn(
            'flex items-center text-xs text-zinc-300/90',
            isOpen ? 'justify-between px-1' : 'justify-center',
          )}
        >
          {isOpen ? (
            <>
              <div className="flex items-center gap-3">
                <span className="text-sm text-zinc-300">v5.22.0</span>
                <span className="inline-flex items-center gap-2 text-zinc-400">
                  <span className="h-2 w-2 rounded-full bg-[#60dfb2]" />
                  Service Status
                </span>
              </div>
              <HoverTooltip label="Sign Out" align="right">
                <button
                  onClick={onLogout}
                  className="flex h-9 w-9 items-center justify-center rounded-full border border-white/12 text-zinc-300 transition-colors hover:border-white/20 hover:text-white"
                  aria-label="Sign out"
                >
                  <LogOut size={16} />
                </button>
              </HoverTooltip>
            </>
          ) : (
            <HoverTooltip label="Sign Out" align="right">
              <button
                onClick={onLogout}
                className="flex h-9 w-9 items-center justify-center rounded-full border border-white/12 text-zinc-300 transition-colors hover:border-white/20 hover:text-white"
                aria-label="Sign out"
              >
                <LogOut size={16} />
              </button>
            </HoverTooltip>
          )}
        </div>
      </div>
    </div>
  )
}

function SidebarLink({ item, isOpen, onNavigate }) {
  const location = useLocation()
  const Icon = item.icon
  const active = isRouteActive(location.pathname, item.href)

  return (
    <NavLink
      to={item.href}
      onClick={onNavigate}
      className={cn(
        'flex items-center rounded-xl border px-4 py-3 text-sm transition-all duration-200',
        isOpen ? 'justify-start gap-4' : 'justify-center',
        active
          ? 'border-white/10 bg-white/[0.08] text-white'
          : 'border-transparent text-zinc-200/88 hover:border-white/6 hover:bg-white/[0.04] hover:text-white',
      )}
      title={item.label}
    >
      <Icon size={18} />
      {isOpen ? <span className="truncate">{item.label}</span> : null}
      {item.external && isOpen ? <ExternalLink size={14} className="ml-auto" /> : null}
    </NavLink>
  )
}

function CollapsibleMenu({ item, isOpen, onNavigate }) {
  const location = useLocation()
  const isActive = item.children.some((child) => isRouteActive(location.pathname, child.href))
  const [manuallyExpanded, setManuallyExpanded] = useState(Boolean(item.defaultOpen))
  const expanded = isActive || manuallyExpanded

  const Icon = item.icon

  return (
    <div className="space-y-1">
      <button
        type="button"
        onClick={() => setManuallyExpanded((previous) => !previous)}
        className={cn(
          'flex w-full items-center rounded-xl border px-4 py-3 text-sm transition-all duration-200',
          isOpen ? 'justify-between' : 'justify-center',
          isActive
            ? 'border-white/10 bg-white/[0.08] text-white'
            : 'border-transparent text-zinc-200/88 hover:border-white/6 hover:bg-white/[0.04] hover:text-white',
        )}
        title={item.label}
      >
        {isOpen ? (
          <>
            <span className="flex min-w-0 items-center gap-4">
              <Icon size={18} />
              <span className="truncate">{item.label}</span>
            </span>
            <ChevronDown
              size={16}
              className={cn('shrink-0 transition-transform', expanded ? 'rotate-180' : 'rotate-0')}
            />
          </>
        ) : (
          <Icon size={18} />
        )}
      </button>

      {isOpen && expanded ? (
        <div className="space-y-1 pl-3">
          {item.children.map((child) => (
            <SidebarLink key={child.href} item={child} isOpen onNavigate={onNavigate} />
          ))}
        </div>
      ) : null}
    </div>
  )
}
