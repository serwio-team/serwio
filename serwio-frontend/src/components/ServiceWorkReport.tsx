import { FileText, Loader2 } from 'lucide-react';
import { useState } from 'react';
import { ApiError, errorMessage } from '../api';
import { useCurrentTechnician, useServiceReport, useUpdateServiceReport } from '../queries';
import type { ReportDraftRequest, ServiceReport } from '../types';
import { EmptyState, ErrorState } from './DataState';
import { TextareaField } from './Field';

type ReportField = 'actionsDone' | 'diagnosis' | 'resolution';

function reportFields(orderType: string): [ReportField, string][] {
  return [
    ['actionsDone', 'Wykonane czynności'],
    ['diagnosis', 'Diagnoza'],
    ['resolution', orderType === 'INSPECTION' ? 'Wynik przeglądu' : 'Rozwiązanie'],
  ];
}

export function ServiceWorkReport({ orderId, orderStatus, orderType, coordinatorId, userId }: {
  orderId: string;
  orderStatus: string;
  orderType: string;
  coordinatorId?: string;
  userId: string;
}) {
  const profile = useCurrentTechnician();
  const query = useServiceReport(userId, orderId);
  const update = useUpdateServiceReport(userId, orderId);
  const report = query.data;
  const open = orderStatus !== 'CLOSED' && orderStatus !== 'CANCELLED';
  const canEdit = open && profile.isSuccess && profile.data.id === (report?.authorTechnicianId ?? coordinatorId);

  return (
    <section className="work-report card details-card" aria-labelledby="work-report-title">
      <h2 className="details-heading" id="work-report-title">Raport serwisowy</h2>
      {update.error && (
        <p className="inline-alert inline-alert-error" role="alert">{errorMessage(update.error)}</p>
      )}
      {query.isError ? (
        <ErrorState message={errorMessage(query.error)} onRetry={() => void query.refetch()} />
      ) : query.isPending || (open && !report?.finalizedAt && profile.isPending) ? (
        <div className="visit-loading" role="status">
          <Loader2 size={16} className="spin" aria-hidden /> Ładowanie raportu…
        </div>
      ) : report?.finalizedAt ? (
        <FinalReport report={report} orderType={orderType} />
      ) : open && profile.isError ? (
        <ErrorState message="Nie udało się sprawdzić uprawnień do edycji raportu." onRetry={() => void profile.refetch()} />
      ) : canEdit ? (
        <ReportForm report={report ?? null} orderType={orderType} canFinalize={orderStatus === 'COMPLETED'} update={update} />
      ) : (
        <EmptyState icon={FileText} title="Brak dostępnego raportu"
          description="Szkic jest dostępny dla jego autora. Sfinalizowany raport pojawi się tutaj po zapisaniu." />
      )}
    </section>
  );
}

function ReportForm({ report, orderType, canFinalize, update }: {
  report: ServiceReport | null;
  orderType: string;
  canFinalize: boolean;
  update: ReturnType<typeof useUpdateServiceReport>;
}) {
  const [edits, setEdits] = useState<Partial<Record<ReportField, string>>>({});
  const [validationErrors, setValidationErrors] = useState<Record<string, string[]>>({});
  const fields = reportFields(orderType);
  const values = {
    actionsDone: edits.actionsDone ?? report?.actionsDone ?? '',
    diagnosis: edits.diagnosis ?? report?.diagnosis ?? '',
    resolution: edits.resolution ?? report?.resolution ?? '',
  };
  const draft: ReportDraftRequest = {
    actionsDone: values.actionsDone.trim() || null,
    diagnosis: values.diagnosis.trim() || null,
    resolution: values.resolution.trim() || null,
    usedMaterials: report?.usedMaterials ?? null,
    recommendations: report?.recommendations ?? null,
  };
  const serverErrors = update.error instanceof ApiError ? update.error.fieldErrors : {};

  function finalize() {
    if (update.isPending) return;
    update.reset();
    const errors = Object.fromEntries(fields.filter(([key]) => !draft[key])
      .map(([key]) => [key, ['Pole jest wymagane do finalizacji.']]));
    setValidationErrors(errors);
    if (Object.keys(errors).length > 0) return;

    const needsSave = !report || fields.some(([key]) => draft[key] !== report[key]);
    update.mutate({ action: 'finalize', draft: needsSave ? draft : undefined });
  }

  return (
    <form noValidate onSubmit={(event) => {
      event.preventDefault();
      if (update.isPending) return;
      setValidationErrors({});
      update.mutate({ action: 'save', draft }, { onSuccess: () => setEdits({}) });
    }}>
      {report && <span className="badge badge-neutral">Szkic</span>}
      <p className="work-report-hint muted">
        {canFinalize ? 'Uzupełnij wszystkie pola przed finalizacją.'
          : 'Zapisz szkic w trakcie pracy. Finalizacja będzie dostępna po wykonaniu zlecenia.'}
      </p>
      {update.isSuccess && <p className="inline-alert inline-alert-info" role="status">Zapisano szkic raportu.</p>}
      {Object.keys(validationErrors).length > 0 && (
        <p className="inline-alert inline-alert-error" role="alert">Uzupełnij wymagane pola, aby sfinalizować raport.</p>
      )}
      {fields.map(([key, label]) => (
        <TextareaField key={key} name={key} label={label} value={values[key]} required={canFinalize}
          maxLength={100000} rows={3} disabled={update.isPending}
          errors={validationErrors[key] ?? serverErrors[key]}
          onChange={(value) => {
            setEdits((previous) => ({ ...previous, [key]: value }));
            setValidationErrors({});
            update.reset();
          }} />
      ))}
      <div className="page-actions">
        <button type="submit" className={`btn ${canFinalize ? 'btn-secondary' : 'btn-primary'}`} disabled={update.isPending}>
          Zapisz szkic
        </button>
        {canFinalize && (
          <button type="button" className="btn btn-primary" disabled={update.isPending} onClick={finalize}>
            Sfinalizuj raport
          </button>
        )}
      </div>
      {update.isPending && (
        <p className="work-report-hint muted" role="status">
          {update.variables?.action === 'finalize' ? 'Finalizowanie raportu…' : 'Zapisywanie szkicu…'}
        </p>
      )}
    </form>
  );
}

function FinalReport({ report, orderType }: { report: ServiceReport; orderType: string }) {
  const fields: [keyof ReportDraftRequest, string][] = [
    ...reportFields(orderType),
    ['usedMaterials', 'Użyte materiały'],
    ['recommendations', 'Zalecenia'],
  ];

  return (
    <>
      <p className="work-report-hint"><span className="badge badge-success">Raport sfinalizowany</span></p>
      <dl className="details-list">
        {fields.map(([key, label]) => report[key] && (
          <div key={key}>
            <dt>{label}</dt>
            <dd className="details-text">{report[key]}</dd>
          </div>
        ))}
      </dl>
    </>
  );
}
