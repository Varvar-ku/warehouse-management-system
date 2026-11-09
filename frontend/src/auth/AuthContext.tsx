import { createContext, useCallback, useContext, useEffect, useMemo, useState, type ReactNode } from 'react';
import { useQueryClient } from '@tanstack/react-query';
import { login as apiLogin } from '../api/auth';
import { setToken, setUnauthorizedHandler } from '../api/client';
import type { Role, UserResponse } from '../api/types';

interface Session {
  token: string;
  user: UserResponse;
  expMs: number;
}

interface AuthState {
  user: UserResponse | null;
  roles: Role[];
  /** true, если прошлая сессия закончилась по сроку: показать подсказку на экране входа. */
  expired: boolean;
  hasRole: (...roles: Role[]) => boolean;
  login: (login: string, password: string) => Promise<void>;
  logout: () => void;
}

// sessionStorage, а не localStorage: токен живёт до закрытия вкладки и не доступен другим вкладкам
const STORAGE_KEY = 'wms.session';
const AuthContext = createContext<AuthState | null>(null);

function jwtExpMs(token: string): number | null {
  try {
    const payload = token.split('.')[1].replace(/-/g, '+').replace(/_/g, '/');
    const exp = JSON.parse(atob(payload)).exp;
    return typeof exp === 'number' ? exp * 1000 : null;
  } catch {
    return null;
  }
}

function loadSession(): Session | null {
  try {
    const raw = sessionStorage.getItem(STORAGE_KEY);
    const s = raw ? (JSON.parse(raw) as Session) : null;
    return s && s.expMs > Date.now() ? s : null;
  } catch {
    return null;
  }
}

export function AuthProvider({ children }: { children: ReactNode }) {
  const queryClient = useQueryClient();
  const [session, setSession] = useState<Session | null>(() => {
    const s = loadSession();
    setToken(s?.token ?? null); // до первого рендера детей, чтобы их запросы сразу шли с токеном
    return s;
  });
  const [expired, setExpired] = useState(false);

  const endSession = useCallback(
    (wasExpired: boolean) => {
      sessionStorage.removeItem(STORAGE_KEY);
      setToken(null);
      setSession(null);
      setExpired(wasExpired);
      queryClient.clear(); // данные прошлого пользователя не должны попасть к следующему
    },
    [queryClient],
  );

  useEffect(() => {
    setUnauthorizedHandler(() => endSession(true));
  }, [endSession]);

  useEffect(() => {
    if (!session) return;
    const timer = setTimeout(() => endSession(true), Math.max(session.expMs - Date.now(), 0));
    return () => clearTimeout(timer);
  }, [session, endSession]);

  const login = useCallback(async (loginName: string, password: string) => {
    const res = await apiLogin(loginName, password);
    const s: Session = {
      token: res.accessToken,
      user: res.user,
      expMs: jwtExpMs(res.accessToken) ?? Date.now() + res.expiresIn * 1000,
    };
    sessionStorage.setItem(STORAGE_KEY, JSON.stringify(s));
    setToken(s.token);
    setExpired(false);
    setSession(s);
  }, []);

  const value = useMemo<AuthState>(() => {
    const roles = session?.user.roles ?? [];
    return {
      user: session?.user ?? null,
      roles,
      expired,
      hasRole: (...wanted) => wanted.some((r) => roles.includes(r)),
      login,
      logout: () => endSession(false),
    };
  }, [session, expired, login, endSession]);

  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>;
}

export function useAuth(): AuthState {
  const ctx = useContext(AuthContext);
  if (!ctx) throw new Error('useAuth должен вызываться внутри AuthProvider');
  return ctx;
}
