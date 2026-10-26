import { Building2, Pencil, Plus, PowerOff } from 'lucide-react';
import { useEffect, useState } from 'react';
import { Link, useLocation as useRouterLocation, useNavigate } from 'react-router-dom';
import { errorMessage } from '../api';
import { BranchDeactivateDialog, BranchFormModal } from '../components/BranchForm';
import { EmptyState, ErrorState, LoadMore, PageHeader, TableSkeleton } from '../components/DataState';
import { BranchStatusBadge } from '../components/StatusBadge';
import { useBranchList } from '../queries';
import type { ServiceBranchResponse } from '../types';

type FormState = { mode: 'create' } | { mode: 'edit'; branch: ServiceBranchResponse } | null;

export function BranchesPage() {
  const routerLocation = useRouterLocation();
  const navigate = useNavigate();
  const list = useBranchList();
  const [form, setForm] = useState<FormState>(
    (routerLocation.state as { create?: boolean } | null)?.create ? { mode: 'create' } : null,
  );
  const [toDeactivate, setToDeactivate] = useState<ServiceBranchResponse | null>(null);

  useEffect(() => {
    if (routerLocation.state) navigate(routerLocation.pathname, { replace: true, state: null });
  }, [routerLocation, navigate]);

  const items = list.data?.pages.flat() ?? [];
  const addButton = (
    <button type="button" className="btn btn-primary" onClick={() => setForm({ mode: 'create' })}>
      <Plus size={14} aria-hidden /> Dodaj oddział
    </button>
  );

  return (
    <>
      <PageHeader title="Oddziały" description="Oddziały Twojej firmy, także nieaktywne." action={addButton} />

      <section className="card">
        {list.isError && !list.isFetchingNextPage ? (
          <ErrorState message={errorMessage(list.error)} onRetry={() => void list.refetch()} />
        ) : list.isSuccess && items.length === 0 ? (
          <EmptyState
            icon={Building2}
            title="Brak oddziałów"
            description="Dodaj oddział, aby przypisywać do niego serwisantów."
            action={addButton}
          />
        ) : (
          <div className="table-wrap">
            <table>
              <thead>
                <tr>
                  <th>Nazwa</th>
                  <th className="col-wide">Aktywni serwisanci</th>
                  <th>Status</th>
                  <th className="col-actions"><span className="sr-only">Akcje</span></th>
                </tr>
              </thead>
              {list.isPending ? (
                <TableSkeleton columns={4} />
              ) : (
                <tbody>
                  {items.map((b) => (
                    <tr key={b.id}>
                      <td>
                        <Link to={`/company/branches/${b.id}`} className="cell-title cell-link">
                          <span className="cell-icon" aria-hidden><Building2 size={14} /></span>
                          <span className="cell-text">
                            {b.name}
                            <span className="cell-sub">Aktywni serwisanci: {b.activeTechnicianCount}</span>
                          </span>
                        </Link>
                      </td>
                      <td className="muted col-wide">{b.activeTechnicianCount}</td>
                      <td><BranchStatusBadge active={b.active} /></td>
                      <td className="col-actions">
                        <div className="row-actions">
                          <button
                            type="button"
                            className="btn-icon"
                            onClick={() => setForm({ mode: 'edit', branch: b })}
                            aria-label={`Zmień nazwę: ${b.name}`}
                            title="Zmień nazwę"
                          >
                            <Pencil size={14} />
                          </button>
                          {b.active && (
                            <button
                              type="button"
                              className="btn-icon btn-icon-danger"
                              onClick={() => setToDeactivate(b)}
                              aria-label={`Dezaktywuj: ${b.name}`}
                              title="Dezaktywuj"
                            >
                              <PowerOff size={14} />
                            </button>
                          )}
                        </div>
                      </td>
                    </tr>
                  ))}
                </tbody>
              )}
            </table>
          </div>
        )}
        <LoadMore
          hasNextPage={list.hasNextPage}
          isFetchingNextPage={list.isFetchingNextPage}
          onLoadMore={() => void list.fetchNextPage()}
        />
      </section>

      {form && (
        <BranchFormModal branch={form.mode === 'edit' ? form.branch : undefined} onClose={() => setForm(null)} />
      )}
      {toDeactivate && <BranchDeactivateDialog branch={toDeactivate} onClose={() => setToDeactivate(null)} />}
    </>
  );
}
