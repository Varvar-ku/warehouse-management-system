import { api } from '../../api/client';
import type { ReportPage } from './types';

export type ReportParams = Record<string, string | number | boolean | undefined>;

export const fetchReport = (path: string, params: ReportParams) =>
  api<ReportPage>(path.replace(/^\/api\/v1/, ''), { query: { ...params } });
