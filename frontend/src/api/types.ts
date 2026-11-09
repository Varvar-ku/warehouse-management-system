export type Role = 'USER' | 'STOREKEEPER' | 'WAREHOUSE_WORKER' | 'ADMIN';

export interface UserResponse {
  id: string;
  login: string;
  fullName: string;
  email: string | null;
  phone: string | null;
  active: boolean;
  roles: Role[];
  createdAt: string;
}

export interface TokenResponse {
  accessToken: string;
  tokenType: string;
  expiresIn: number;
  user: UserResponse;
}

/** Страница списка (common-lib PageResponse). Нумерация страниц с нуля. */
export interface Page<T> {
  content: T[];
  page: number;
  size: number;
  totalElements: number;
  totalPages: number;
}
