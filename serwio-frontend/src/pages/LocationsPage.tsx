import { MapPin, Plus } from 'lucide-react';
import { useEffect, useState } from 'react';
import { useLocation as useRouterLocation, useNavigate } from 'react-router-dom';
import { ApiError, errorMessage } from '../api';
import { EmptyState, ErrorState, LoadMore, PageHeader, TableSkeleton } from '../components/DataState';
import { LocationFormModal } from '../components/LocationForm';
import { ConfirmDialog } from '../components/Modal';
import { RowActions } from '../components/RowActions';
import { useToast } from '../components/Toast';
import { useDeleteLocation, useLocationList } from '../queries';
import type { LocationListItemResponse } from '../types';

type FormState = { mode: 'create' } | { mode: 'edit'; id: string } | null;

export function LocationsPage() {
  const routerLocation = useRouterLocation();
  const navigate = useNavigate();
  const list = useLocationList();
  const remove = useDeleteLocation();
  const toast = useToast();
  const [form, setForm] = useState<FormState>(
    (routerLocation.state as { create?: boolean } | null)?.create ? { mode: 'create' } : null,
  );
  const [toDelete, setToDelete] = useState<LocationListItemResponse | null>(null);

  useEffect(() => {
    if (routerLocation.state) navigate(routerLocation.pathname, { replace: true, state: null });
  }, [routerLocation, navigate]);

  const items = list.data?.pages.flat() ?? [];
  const addButton = (
    <button type="button" className="btn btn-primary" onClick={() => setForm({ mode: 'create' })}>
      <Plus size={14} aria-hidden /> Dodaj lokalizację
    </button>
  );

  function confirmDelete() {
    if (!toDelete) return;
    const { id, name } = toDelete;
    remove.mutate(id, {
      onSuccess: () => toast.success(`Usunięto lokalizację „${name}”.`),
      onError: (e) => {
        if (e instanceof ApiError && e.status === 409) {
          toast.error(`Nie można usunąć lokalizacji „${name}”, bo są (lub były) do niej przypisane urządzenia.`);
        } else if (e instanceof ApiError && e.status === 404) {
          toast.error('Ta lokalizacja już nie istnieje. Lista została odświeżona.');
        } else {
          toast.error(errorMessage(e));
        }
      },
      onSettled: () => setToDelete(null),
    });
  }

  return (
    <>
      <PageHeader title="Lokalizacje" description="Miejsca, w których znajdują się Twoje urządzenia." action={addButton} />

      <section className="card">
        {list.isError && !list.isFetchingNextPage ? (
          <ErrorState message={errorMessage(list.error)} onRetry={() => void list.refetch()} />
        ) : list.isSuccess && items.length === 0 ? (
          <EmptyState
            icon={MapPin}
            title="Brak lokalizacji"
            description="Dodaj pierwszą lokalizację, aby móc przypisywać do niej urządzenia."
            action={addButton}
          />
        ) : (
          <div className="table-wrap">
            <table>
              <thead>
                <tr>
                  <th>Nazwa</th>
                  <th className="col-wide">Adres</th>
                  <th className="col-wide">Miasto</th>
                  <th className="col-actions"><span className="sr-only">Akcje</span></th>
                </tr>
              </thead>
              {list.isPending ? (
                <TableSkeleton columns={4} />
              ) : (
                <tbody>
                  {items.map((l) => (
                    <tr key={l.id}>
                      <td>
                        <span className="cell-title">
                          <span className="cell-icon" aria-hidden><MapPin size={14} /></span>
                          <span className="cell-text">
                            {l.name}
                            <span className="cell-sub">{l.addressLine}, {l.city}</span>
                          </span>
                        </span>
                      </td>
                      <td className="muted col-wide">{l.addressLine}</td>
                      <td className="muted col-wide">{l.city}</td>
                      <td className="col-actions">
                        <RowActions
                          name={l.name}
                          onEdit={() => setForm({ mode: 'edit', id: l.id })}
                          onDelete={() => setToDelete(l)}
                        />
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
        <LocationFormModal editId={form.mode === 'edit' ? form.id : undefined} onClose={() => setForm(null)} />
      )}
      {toDelete && (
        <ConfirmDialog
          title="Usunąć lokalizację?"
          message={<>Lokalizacja <strong>{toDelete.name}</strong> zostanie usunięta. Tej operacji nie można cofnąć.</>}
          confirmLabel="Usuń lokalizację"
          pending={remove.isPending}
          onConfirm={confirmDelete}
          onCancel={() => setToDelete(null)}
        />
      )}
    </>
  );
}
