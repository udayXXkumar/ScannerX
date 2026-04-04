import { NavLink } from 'react-router-dom';
import { LayoutDashboard, Shield, Target, FileText, AlertTriangle, Clock, Users, Bell } from 'lucide-react';
import { useAuth } from '../../context/AuthContext';

const Sidebar = () => {
  const { user } = useAuth();

  const navItems = [
    { name: 'Dashboard', path: '/', icon: LayoutDashboard },
    { name: 'Scans', path: '/scans', icon: Shield },
    { name: 'Targets', path: '/targets', icon: Target },
    { name: 'Schedules', path: '/schedules', icon: Clock },
    { name: 'Findings', path: '/findings', icon: AlertTriangle },
    { name: 'Reports', path: '/reports', icon: FileText },
    { name: 'Notifications', path: '/notifications', icon: Bell },
  ];

  if (user?.role === 'ADMIN') {
    navItems.push({ name: 'Admin Panel', path: '/admin', icon: Users });
  }

  return (
    <div className="w-64 bg-bg-panel border-r border-border-subtle flex flex-col">
      <div className="h-16 flex items-center px-6 border-b border-border-subtle shrink-0">
        <Shield className="w-8 h-8 text-primary mr-3" />
        <span className="font-bold text-xl tracking-wide">Scanner<span className="text-primary">X</span></span>
      </div>
      <nav className="flex-1 py-4 px-3 space-y-1">
        {navItems.map((item) => (
          <NavLink
            key={item.name}
            to={item.path}
            className={({ isActive }) =>
              `flex items-center px-3 py-2.5 rounded-md transition-colors ${
                isActive 
                  ? 'bg-primary/10 text-primary font-medium' 
                  : 'text-gray-400 hover:text-gray-100 hover:bg-white/5'
              }`
            }
            end={item.path === '/'}
          >
            <item.icon className="w-5 h-5 mr-3" />
            {item.name}
          </NavLink>
        ))}
      </nav>
    </div>
  );
};
export default Sidebar;
