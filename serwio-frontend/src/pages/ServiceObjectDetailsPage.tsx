import { ArrowLeft, ClipboardPlus, Pencil, SearchX } from 'lucide-react';
import { useState } from 'react';
import { Link, useNavigate, useParams } from 'react-router-dom';
import { ApiError, errorMessage } from '../api';
import { EmptyState, ErrorState } from '../components/DataState';
import { ServiceObjectFormModal, serviceObjectTypeLabel } from '../components/ServiceObjectForm';
import { ServiceOrderCreateModal } from '../components/ServiceOrderForm';
import { useLocation, useServiceObject } from '../queries';
import type { ServiceObjectResponse } from '../types';

export function ServiceObjectDetailsPage() {
  const { id } = useParams<{ id: string }>();
  const navigate = useNavigate();
  const object = useServiceObject(id);
  const [editing, setEditing] = useState(false);
  const [ordering, setOrdering] = useState(false);
  const data = object.data;

  const backLink = (
    <Link to="/service-objects" className="back-link">
      <ArrowLeft size={14} aria-hidden /> Urządzenia
    </Link>
  );

  if (object.isError) {
    const notFound = object.error instanceof ApiError && object.error.status === 404;
    return (
      <>
        {backLink}
        <section className="card">
          {notFound ? (
            <EmptyState icon={SearchX} title="Nie znaleziono urządzenia" description="To urządzenie nie istnieje albo zostało usunięte." />
          ) : (
            <ErrorState message={errorMessage(object.error)} onRetry={() => void object.refetch()} />
          )}
        </section>
      </>
    );
  }

  return (
    <>
      {backLink}
      <header className="page-header">
        <div>
          {data ? (
            <h1 className="title-with-badge">{data.name} <span className="badge">{serviceObjectTypeLabel(data.type)}</span></h1>
          ) : (
            <h1><span className="skeleton skeleton-title" /></h1>
          )}
          <p>{data ? data.locationName : ' '}</p>
        </div>
        {data && (
          <div className="page-actions">
            <button type="button" className="btn btn-secondary" onClick={() => setEditing(true)}>
              <Pencil size={14} aria-hidden /> Edytuj
            </button>
            {data.active && (
              <button type="button" className="btn btn-primary" onClick={() => setOrdering(true)}>
                <ClipboardPlus size={14} aria-hidden /> Utwórz zlecenie
              </button>
            )}
          </div>
        )}
      </header>

      {data && !data.active && (
        <div className="inline-alert inline-alert-info">Urządzenie jest nieaktywne. Nie można tworzyć dla niego zleceń.</div>
      )}

      {data ? (
        <ObjectDetails object={data} />
      ) : (
        <section className="card details-card" aria-busy="true" aria-label="Ładowanie">
          <span className="skeleton skeleton-line" />
          <span className="skeleton skeleton-line" />
        </section>
      )}

      {editing && data && (
        <ServiceObjectFormModal
          editId={data.id}
          onClose={() => setEditing(false)}
          onAddLocation={() => navigate('/locations', { state: { create: true } })}
        />
      )}
      {ordering && data && <ServiceOrderCreateModal serviceObjectId={data.id} onClose={() => setOrdering(false)} />}
    </>
  );
}

function ObjectDetails({ object }: { object: ServiceObjectResponse }) {
  const location = useLocation(object.locationId);
  const rows: [string, string | null][] = [
    ['Typ', serviceObjectTypeLabel(object.type)],
    ['Producent', object.manufacturer],
    ['Model', object.model],
    ['Numer seryjny', object.serialNumber],
  ];

  return (
    <div className="details-layout">
      <section className="card details-card">
        <h2 className="details-heading">Urządzenie</h2>
        <dl className="details-list">
          {rows.map(([label, value]) => (
            <div key={label}>
              <dt>{label}</dt>
              <dd className={value ? undefined : 'muted'}>{value ?? 'Brak danych'}</dd>
            </div>
          ))}
          <div>
            <dt>Opis</dt>
            <dd className={object.description ? 'details-text' : 'muted'}>{object.description ?? 'Brak opisu'}</dd>
          </div>
        </dl>
      </section>
      <section className="card details-card">
        <h2 className="details-heading">Lokalizacja</h2>
        <dl className="details-list">
          <div>
            <dt>Nazwa</dt>
            <dd>
              <Link to="/locations" className="cell-link">{location.data?.name ?? object.locationName}</Link>
            </dd>
          </div>
          <div>
            <dt>Adres</dt>
            <dd>
              {location.data ? (
                <>
                  {location.data.addressLine}
                  <span className="details-sub">{location.data.postalCode} {location.data.city}</span>
                </>
              ) : location.isError ? (
                <span className="muted">Nie udało się pobrać adresu.</span>
              ) : (
                <span className="skeleton skeleton-line" />
              )}
            </dd>
          </div>
        </dl>
      </section>
    </div>
  );
}
