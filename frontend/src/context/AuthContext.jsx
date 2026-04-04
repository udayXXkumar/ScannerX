/* eslint-disable react-refresh/only-export-components */
import React, { createContext, useContext, useState, useEffect } from 'react';
import { getCurrentUser } from '../api/authApi';

const AuthContext = createContext(null);

const normalizeRole = (role) => String(role || '').toUpperCase();

export const AuthProvider = ({ children }) => {
  const [user, setUser] = useState(null);
  const [token, setToken] = useState(localStorage.getItem('token'));
  const [isLoading, setIsLoading] = useState(true);

  useEffect(() => {
    let cancelled = false;

    const hydrateUser = async () => {
      if (!token) {
        localStorage.removeItem('token');
        localStorage.removeItem('user');
        if (!cancelled) {
          setUser(null);
          setIsLoading(false);
        }
        return;
      }

      const storedUser = localStorage.getItem('user');
      if (storedUser) {
        try {
          setUser(JSON.parse(storedUser));
        } catch (error) {
          console.error('Failed to parse user from local storage:', error);
        }
      }

      try {
        const currentUser = await getCurrentUser();
        if (cancelled) {
          return;
        }
        const nextUser = {
          id: currentUser.id,
          email: currentUser.email,
          fullName: currentUser.fullName,
          role: normalizeRole(currentUser.role),
          status: currentUser.status,
        };
        localStorage.setItem('user', JSON.stringify(nextUser));
        setUser(nextUser);
      } catch {
        if (cancelled) {
          return;
        }
        localStorage.removeItem('token');
        localStorage.removeItem('user');
        setToken(null);
        setUser(null);
      } finally {
        if (!cancelled) {
          setIsLoading(false);
        }
      }
    };

    setIsLoading(true);
    hydrateUser();

    return () => {
      cancelled = true;
    };
  }, [token]);

  const loginUser = (authData) => {
    localStorage.setItem('token', authData.token);
    const userInfo = {
      id: authData.id,
      email: authData.email,
      fullName: authData.fullName,
      role: normalizeRole(authData.role),
      status: authData.status,
    };
    localStorage.setItem('user', JSON.stringify(userInfo));
    setToken(authData.token);
    setUser(userInfo);
  };

  const updateUser = (updates) => {
    setUser((previousUser) => {
      const nextUser = { ...(previousUser || {}), ...updates };
      localStorage.setItem('user', JSON.stringify(nextUser));
      return nextUser;
    });
  };

  const logoutUser = () => {
    localStorage.removeItem('token');
    localStorage.removeItem('user');
    setToken(null);
    setUser(null);
  };

  const hasRole = (role) => normalizeRole(user?.role) === normalizeRole(role);
  const isAdmin = hasRole('ADMIN');

  return (
    <AuthContext.Provider
      value={{
        user,
        token,
        isLoading,
        loginUser,
        logoutUser,
        logout: logoutUser,
        updateUser,
        hasRole,
        isAdmin,
      }}
    >
      {children}
    </AuthContext.Provider>
  );
};

export const useAuth = () => useContext(AuthContext);
