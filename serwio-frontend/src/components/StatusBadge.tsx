import type { TechnicianAccountStatus } from '../types';

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

const SERVICE_ORDER_STATUS: Record<string, { label: string; tone: string }> = {
  NEW: { label: 'Nowe', tone: 'info' },
  AWAITING_TECHNICIAN_REVIEW: { label: 'Oczekuje na serwisanta', tone: 'warning' },
  AWAITING_OWNER_CONFIRMATION: { label: 'Oczekuje na potwierdzenie właściciela', tone: 'warning' },
};

export function ServiceOrderStatusBadge({ status }: { status: string }) {
  const { label, tone } = SERVICE_ORDER_STATUS[status] ?? { label: status, tone: 'neutral' };
  return <span className={`badge badge-${tone}`}>{label}</span>;
}
