import { ArrowRight, Building2, Plus, UserPlus, Users } from 'lucide-react';
import { Link, useNavigate } from 'react-router-dom';
import { errorMessage } from '../api';
import { ErrorState, PageHeader } from '../components/DataState';
import { useCompanySummary, useCurrentCompany } from '../queries';
import type { CompanySummaryResponse } from '../types';

function pendingMessage(n: number): string {
  if (n === 1) return '1 serwisant nie zmienił jeszcze hasła startowego.';
  const few = n % 10 >= 2 && n % 10 <= 4 && (n % 100 < 12 || n % 100 > 14);
  return few
    ? `${n} serwisanci nie zmienili jeszcze hasła startowego.`
    : `${n} serwisantów nie zmieniło jeszcze hasła startowego.`;
}

function Stat({ label, value, tone }: { label: string; value?: number; tone?: string }) {
  return (
    <div className="stat">
      <span className={`stat-value${tone && value ? ` stat-${tone}` : ''}`}>
        {value ?? <span className="skeleton skeleton-stat" />}
      </span>
      <span className="stat-label">{label}</span>
    </div>
  );
}

function SummaryCards({ summary }: { summary?: CompanySummaryResponse }) {
  return (
    <div className="summary-grid">
      <section className="card summary-card">
        <header className="summary-header">
          <span className="cell-icon" aria-hidden><Building2 size={14} /></span>
          <h2>Oddziały</h2>
          <Link to="/company/branches" className="summary-link">Wszystkie <ArrowRight size={14} aria-hidden /></Link>
        </header>
        <div className="stats">
          <Stat label="Aktywne" value={summary?.activeBranchCount} tone="success" />
          <Stat label="Nieaktywne" value={summary?.inactiveBranchCount} />
        </div>
      </section>
      <section className="card summary-card">
        <header className="summary-header">
          <span className="cell-icon" aria-hidden><Users size={14} /></span>
          <h2>Serwisanci</h2>
          <Link to="/company/technicians" className="summary-link">Wszyscy <ArrowRight size={14} aria-hidden /></Link>
        </header>
        <div className="stats">
          <Stat label="Aktywni" value={summary?.activeTechnicianCount} tone="success" />
          <Stat label="Oczekujący na aktywację" value={summary?.pendingTechnicianCount} tone="warning" />
          <Stat label="Nieaktywni" value={summary?.inactiveTechnicianCount} />
        </div>
      </section>
    </div>
  );
}

export function CompanyDashboardPage() {
  const navigate = useNavigate();
  const company = useCurrentCompany();
  const summary = useCompanySummary();
  const pending = summary.data?.pendingTechnicianCount ?? 0;

  return (
    <>
      <PageHeader
        title={company.data?.name ?? 'Pulpit'}
        description="Podsumowanie oddziałów i serwisantów Twojej firmy."
        action={
          <div className="page-actions">
            <button
              type="button"
              className="btn btn-secondary"
              onClick={() => navigate('/company/branches', { state: { create: true } })}
            >
              <Plus size={14} aria-hidden /> Dodaj oddział
            </button>
            <button
              type="button"
              className="btn btn-primary"
              onClick={() => navigate('/company/technicians', { state: { create: true } })}
            >
              <UserPlus size={14} aria-hidden /> Dodaj serwisanta
            </button>
          </div>
        }
      />

      {summary.isError ? (
        <section className="card">
          <ErrorState message={errorMessage(summary.error)} onRetry={() => void summary.refetch()} />
        </section>
      ) : (
        <>
          {pending > 0 && (
            <div className="inline-alert inline-alert-warning notice">
              <span>{pendingMessage(pending)}</span>
              <Link to="/company/technicians" className="notice-link">Pokaż serwisantów</Link>
            </div>
          )}
          <SummaryCards summary={summary.data} />
        </>
      )}
    </>
  );
}
