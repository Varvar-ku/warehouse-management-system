/* Типы по PickingDtos.java. Значения enum смотрите в statuses.ts. */

export interface PickingItem {
  id: string;
  requestItemId: string;
  lineNo: number;
  productSku: string;
  productName: string;
  unitCode: string;
  sectionId: string;
  sectionCode: string;
  batchNumber: string | null;
  requestedQuantity: number;
  pickedQuantity: number | null;
  status: string;
  assignedWorkerId: string | null;
  comment: string | null;
  pickedAt: string | null;
}

export interface PickingWorker {
  id: string;
  workerId: string;
  role: string;
  status: string;
  assignedAt: string | null;
  startedAt: string | null;
  completedAt: string | null;
  comment: string | null;
}

export interface WorkerProgress {
  workerId: string;
  role: string;
  status: string;
  assignedQuantity: number;
  pickedQuantity: number;
  itemsTotal: number;
  itemsProcessed: number;
  percent: number;
}

export interface Progress {
  totalQuantity: number;
  unassignedQuantity: number;
  processedQuantity: number;
  pickedQuantity: number;
  itemsTotal: number;
  itemsProcessed: number;
  percent: number;
  workers: WorkerProgress[];
}

export interface PickingTask {
  id: string;
  number: string;
  requestId: string;
  requestNumber: string;
  status: string;
  createdAt: string;
  completedAt: string | null;
  workers: PickingWorker[];
  items: PickingItem[];
  progress: Progress | null;
}

export interface WorkerInput {
  workerId: string;
}

export interface CreatePickingTaskBody {
  requestId: string;
  workers: WorkerInput[];
  autoAssignRemaining: boolean;
}

export interface AddWorkersBody {
  workers: WorkerInput[];
  autoAssignRemaining: boolean;
}

export interface AssignmentInput {
  requestItemId: string;
  workerId: string;
  quantity: number;
}

export interface CompleteTaskBody {
  confirmShortage: boolean;
  comment?: string;
}
