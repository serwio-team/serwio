import { ArrowLeft, Pencil, Plus, PowerOff, SearchX } from 'lucide-react';
import { useState } from 'react';
import { Link, useParams } from 'react-router-dom';
import { ApiError, errorMessage } from '../api';
import { BranchDeactivateDialog, BranchFormModal } from '../components/BranchForm';
import { EmptyState, ErrorState } from '../components/DataState';
import { BranchStatusBadge } from '../components/StatusBadge';
import { TechnicianCreateModal } from '../components/TechnicianForm';
import { TechnicianTable } from '../components/TechnicianTable';
import { useBranch } from '../queries';

export function BranchDetailsPage() {
  const { id } = useParams<{ id: string }>();
  const branch = useBranch(id);
  const [renaming, setRenaming] = useState(false);
  const [deactivating, setDeactivating] = useState(false);
  const [creating, setCreating] = useState(false);

  const backLink = (
    <Link to="/company/branches" className="back-link">
      <ArrowLeft size={14} aria-hidden /> Oddziały
    </Link>
  );

  if (branch.isError) {
    const notFound = branch.error instanceof ApiError && branch.error.status === 404;
    return (
      <>
        {backLink}
        <section className="card">
          {notFound ? (
            <EmptyState
              icon={SearchX}
              title="Nie znaleziono oddziału"
              description="Ten oddział nie istnieje albo należy do innej firmy."
            />
          ) : (
            <ErrorState message={errorMessage(branch.error)} onRetry={() => void branch.refetch()} />
          )}
        </section>
      </>
    );
  }

  const data = branch.data;
  const addButton = data?.active ? (
    <button type="button" className="btn btn-primary" onClick={() => setCreating(true)}>
      <Plus size={14} aria-hidden /> Dodaj serwisanta
    </button>
  ) : undefined;

  return (
    <>
      {backLink}
      <header className="page-header">
        <div>
          {data ? (
            <h1 className="title-with-badge">{data.name} <BranchStatusBadge active={data.active} /></h1>
          ) : (
            <h1><span className="skeleton skeleton-title" /></h1>
          )}
          <p>
            {data
              ? `Aktywni serwisanci: ${data.activeTechnicianCount} · Utworzono ${new Date(data.createdAt).toLocaleDateString('pl-PL')}`
              : ' '}
          </p>
        </div>
        {data && (
          <div className="page-actions">
            <button type="button" className="btn btn-secondary" onClick={() => setRenaming(true)}>
              <Pencil size={14} aria-hidden /> Zmień nazwę
            </button>
            {data.active && (
              <button type="button" className="btn btn-secondary" onClick={() => setDeactivating(true)}>
                <PowerOff size={14} aria-hidden /> Dezaktywuj
              </button>
            )}
            {addButton}
          </div>
        )}
      </header>

      {data && !data.active && (
        <div className="inline-alert inline-alert-info">
          Oddział jest nieaktywny. Nie można dodawać do niego serwisantów ani aktywować tych, którzy w nim zostali.
        </div>
      )}

      <section className="card">
        <h2 className="card-heading">Serwisanci oddziału</h2>
        {id && (
          <TechnicianTable
            branchId={id}
            showBranch={false}
            emptyTitle="Brak serwisantów w tym oddziale"
            emptyDescription={data?.active
              ? 'Dodaj serwisanta albo przenieś go z innego oddziału.'
              : 'W tym oddziale nie ma serwisantów.'}
            emptyAction={addButton}
          />
        )}
      </section>

      {renaming && data && <BranchFormModal branch={data} onClose={() => setRenaming(false)} />}
      {deactivating && data && <BranchDeactivateDialog branch={data} onClose={() => setDeactivating(false)} />}
      {creating && data && <TechnicianCreateModal defaultBranchId={data.id} onClose={() => setCreating(false)} />}
    </>
  );
}
