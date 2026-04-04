import Sidebar from './Sidebar'
import Navbar from './Navbar'
import { useSidebar } from '../../context/SidebarContext'
import { cn } from '../../lib/utils'

export default function MainLayout({ children, title = 'Dashboard', icon = null }) {
  const { getOpenState } = useSidebar()

  return (
    <div className="relative flex min-h-screen overflow-hidden bg-[#08090a] text-zinc-100">
      <div className="pointer-events-none absolute inset-0 bg-[#050607]" />
      <div className="pointer-events-none absolute inset-0 bg-[linear-gradient(90deg,rgba(20,70,59,0.34)_0%,rgba(8,12,12,0.14)_18%,rgba(5,6,7,0)_38%)]" />
      <div className="pointer-events-none absolute inset-0 bg-[radial-gradient(circle_at_50%_38%,rgba(5,6,7,0.02)_0%,rgba(5,6,7,0.14)_32%,rgba(5,6,7,0.42)_68%,rgba(5,6,7,0.72)_100%)]" />
      <div className="pointer-events-none absolute inset-x-0 top-0 h-[180px] bg-gradient-to-b from-white/[0.03] to-transparent" />
      <div className="pointer-events-none absolute inset-0 z-0 overflow-hidden mix-blend-screen">
        <div
          className="absolute inset-0"
          style={{
            backgroundImage: [
              'radial-gradient(44% 86% at -6% 38%, rgba(53, 208, 128, 0.34) 0%, rgba(27, 110, 68, 0.18) 38%, rgba(5, 6, 7, 0) 78%)',
              'radial-gradient(38% 80% at 106% 32%, rgba(100, 226, 231, 0.21) 0%, rgba(48, 116, 130, 0.12) 38%, rgba(5, 6, 7, 0) 80%)',
              'radial-gradient(24% 24% at 50% 82%, rgba(74, 241, 247, 0.08) 0%, rgba(5, 6, 7, 0) 76%)',
            ].join(', '),
          }}
        />
        <div
          className="absolute inset-0 blur-2xl"
          style={{
            backgroundImage: [
              'radial-gradient(32% 66% at 4% 46%, rgba(72, 232, 161, 0.09) 0%, rgba(5, 6, 7, 0) 72%)',
              'radial-gradient(30% 64% at 98% 38%, rgba(120, 238, 255, 0.09) 0%, rgba(5, 6, 7, 0) 74%)',
            ].join(', '),
          }}
        />
      </div>

      <Sidebar />

      <div
        className={cn(
          'relative z-10 flex min-h-screen min-w-0 flex-1 flex-col overflow-x-hidden transition-[padding-left] duration-300 ease-in-out',
          getOpenState() ? 'lg:pl-72' : 'lg:pl-24',
        )}
      >
        <Navbar title={title} icon={icon} />
        <div className="relative z-10 flex-1 overflow-x-hidden overflow-y-auto">{children}</div>
      </div>
    </div>
  )
}
