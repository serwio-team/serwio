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
