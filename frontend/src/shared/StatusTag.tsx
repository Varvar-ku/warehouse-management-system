import { Tag } from 'antd';
import { REQUEST_STATUS, type RequestStatus } from './statuses';

export default function StatusTag({ status }: { status: RequestStatus }) {
  const view = REQUEST_STATUS[status];
  return <Tag color={view?.color}>{view?.label ?? status}</Tag>;
}
