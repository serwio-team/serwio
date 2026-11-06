import type { ServiceOrderStatus, TechnicianAccountStatus } from '../types';

const ACCOUNT_STATUS: Record<TechnicianAccountStatus, { label: string; tone: string }> = {
  PENDING: { label: 'Oczekuje na aktywację', tone: 'warning' },
  ACTIVE: { label: 'Aktywne', tone: 'success' },
  INACTIVE: { label: 'Nieaktywne', tone: 'neutral' },
};

export function AccountStatusBadge({ status }: { status: TechnicianAccountStatus }) {
  const { label, tone } = ACCOUNT_STATUS[status] ?? { label: status, tone: 'neutral' };
  return <span className={`badge badge-${tone}`}>{label}</span>;
}

export function BranchStatusBadge({ active }: { active: boolean }) {
  return active
    ? <span className="badge badge-success">Aktywny</span>
    : <span className="badge badge-neutral">Nieaktywny</span>;
}

const SERVICE_ORDER_STATUS: Record<ServiceOrderStatus, { label: string; tone: string }> = {
  NEW: { label: 'Nowe', tone: 'info' },
  AWAITING_TECHNICIAN_REVIEW: { label: 'Oczekuje na serwisanta', tone: 'warning' },
  AWAITING_OWNER_CONFIRMATION: { label: 'Oczekuje na potwierdzenie właściciela', tone: 'warning' },
  CLOSED: { label: 'Zamknięte', tone: 'success' },
  CANCELLED: { label: 'Anulowane', tone: 'neutral' },
};

export const ACTIVE_ORDER_STATUSES: ServiceOrderStatus[] = ['NEW', 'AWAITING_TECHNICIAN_REVIEW', 'AWAITING_OWNER_CONFIRMATION'];

export const FINISHED_ORDER_STATUSES: ServiceOrderStatus[] = ['CLOSED', 'CANCELLED'];

export function serviceOrderStatusLabel(status: string): string {
  return SERVICE_ORDER_STATUS[status as ServiceOrderStatus]?.label ?? status;
}

export function ServiceOrderStatusBadge({ status }: { status: string }) {
  const { label, tone } = SERVICE_ORDER_STATUS[status as ServiceOrderStatus] ?? { label: status, tone: 'neutral' };
  return <span className={`badge badge-${tone}`}>{label}</span>;
}
