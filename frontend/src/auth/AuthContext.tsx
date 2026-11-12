import { createContext, useCallback, useContext, useEffect, useMemo, useState, type ReactNode } from 'react';
import { useQueryClient } from '@tanstack/react-query';
import { login as apiLogin } from '../api/auth';
import { api, refreshToken, setRefresher, setToken, setUnauthorizedHandler } from '../api/client';
import { ApiError } from '../api/errors';
import type { Role, UserResponse } from '../api/types';

interface Session {
  token: string;
  user: UserResponse;
  expMs: number;
}

/** Ответ POST /auth/refresh: то же, что у входа. Refresh-токен приходит в HttpOnly-cookie и скриптам недоступен. */
interface RefreshResponse {
  accessToken: string;
  tokenType: string;
  expiresIn: number;
  user: UserResponse;
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
/** Обновляем access-токен за минуту до истечения. */
const REFRESH_MARGIN_MS = 60_000;
/** Нижняя граница задержки: защита от частых обновлений при расхождении часов клиента и сервера. */
const MIN_REFRESH_DELAY_MS = 5_000;
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

function toSession(res: { accessToken: string; expiresIn: number; user: UserResponse }): Session {
  return {
    token: res.accessToken,
    user: res.user,
    expMs: jwtExpMs(res.accessToken) ?? Date.now() + res.expiresIn * 1000,
  };
}

/**
 * Сессия с просроченным access-токеном тоже восстанавливается: первый же запрос получит 401 и обновит токен
 * по refresh-cookie. Если и она истекла, пользователь попадёт на экран входа.
 */
function loadSession(): Session | null {
  try {
    const raw = sessionStorage.getItem(STORAGE_KEY);
    const s = raw ? (JSON.parse(raw) as Session) : null;
    return s && s.token && s.user ? s : null;
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

  // Обновление токена: новый токен, null (сервер отказал, сессия закончена) или исключение (временный сбой)
  const doRefresh = useCallback(async (): Promise<string | null> => {
    try {
      const res = await api<RefreshResponse>('/auth/refresh', { method: 'POST', anonymous: true });
      const s = toSession(res);
      sessionStorage.setItem(STORAGE_KEY, JSON.stringify(s));
      setToken(s.token);
      setSession(s);
      return s.token;
    } catch (e) {
      if (e instanceof ApiError && e.code === 'INVALID_REFRESH_TOKEN') return null;
      throw e;
    }
  }, []);

  useEffect(() => {
    setRefresher(doRefresh);
    return () => setRefresher(null);
  }, [doRefresh]);

  // Заблаговременное обновление: пользователь не замечает истечения короткого access-токена
  useEffect(() => {
    if (!session) return;
    const delay = Math.max(session.expMs - REFRESH_MARGIN_MS - Date.now(), MIN_REFRESH_DELAY_MS);
    const timer = setTimeout(() => {
      refreshToken()
        .then((t) => {
          if (t === null) endSession(true);
        })
        .catch(() => {
          /* временный сбой: следующий запрос с 401 повторит обновление */
        });
    }, delay);
    return () => clearTimeout(timer);
  }, [session, endSession]);

  const login = useCallback(async (loginName: string, password: string) => {
    const res = await apiLogin(loginName, password);
    const s = toSession(res);
    sessionStorage.setItem(STORAGE_KEY, JSON.stringify(s));
    setToken(s.token);
    setExpired(false);
    setSession(s);
  }, []);

  const logout = useCallback(() => {
    // сервер отзывает сессию и удаляет cookie; сбой сети не мешает выйти локально
    void api('/auth/logout', { method: 'POST', anonymous: true }).catch(() => {});
    endSession(false);
  }, [endSession]);

  const value = useMemo<AuthState>(() => {
    const roles = session?.user.roles ?? [];
    return {
      user: session?.user ?? null,
      roles,
      expired,
      hasRole: (...wanted) => wanted.some((r) => roles.includes(r)),
      login,
      logout,
    };
  }, [session, expired, login, logout]);

  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>;
}

export function useAuth(): AuthState {
  const ctx = useContext(AuthContext);
  if (!ctx) throw new Error('useAuth должен вызываться внутри AuthProvider');
  return ctx;
}
