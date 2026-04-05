const APP_NAVIGATION_EVENT = 'scannerx:app-navigation';
const APP_PROGRESS_EVENT = 'scannerx:app-progress';

const routeStartListeners = new Set();

let historyPatched = false;
let originalPushState = null;
let originalReplaceState = null;
let popstateHandler = null;

const emitRouteStart = (source = 'history') => {
  routeStartListeners.forEach((listener) => {
    listener({ source, timestamp: Date.now() });
  });
};

const patchHistory = () => {
  if (historyPatched || typeof window === 'undefined') {
    return;
  }

  historyPatched = true;

  originalPushState = window.history.pushState;
  originalReplaceState = window.history.replaceState;

  window.history.pushState = function patchedPushState(...args) {
    emitRouteStart('pushState');
    return originalPushState.apply(this, args);
  };

  window.history.replaceState = function patchedReplaceState(...args) {
    emitRouteStart('replaceState');
    return originalReplaceState.apply(this, args);
  };

  popstateHandler = () => emitRouteStart('popstate');
  window.addEventListener('popstate', popstateHandler);
};

export const subscribeToRouteStarts = (listener) => {
  patchHistory();
  routeStartListeners.add(listener);

  return () => {
    routeStartListeners.delete(listener);
  };
};

export const requestAppNavigation = ({ to, replace = false, reason = 'app-navigation', state = null }) => {
  if (typeof window === 'undefined') {
    return;
  }

  window.dispatchEvent(
    new CustomEvent(APP_NAVIGATION_EVENT, {
      detail: { to, replace, reason, state },
    }),
  );
};

export const subscribeToAppNavigation = (listener) => {
  if (typeof window === 'undefined') {
    return () => {};
  }

  const handler = (event) => {
    listener(event.detail || {});
  };

  window.addEventListener(APP_NAVIGATION_EVENT, handler);
  return () => {
    window.removeEventListener(APP_NAVIGATION_EVENT, handler);
  };
};

export const beginAppProgress = (source) => {
  if (typeof window === 'undefined' || !source) {
    return;
  }

  window.dispatchEvent(
    new CustomEvent(APP_PROGRESS_EVENT, {
      detail: { source, action: 'start' },
    }),
  );
};

export const endAppProgress = (source) => {
  if (typeof window === 'undefined' || !source) {
    return;
  }

  window.dispatchEvent(
    new CustomEvent(APP_PROGRESS_EVENT, {
      detail: { source, action: 'end' },
    }),
  );
};

export const subscribeToAppProgress = (listener) => {
  if (typeof window === 'undefined') {
    return () => {};
  }

  const handler = (event) => {
    listener(event.detail || {});
  };

  window.addEventListener(APP_PROGRESS_EVENT, handler);
  return () => {
    window.removeEventListener(APP_PROGRESS_EVENT, handler);
  };
};
