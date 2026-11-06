import { Box, ClipboardPlus, MapPin, Plus } from 'lucide-react';
import { useEffect, useState } from 'react';
import { Link, useNavigate, useSearchParams } from 'react-router-dom';
import { ApiError, errorMessage } from '../api';
import { EmptyState, ErrorState, LoadMore, PageHeader, TableSkeleton } from '../components/DataState';
import { ConfirmDialog } from '../components/Modal';
import { RowActions } from '../components/RowActions';
import { ServiceObjectFormModal, serviceObjectTypeLabel } from '../components/ServiceObjectForm';
import { ServiceOrderCreateModal } from '../components/ServiceOrderForm';
import { useToast } from '../components/Toast';
import { useAllLocations, useDeleteServiceObject, useServiceObjectList } from '../queries';
import type { ServiceObjectListItemResponse } from '../types';

type FormState = { mode: 'create' } | { mode: 'edit'; id: string } | null;

export function ServiceObjectsPage() {
  const navigate = useNavigate();
  const [searchParams, setSearchParams] = useSearchParams();
  const locationId = searchParams.get('locationId') ?? undefined;
  const locations = useAllLocations();
  const list = useServiceObjectList(locationId);
  const remove = useDeleteServiceObject();
  const toast = useToast();
  const [form, setForm] = useState<FormState>(null);
  const [toDelete, setToDelete] = useState<ServiceObjectListItemResponse | null>(null);
  const [orderFor, setOrderFor] = useState<string | null>(null);

  useEffect(() => {
    if (locationId && locations.data && !locations.data.some((l) => l.id === locationId)) {
      setSearchParams({}, { replace: true });
    }
  }, [locationId, locations.data, setSearchParams]);

  const setFilter = (id: string) => setSearchParams(id ? { locationId: id } : {}, { replace: true });
  const items = list.data?.pages.flat() ?? [];
  const addButton = (
    <button type="button" className="btn btn-primary" onClick={() => setForm({ mode: 'create' })}>
      <Plus size={14} aria-hidden /> Dodaj urządzenie
    </button>
  );

  function confirmDelete() {
    if (!toDelete) return;
    const { id, name } = toDelete;
    remove.mutate(id, {
      onSuccess: () => toast.success(`Usunięto urządzenie „${name}”.`),
      onError: (e) => {
        if (e instanceof ApiError && e.status === 404) {
          toast.error('To urządzenie już nie istnieje. Lista została odświeżona.');
        } else {
          toast.error(errorMessage(e));
        }
      },
      onSettled: () => setToDelete(null),
    });
  }

  return (
    <>
      <PageHeader title="Urządzenia" description="Obiekty serwisowe w Twoich lokalizacjach." action={addButton} />

      <section className="card">
        <div className="card-toolbar">
          <label className="filter">
            <MapPin size={14} aria-hidden className="muted" />
            <span className="sr-only">Lokalizacja</span>
            <select value={locationId ?? ''} onChange={(e) => setFilter(e.target.value)} disabled={locations.isPending}>
              <option value="">Wszystkie lokalizacje</option>
              {locations.data?.map((l) => <option key={l.id} value={l.id}>{l.name}</option>)}
            </select>
          </label>
          {locations.isError && <span className="field-error">Nie udało się pobrać lokalizacji do filtra.</span>}
        </div>

        {list.isError && !list.isFetchingNextPage ? (
          <ErrorState message={errorMessage(list.error)} onRetry={() => void list.refetch()} />
        ) : list.isSuccess && items.length === 0 ? (
          <EmptyState
            icon={Box}
            title={locationId ? 'Brak urządzeń w tej lokalizacji' : 'Brak urządzeń'}
            description={locationId
              ? 'Wybierz inną lokalizację albo dodaj tu urządzenie.'
              : 'Dodaj pierwsze urządzenie, aby śledzić jego serwis.'}
            action={addButton}
          />
        ) : (
          <div className="table-wrap">
            <table>
              <thead>
                <tr>
                  <th>Nazwa</th>
                  <th className="col-wide">Typ</th>
                  <th className="col-wide">Lokalizacja</th>
                  <th className="col-actions"><span className="sr-only">Akcje</span></th>
                </tr>
              </thead>
              {list.isPending ? (
                <TableSkeleton columns={4} />
              ) : (
                <tbody>
                  {items.map((o) => (
                    <tr key={o.id}>
                      <td>
                        <Link to={`/service-objects/${encodeURIComponent(o.id)}`} className="cell-title cell-link">
                          <span className="cell-icon" aria-hidden><Box size={14} /></span>
                          <span className="cell-text">
                            {o.name}
                            <span className="cell-sub">{o.locationName}</span>
                            <span className="cell-sub cell-badge"><span className="badge">{serviceObjectTypeLabel(o.type)}</span></span>
                          </span>
                        </Link>
                      </td>
                      <td className="col-wide"><span className="badge" title={o.type}>{serviceObjectTypeLabel(o.type)}</span></td>
                      <td className="muted col-wide">{o.locationName}</td>
                      <td className="col-actions">
                        <div className="row-actions-group">
                          <button
                            type="button"
                            className="btn btn-secondary btn-sm"
                            onClick={() => setOrderFor(o.id)}
                            aria-label={`Utwórz zlecenie: ${o.name}`}
                            title="Utwórz zlecenie"
                          >
                            <ClipboardPlus size={14} aria-hidden /> <span className="btn-label">Utwórz zlecenie</span>
                          </button>
                          <RowActions
                            name={o.name}
                            onEdit={() => setForm({ mode: 'edit', id: o.id })}
                            onDelete={() => setToDelete(o)}
                          />
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
        <ServiceObjectFormModal
          editId={form.mode === 'edit' ? form.id : undefined}
          defaultLocationId={locationId}
          onClose={() => setForm(null)}
          onAddLocation={() => navigate('/locations', { state: { create: true } })}
        />
      )}
      {orderFor && <ServiceOrderCreateModal serviceObjectId={orderFor} onClose={() => setOrderFor(null)} />}
      {toDelete && (
        <ConfirmDialog
          title="Usunąć urządzenie?"
          message={<>Urządzenie <strong>{toDelete.name}</strong> zniknie z listy.</>}
          confirmLabel="Usuń urządzenie"
          pending={remove.isPending}
          onConfirm={confirmDelete}
          onCancel={() => setToDelete(null)}
        />
      )}
    </>
  );
}
