import axios from 'axios';
import { requestAppNavigation } from '../lib/appNavigation';

const AUTH_SESSION_VERSION_KEY = 'authSessionVersion';
const AUTH_CONFIRMATION_REQUEST_FLAG = '__isAuthConfirmationRequest';
const AUTH_CONFIRMED_UNAUTHORIZED_FLAG = '__authConfirmedUnauthorized';
const configuredApiBaseUrl = import.meta.env.VITE_API_BASE_URL?.trim();
const apiBaseUrl = configuredApiBaseUrl
  ? configuredApiBaseUrl.replace(/\/+$/, '')
  : '/api';

const createAuthSessionVersion = () => {
  if (typeof crypto !== 'undefined' && typeof crypto.randomUUID === 'function') {
    return crypto.randomUUID();
  }

  return `${Date.now()}-${Math.random().toString(36).slice(2, 10)}`;
};

const getOrCreateAuthSessionVersion = (token) => {
  if (!token) {
    return null;
  }

  let sessionVersion = localStorage.getItem(AUTH_SESSION_VERSION_KEY);
  if (!sessionVersion) {
    sessionVersion = createAuthSessionVersion();
    localStorage.setItem(AUTH_SESSION_VERSION_KEY, sessionVersion);
  }

  return sessionVersion;
};

const api = axios.create({
  baseURL: apiBaseUrl,
  headers: {
    'Content-Type': 'application/json',
  },
});
const authConfirmationClient = axios.create({
  baseURL: apiBaseUrl,
  headers: {
    'Content-Type': 'application/json',
  },
});

let pendingUnauthorizedConfirmation = null;

const clearPersistedAuth = () => {
  localStorage.removeItem('token');
  localStorage.removeItem('user');
  localStorage.removeItem(AUTH_SESSION_VERSION_KEY);
};

const isAuthRouteRequest = (requestUrl) =>
  requestUrl.includes('/auth/login') || requestUrl.includes('/auth/register');

const isAuthConfirmationRequest = (config) =>
  Boolean(config?.[AUTH_CONFIRMATION_REQUEST_FLAG]) || String(config?.url || '').includes('/auth/me');

const getSessionKey = (token, sessionVersion) =>
  token && sessionVersion ? `${sessionVersion}:${token}` : null;

const markConfirmedUnauthorized = (error) => {
  if (!error) {
    return error;
  }

  error[AUTH_CONFIRMED_UNAUTHORIZED_FLAG] = true;
  return error;
};

const confirmCurrentSession = ({ token, sessionVersion }) => {
  const sessionKey = getSessionKey(token, sessionVersion);
  if (!sessionKey) {
    return Promise.resolve({ status: 'unknown' });
  }

  if (pendingUnauthorizedConfirmation?.sessionKey === sessionKey) {
    return pendingUnauthorizedConfirmation.promise;
  }

  const promise = authConfirmationClient
    .get('/auth/me', {
      headers: {
        Authorization: `Bearer ${token}`,
      },
      validateStatus: () => true,
      [AUTH_CONFIRMATION_REQUEST_FLAG]: true,
    })
    .then((response) => {
      if (response.status === 401) {
        return { status: 'unauthorized' };
      }

      if (response.status >= 200 && response.status < 300) {
        return { status: 'valid' };
      }

      return { status: 'unknown' };
    })
    .catch(() => ({ status: 'unknown' }))
    .finally(() => {
      if (pendingUnauthorizedConfirmation?.sessionKey === sessionKey) {
        pendingUnauthorizedConfirmation = null;
      }
    });

  pendingUnauthorizedConfirmation = { sessionKey, promise };
  return promise;
};

api.interceptors.request.use(
  (config) => {
    const token = localStorage.getItem('token');
    const authSessionVersion = getOrCreateAuthSessionVersion(token);
    config.__authToken = token;
    config.__authSessionVersion = authSessionVersion;
    if (token) {
      config.headers.Authorization = `Bearer ${token}`;
    }
    return config;
  },
  (error) => {
    return Promise.reject(error);
  }
);

api.interceptors.response.use(
  (response) => response,
  async (error) => {
    const status = error.response?.status;
    const currentPath = window.location.pathname;
    const requestUrl = String(error.config?.url || '');
    const requestToken = error.config?.__authToken ?? null;
    const requestSessionVersion = error.config?.__authSessionVersion ?? null;
    const currentToken = localStorage.getItem('token');
    const currentSessionVersion = localStorage.getItem(AUTH_SESSION_VERSION_KEY);
    const shouldHandleUnauthorized =
      status === 401
      && !isAuthRouteRequest(requestUrl)
      && !isAuthConfirmationRequest(error.config)
      && !currentPath.startsWith('/login')
      && !currentPath.startsWith('/register')
      && requestToken
      && requestSessionVersion
      && requestToken === currentToken
      && requestSessionVersion === currentSessionVersion;

    if (!shouldHandleUnauthorized) {
      return Promise.reject(error);
    }

    const confirmation = await confirmCurrentSession({
      token: requestToken,
      sessionVersion: requestSessionVersion,
    });

    const latestToken = localStorage.getItem('token');
    const latestSessionVersion = localStorage.getItem(AUTH_SESSION_VERSION_KEY);
    const isSameSession =
      latestToken === requestToken && latestSessionVersion === requestSessionVersion;

    if (confirmation.status === 'unauthorized' && isSameSession) {
      clearPersistedAuth();
      requestAppNavigation({
        to: '/login',
        replace: true,
        reason: 'unauthorized',
      });
      return Promise.reject(markConfirmedUnauthorized(error));
    }

    return Promise.reject(error);
  }
);

export const isConfirmedUnauthorizedError = (error) => Boolean(error?.[AUTH_CONFIRMED_UNAUTHORIZED_FLAG]);

export default api;
