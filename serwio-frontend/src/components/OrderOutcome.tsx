import { Ban, CheckCircle2, FileText } from 'lucide-react';
import { useState } from 'react';
import { ApiError, errorMessage } from '../api';
import { useEndServiceOrder, useOrderVisits, useServiceReport } from '../queries';
import type { ServiceOrderResponse, ServiceReportResponse } from '../types';
import { formatMoment } from '../visitTime';
import { ErrorState } from './DataState';
import { ConfirmDialog } from './Modal';
import { ServiceOrderStatusBadge } from './StatusBadge';
import { useToast } from './Toast';

const CANCELLABLE_STATUSES = [
  'NEW', 'ASSIGNED', 'AWAITING_SCHEDULING', 'VISIT_CONFIRMED', 'AWAITING_TECHNICIAN_REVIEW', 'AWAITING_OWNER_CONFIRMATION',
];

const REPORT_SECTIONS: { key: keyof ServiceReportResponse; label: string; optional?: boolean }[] = [
  { key: 'actionsDone', label: 'Wykonane czynności' },
  { key: 'diagnosis', label: 'Diagnoza' },
  { key: 'resolution', label: 'Rozwiązanie i wynik' },
  { key: 'usedMaterials', label: 'Użyte materiały', optional: true },
  { key: 'recommendations', label: 'Zalecenia', optional: true },
];

function statusSummary(order: ServiceOrderResponse, company: boolean, timeZone: string): string {
  const closedAt = order.closedAt ? formatMoment(order.closedAt, timeZone) : null;
  switch (order.currentStatus) {
    case 'CLOSED':
      return closedAt ? `Zlecenie zamknięte ${closedAt}.` : 'Zlecenie zamknięte.';
    case 'CANCELLED':
      return closedAt ? `Zlecenie anulowane ${closedAt}.` : 'Zlecenie anulowane.';
    case 'COMPLETED':
      if (!order.reportFinalized) return 'Prace są wykonane. Czeka na finalizację raportu przez jego autora.';
      return company ? 'Raport jest sfinalizowany. Możesz zamknąć zlecenie.' : 'Raport jest gotowy. Firma serwisowa zamknie zlecenie.';
    default:
      return 'Zlecenie jest w realizacji. Raport pojawi się po zakończeniu prac.';
  }
}

export function OrderOutcomeCard({ order, role }: { order: ServiceOrderResponse; role: string }) {
  const company = role === 'SERVICE_COMPANY';
  const report = useServiceReport(order.id, order.reportFinalized);
  const visits = useOrderVisits(order.id);
  const end = useEndServiceOrder(order.id);
  const toast = useToast();
  const [confirming, setConfirming] = useState<'cancel' | 'close' | null>(null);
  const timeZone = order.timezone;
  const open = order.closedAt === null;
  const started = visits.data?.visits.some((v) => v.startedAt !== null) ?? false;
  const canCancel = company && open && visits.isSuccess && CANCELLABLE_STATUSES.includes(order.currentStatus) && !started;
  const showClose = company && open && order.currentStatus === 'COMPLETED';

  function run() {
    if (!confirming) return;
    const action = confirming;
    end.mutate(action, {
      onSuccess: () => toast.success(action === 'close' ? 'Zamknięto zlecenie.' : 'Anulowano zlecenie.'),
      onError: (e) => toast.error(e instanceof ApiError && (e.status === 409 || e.status === 404)
        ? `${e.message} Wyświetlono aktualne dane.`
        : errorMessage(e)),
      onSettled: () => setConfirming(null),
    });
  }

  return (
    <section className="card details-card assignment-card">
      <header className="assignment-header">
        <h2 className="details-heading">Wynik zlecenia</h2>
        <ServiceOrderStatusBadge status={order.currentStatus} />
      </header>
      <p className="outcome-summary">{statusSummary(order, company, timeZone)}</p>

      {(showClose || canCancel) && (
        <div className="visit-actions outcome-actions">
          {showClose && (
            <button type="button" className="btn btn-primary btn-sm" onClick={() => setConfirming('close')} disabled={!order.reportFinalized}>
              <CheckCircle2 size={14} aria-hidden /> Zamknij zlecenie
            </button>
          )}
          {canCancel && (
            <button type="button" className="btn btn-secondary btn-sm" onClick={() => setConfirming('cancel')}>
              <Ban size={14} aria-hidden /> Anuluj zlecenie
            </button>
          )}
        </div>
      )}
      {showClose && !order.reportFinalized && (
        <p className="field-hint">Zamknięcie będzie dostępne po finalizacji raportu.</p>
      )}
      {company && open && started && order.currentStatus !== 'COMPLETED' && (
        <p className="field-hint">Realizacja się rozpoczęła, więc zlecenia nie można już anulować.</p>
      )}

      <div className="report">
        <h3 className="report-heading"><FileText size={14} aria-hidden /> Raport serwisowy</h3>
        {!order.reportFinalized ? (
          <p className="muted">
            {order.currentStatus === 'CANCELLED' ? 'Zlecenie anulowano bez raportu.' : 'Raport nie został jeszcze sfinalizowany.'}
          </p>
        ) : report.isPending ? (
          <span className="skeleton skeleton-line" />
        ) : report.isError ? (
          <ErrorState message={errorMessage(report.error)} onRetry={() => void report.refetch()} />
        ) : (
          <>
            <dl className="details-list">
              {REPORT_SECTIONS.filter((s) => !s.optional || report.data[s.key]).map((s) => (
                <div key={s.key}>
                  <dt>{s.label}</dt>
                  <dd className="details-text">{report.data[s.key] ?? '—'}</dd>
                </div>
              ))}
            </dl>
            {report.data.finalizedAt && (
              <p className="field-hint">Sfinalizowano {formatMoment(report.data.finalizedAt, timeZone)}.</p>
            )}
          </>
        )}
      </div>

      {confirming && (
        <ConfirmDialog
          title={confirming === 'close' ? 'Zamknąć zlecenie?' : 'Anulować zlecenie?'}
          message={confirming === 'close'
            ? <>Zlecenie trafi do zakończonych, a przydziały oddziałów i serwisantów się zakończą. Raport i historia wizyt zostaną zachowane.</>
            : <>Zlecenie trafi do zakończonych, a zaplanowana wizyta zostanie anulowana. Tej operacji nie można cofnąć.</>}
          confirmLabel={confirming === 'close' ? 'Zamknij zlecenie' : 'Anuluj zlecenie'}
          pendingLabel={confirming === 'close' ? 'Zamykanie…' : 'Anulowanie…'}
          cancelLabel="Wróć"
          pending={end.isPending}
          onConfirm={run}
          onCancel={() => setConfirming(null)}
        />
      )}
    </section>
  );
}
