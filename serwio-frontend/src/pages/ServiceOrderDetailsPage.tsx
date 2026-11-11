import { ArrowLeft, SearchX, ShieldAlert } from 'lucide-react';
import { Link, useParams } from 'react-router-dom';
import { ApiError, errorMessage } from '../api';
import { EmptyState, ErrorState } from '../components/DataState';
import { serviceOrderDescriptionLabel, serviceOrderTypeLabel } from '../components/ServiceOrderForm';
import { ServiceOrderStatusBadge } from '../components/StatusBadge';
import { useServiceOrder } from '../queries';
import type { ServiceOrderResponse } from '../types';

const formatDate = (value: string) =>
  new Date(value).toLocaleString('pl-PL', { dateStyle: 'long', timeStyle: 'short' });

export function ServiceOrderDetailsPage() {
  const { id } = useParams<{ id: string }>();
  const order = useServiceOrder(id);
  const data = order.data;

  const backLink = data ? (
    <Link to={`/service-objects/${encodeURIComponent(data.serviceObjectId)}`} className="back-link">
      <ArrowLeft size={14} aria-hidden /> {data.serviceObjectName}
    </Link>
  ) : (
    <Link to="/service-objects" className="back-link">
      <ArrowLeft size={14} aria-hidden /> Urządzenia
    </Link>
  );

  if (order.isError) {
    const status = order.error instanceof ApiError ? order.error.status : 0;
    return (
      <>
        {backLink}
        <section className="card">
          {status === 404 ? (
            <EmptyState icon={SearchX} title="Nie znaleziono zlecenia" description="To zlecenie nie istnieje albo nie masz do niego dostępu." />
          ) : status === 403 ? (
            <EmptyState icon={ShieldAlert} title="Brak dostępu do zlecenia" description="Twoje konto nie może wyświetlić tego zlecenia." />
          ) : (
            <ErrorState message={errorMessage(order.error)} onRetry={() => void order.refetch()} />
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
            <h1 className="title-with-badge">
              {serviceOrderTypeLabel(data.orderType)}: {data.serviceObjectName} <ServiceOrderStatusBadge status={data.currentStatus} />
            </h1>
          ) : (
            <h1><span className="skeleton skeleton-title" /></h1>
          )}
          <p>{data ? `Zlecenie utworzone ${formatDate(data.createdAt)}` : ' '}</p>
        </div>
      </header>

      {data ? <OrderDetails order={data} /> : <OrderDetailsSkeleton />}
    </>
  );
}

function OrderDetails({ order }: { order: ServiceOrderResponse }) {
  return (
    <div className="details-layout">
      <section className="card details-card">
        <h2 className="details-heading">{serviceOrderDescriptionLabel(order.orderType)}</h2>
        <p className="details-text">{order.description}</p>
      </section>
      <section className="card details-card">
        <h2 className="details-heading">Szczegóły</h2>
        <dl className="details-list">
          <div>
            <dt>Typ zlecenia</dt>
            <dd>{serviceOrderTypeLabel(order.orderType)}</dd>
          </div>
          <div>
            <dt>Status</dt>
            <dd><ServiceOrderStatusBadge status={order.currentStatus} /></dd>
          </div>
          <div>
            <dt>Urządzenie</dt>
            <dd>
              <Link to={`/service-objects/${encodeURIComponent(order.serviceObjectId)}`} className="cell-link">
                {order.serviceObjectName}
              </Link>
            </dd>
          </div>
          <div>
            <dt>Lokalizacja</dt>
            <dd>
              {order.locationName}
              <span className="details-sub">{order.addressLine}, {order.city}</span>
            </dd>
          </div>
          <div>
            <dt>Właściciel</dt>
            <dd>{order.assetOwnerName}</dd>
          </div>
          <div>
            <dt>Utworzono</dt>
            <dd>{formatDate(order.createdAt)}</dd>
          </div>
          {order.closedAt && (
            <div>
              <dt>Zamknięto</dt>
              <dd>{formatDate(order.closedAt)}</dd>
            </div>
          )}
        </dl>
      </section>
    </div>
  );
}

function OrderDetailsSkeleton() {
  return (
    <div className="details-layout" aria-busy="true" aria-label="Ładowanie">
      <section className="card details-card">
        <span className="skeleton skeleton-line" />
        <span className="skeleton skeleton-line" />
      </section>
      <section className="card details-card">
        <span className="skeleton skeleton-line" />
        <span className="skeleton skeleton-line" />
      </section>
    </div>
  );
}
