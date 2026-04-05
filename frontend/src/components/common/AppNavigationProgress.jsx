import { useEffect, useEffectEvent, useRef, useState } from 'react';
import { useLocation, useNavigate } from 'react-router-dom';
import { useAuth } from '../../context/AuthContext';
import { subscribeToAppNavigation, subscribeToAppProgress, subscribeToRouteStarts } from '../../lib/appNavigation';

const PUBLIC_PATHS = new Set(['/', '/login', '/register', '/terms']);
const START_PROGRESS = 14;
const MAX_PROGRESS = 88;
const MIN_VISIBLE_MS = 220;
const COMPLETE_ANIMATION_MS = 180;
const TRICKLE_INTERVAL_MS = 140;

const isProtectedPath = (pathname) => !PUBLIC_PATHS.has(pathname);

const TopLoadingBar = ({ visible, progress }) => (
  <div className="pointer-events-none fixed inset-x-0 top-0 z-[120] h-[3px]">
    <div
      className="h-full origin-left rounded-r-full bg-[#60dfb2] shadow-[0_0_18px_rgba(96,223,178,0.9)] transition-[transform,opacity] duration-200 ease-out will-change-transform"
      style={{
        opacity: visible ? 1 : 0,
        transform: `scaleX(${progress / 100})`,
      }}
    />
  </div>
);

const AppNavigationProgress = () => {
  const location = useLocation();
  const navigate = useNavigate();
  const { isLoading, token, isAdmin } = useAuth();
  const [barState, setBarState] = useState({
    visible: false,
    progress: 0,
    isCompleting: false,
  });

  const pendingSourcesRef = useRef(new Set());
  const visibleSinceRef = useRef(0);
  const stateRef = useRef(barState);
  const mountedRef = useRef(false);
  const locationRef = useRef(`${location.pathname}${location.search}${location.hash}`);
  const trickleTimerRef = useRef(null);
  const completeTimerRef = useRef(null);
  const hideTimerRef = useRef(null);

  useEffect(() => {
    stateRef.current = barState;
  }, [barState]);

  useEffect(() => {
    locationRef.current = `${location.pathname}${location.search}${location.hash}`;
  }, [location.pathname, location.search, location.hash]);

  const clearTimer = (timerRef) => {
    if (timerRef.current) {
      window.clearTimeout(timerRef.current);
      timerRef.current = null;
    }
  };

  const stopTrickle = () => {
    if (trickleTimerRef.current) {
      window.clearInterval(trickleTimerRef.current);
      trickleTimerRef.current = null;
    }
  };

  const startTrickle = () => {
    if (trickleTimerRef.current) {
      return;
    }

    trickleTimerRef.current = window.setInterval(() => {
      if (!pendingSourcesRef.current.size) {
        stopTrickle();
        return;
      }

      setBarState((previous) => {
        if (!previous.visible || previous.isCompleting) {
          return previous;
        }

        const remaining = MAX_PROGRESS - previous.progress;
        if (remaining <= 0.5) {
          return previous;
        }

        const nextProgress = Math.min(
          MAX_PROGRESS,
          previous.progress + Math.max(remaining * 0.12, 1.8),
        );

        return {
          ...previous,
          progress: nextProgress,
        };
      });
    }, TRICKLE_INTERVAL_MS);
  };

  const scheduleCompletion = useEffectEvent(() => {
    stopTrickle();

    if (!stateRef.current.visible || completeTimerRef.current) {
      return;
    }

    const elapsed = Date.now() - visibleSinceRef.current;
    const delay = Math.max(MIN_VISIBLE_MS - elapsed, 0);

    completeTimerRef.current = window.setTimeout(() => {
      completeTimerRef.current = null;
      setBarState((previous) => ({
        ...previous,
        visible: true,
        progress: 100,
        isCompleting: true,
      }));

      hideTimerRef.current = window.setTimeout(() => {
        hideTimerRef.current = null;
        visibleSinceRef.current = 0;
        setBarState({
          visible: false,
          progress: 0,
          isCompleting: false,
        });
      }, COMPLETE_ANIMATION_MS);
    }, delay);
  });

  const beginProgress = useEffectEvent((source) => {
    const pendingSources = pendingSourcesRef.current;
    if (pendingSources.has(source)) {
      return;
    }

    pendingSources.add(source);
    clearTimer(completeTimerRef);
    clearTimer(hideTimerRef);

    if (!visibleSinceRef.current) {
      visibleSinceRef.current = Date.now();
    }

    setBarState((previous) => ({
      visible: true,
      progress: previous.visible ? Math.max(previous.progress, START_PROGRESS) : START_PROGRESS,
      isCompleting: false,
    }));

    startTrickle();
  });

  const endProgress = useEffectEvent((source) => {
    const pendingSources = pendingSourcesRef.current;
    if (!pendingSources.has(source)) {
      return;
    }

    pendingSources.delete(source);

    if (!pendingSources.size) {
      scheduleCompletion();
    }
  });

  useEffect(() => {
    const unsubscribeRouteStart = subscribeToRouteStarts(() => {
      beginProgress('route');
    });

    const unsubscribeAppProgress = subscribeToAppProgress(({ source, action }) => {
      if (!source) {
        return;
      }

      const progressSource = `app:${source}`;

      if (action === 'start') {
        beginProgress(progressSource);
        return;
      }

      if (action === 'end') {
        endProgress(progressSource);
      }
    });

    const unsubscribeAppNavigation = subscribeToAppNavigation(({ to, replace = false, state = null }) => {
      beginProgress('route');

      if (!to || to === locationRef.current) {
        endProgress('route');
        return;
      }

      navigate(to, { replace, state });
    });

    return () => {
      unsubscribeRouteStart();
      unsubscribeAppProgress();
      unsubscribeAppNavigation();
      stopTrickle();
      clearTimer(completeTimerRef);
      clearTimer(hideTimerRef);
    };
  }, [navigate]);

  useEffect(() => {
    const currentPath = location.pathname;
    const waitingForAuthRedirect = isProtectedPath(currentPath) && !isLoading && !token;
    const waitingForRoleRedirect = currentPath === '/admin' && !isLoading && Boolean(token) && !isAdmin;

    const syncTimer = window.requestAnimationFrame(() => {
      if ((isProtectedPath(currentPath) && isLoading) || waitingForAuthRedirect || waitingForRoleRedirect) {
        beginProgress('auth');
        return;
      }

      endProgress('auth');
    });

    return () => {
      window.cancelAnimationFrame(syncTimer);
    };
  }, [isAdmin, isLoading, location.pathname, token]);

  useEffect(() => {
    if (!mountedRef.current) {
      mountedRef.current = true;
      return;
    }

    const frame = window.requestAnimationFrame(() => {
      endProgress('route');
    });

    return () => {
      window.cancelAnimationFrame(frame);
    };
  }, [location.pathname, location.search, location.hash]);

  return <TopLoadingBar visible={barState.visible} progress={barState.progress} />;
};

export default AppNavigationProgress;
