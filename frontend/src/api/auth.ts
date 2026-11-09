import { api } from './client';
import type { TokenResponse, UserResponse } from './types';

export const login = (loginName: string, password: string) =>
  api<TokenResponse>('/auth/login', { method: 'POST', body: { login: loginName, password } });

export const fetchMe = () => api<UserResponse>('/me');

export const changePassword = (currentPassword: string, newPassword: string) =>
  api<void>('/me/password', { method: 'POST', body: { currentPassword, newPassword } });
