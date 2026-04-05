/* eslint-disable react-refresh/only-export-components */
import React, { createContext, useContext, useState, useEffect } from 'react';
import { getCurrentUser } from '../api/authApi';
import { subscribeToAppNavigation } from '../lib/appNavigation';

const AuthContext = createContext(null);
const AUTH_SESSION_VERSION_KEY = 'authSessionVersion';

const normalizeRole = (role) => String(role || '').toUpperCase();
const createAuthSessionVersion = () => {
  if (typeof crypto !== 'undefined' && typeof crypto.randomUUID === 'function') {
    return crypto.randomUUID();
  }

  return `${Date.now()}-${Math.random().toString(36).slice(2, 10)}`;
};

const ensureAuthSessionVersion = (tokenValue) => {
  if (!tokenValue) {
    localStorage.removeItem(AUTH_SESSION_VERSION_KEY);
    return null;
  }

  let sessionVersion = localStorage.getItem(AUTH_SESSION_VERSION_KEY);
  if (!sessionVersion) {
    sessionVersion = createAuthSessionVersion();
    localStorage.setItem(AUTH_SESSION_VERSION_KEY, sessionVersion);
  }

  return sessionVersion;
};

const clearPersistedAuth = () => {
  localStorage.removeItem('token');
  localStorage.removeItem('user');
  localStorage.removeItem(AUTH_SESSION_VERSION_KEY);
};

export const AuthProvider = ({ children }) => {
  const [user, setUser] = useState(null);
  const [token, setToken] = useState(localStorage.getItem('token'));
  const [isLoading, setIsLoading] = useState(true);

  useEffect(() => {
    let cancelled = false;
    const hydrationToken = token;
    const hydrationSessionVersion = ensureAuthSessionVersion(hydrationToken);

    const hydrateUser = async () => {
      if (!hydrationToken) {
        clearPersistedAuth();
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
        if (localStorage.getItem('token') !== hydrationToken) {
          return;
        }
        if (localStorage.getItem(AUTH_SESSION_VERSION_KEY) !== hydrationSessionVersion) {
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
      } catch (error) {
        if (cancelled) {
          return;
        }
        if (localStorage.getItem('token') !== hydrationToken) {
          return;
        }
        if (localStorage.getItem(AUTH_SESSION_VERSION_KEY) !== hydrationSessionVersion) {
          return;
        }
        if (error?.response?.status === 401) {
          clearPersistedAuth();
          setToken(null);
          setUser(null);
        }
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

  useEffect(() => {
    const unsubscribe = subscribeToAppNavigation(({ reason }) => {
      if (reason !== 'unauthorized') {
        return;
      }

      clearPersistedAuth();
      setToken(null);
      setUser(null);
      setIsLoading(false);
    });

    return unsubscribe;
  }, []);

  const loginUser = (authData) => {
    localStorage.setItem('token', authData.token);
    localStorage.setItem(AUTH_SESSION_VERSION_KEY, createAuthSessionVersion());
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
    clearPersistedAuth();
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
