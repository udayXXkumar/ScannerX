import { Navigate } from 'react-router-dom';
import { useAuth } from '../../context/AuthContext';

const normalizeRole = (role) => String(role || '').toUpperCase();

const RoleRoute = ({ allowedRoles = [], children }) => {
  const { user, isLoading } = useAuth();

  if (isLoading) {
    return <div className="min-h-screen bg-bg-base flex items-center justify-center text-gray-400">Loading Session...</div>;
  }

  const isAllowed = allowedRoles.some((role) => normalizeRole(user?.role) === normalizeRole(role));

  if (!isAllowed) {
    return <Navigate to="/dashboard" replace />;
  }

  return children;
};

export default RoleRoute;
