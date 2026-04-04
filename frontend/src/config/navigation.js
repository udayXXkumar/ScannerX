import {
  ArrowLeftRight,
  Bell,
  Clock3,
  FileText,
  LayoutDashboard,
  Radar,
  Settings,
  Shield,
  Tag,
  Target,
  BarChart3,
  UserRound,
} from 'lucide-react'

export const navigationGroups = [
  {
    items: [
      {
        label: 'Overview',
        href: '/dashboard',
        icon: LayoutDashboard,
      },
    ],
  },
  {
    items: [
      {
        label: 'Findings',
        href: '/findings',
        icon: Tag,
      },
    ],
  },
  {
    items: [
      {
        label: 'Operations',
        icon: Settings,
        defaultOpen: true,
        children: [
          { label: 'Targets', href: '/targets', icon: Target },
          { label: 'Scan Jobs', href: '/scans', icon: Radar },
          { label: 'Schedules', href: '/schedules', icon: Clock3 },
        ],
      },
    ],
  },
  {
    items: [
      {
        label: 'Insights',
        icon: BarChart3,
        defaultOpen: false,
        children: [
          { label: 'Comparison', href: '/compare', icon: ArrowLeftRight },
          { label: 'Notifications', href: '/notifications', icon: Bell },
          { label: 'Reports', href: '/reports', icon: FileText },
        ],
      },
    ],
  },
  {
    adminOnly: true,
    items: [
      {
        label: 'Administration',
        icon: Shield,
        defaultOpen: false,
        children: [
          { label: 'Admin', href: '/admin', icon: Shield },
        ],
      },
    ],
  },
]

const routeMeta = [
  {
    match: (pathname) => pathname === '/' || pathname === '/dashboard',
    title: 'Overview',
    icon: LayoutDashboard,
  },
  {
    match: (pathname) => pathname.startsWith('/findings'),
    title: 'Findings',
    icon: Tag,
  },
  {
    match: (pathname) => pathname.startsWith('/targets'),
    title: 'Targets',
    icon: Target,
  },
  {
    match: (pathname) => pathname.startsWith('/scans'),
    title: 'Scan Jobs',
    icon: Radar,
  },
  {
    match: (pathname) => pathname.startsWith('/schedules'),
    title: 'Schedules',
    icon: Clock3,
  },
  {
    match: (pathname) => pathname.startsWith('/notifications'),
    title: 'Notifications',
    icon: Bell,
  },
  {
    match: (pathname) => pathname.startsWith('/compare'),
    title: 'Comparison',
    icon: ArrowLeftRight,
  },
  {
    match: (pathname) => pathname.startsWith('/reports'),
    title: 'Reports',
    icon: FileText,
  },
  {
    match: (pathname) => pathname.startsWith('/profile'),
    title: 'User Profile',
    icon: UserRound,
  },
  {
    match: (pathname) => pathname.startsWith('/admin'),
    title: 'Admin',
    icon: Shield,
  },
]

export function isRouteActive(pathname, href) {
  if (href === '/dashboard') {
    return pathname === '/' || pathname === '/dashboard'
  }

  return pathname === href || pathname.startsWith(`${href}/`)
}

export function getRouteMeta(pathname) {
  return (
    routeMeta.find((item) => item.match(pathname)) ?? {
      title: 'ScannerX',
      icon: LayoutDashboard,
    }
  )
}
