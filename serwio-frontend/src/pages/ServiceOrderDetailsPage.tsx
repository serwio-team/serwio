import { ArrowLeft, SearchX, ShieldAlert } from 'lucide-react';
import { Link, useOutletContext, useParams } from 'react-router-dom';
import { ApiError, errorMessage } from '../api';
import { EmptyState, ErrorState } from '../components/DataState';
import { OrderAssignmentCard } from '../components/OrderAssignment';
import { OrderOutcomeCard } from '../components/OrderOutcome';
import { OrderVisitsCard } from '../components/OrderVisits';
import { serviceOrderDescriptionLabel, serviceOrderTypeLabel } from '../components/ServiceOrderForm';
import { ServiceOrderStatusBadge } from '../components/StatusBadge';
import { useServiceOrder } from '../queries';
import type { ServiceOrderResponse, TechnicianServiceOrderResponse, UserAccountResponse } from '../types';

const BACK_LINKS: Record<string, { to: string; label: string }> = {
  ASSET_OWNER: { to: '/orders', label: 'Zlecenia' },
  SERVICE_COMPANY: { to: '/company/orders', label: 'Zlecenia' },
  TECHNICIAN: { to: '/technician/orders', label: 'Moje zlecenia' },
};

const formatDate = (value: string) =>
  new Date(value).toLocaleString('pl-PL', { dateStyle: 'long', timeStyle: 'short' });

export function ServiceOrderDetailsPage() {
  const { id } = useParams<{ id: string }>();
  const user = useOutletContext<UserAccountResponse>();
  const technician = user.role === 'TECHNICIAN';
  const order = useServiceOrder(id, technician ? user.id : undefined);
  const data = order.data;

  const back = BACK_LINKS[user.role] ?? BACK_LINKS.ASSET_OWNER;
  const backLink = (
    <Link to={back.to} className="back-link">
      <ArrowLeft size={14} aria-hidden /> {back.label}
    </Link>
  );

  if (order.isError) {
    const status = order.error instanceof ApiError ? order.error.status : 0;
    if (technician && status === 401) return null;
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

      {data ? <OrderDetails order={data} role={user.role} /> : <OrderDetailsSkeleton />}
      {data && !technician && 'reportFinalized' in data && <OrderOutcomeCard order={data} role={user.role} />}
      {data && user.role === 'ASSET_OWNER' && (
        <OrderVisitsCard orderId={data.id} orderStatus={data.currentStatus} role={user.role} />
      )}
      {data && user.role === 'SERVICE_COMPANY' && (
        <>
          <OrderAssignmentCard orderId={data.id} orderStatus={data.currentStatus} />
          <OrderVisitsCard orderId={data.id} orderStatus={data.currentStatus} role={user.role} />
        </>
      )}
    </>
  );
}

function OrderDetails({ order, role }: {
  order: ServiceOrderResponse | TechnicianServiceOrderResponse;
  role: string;
}) {
  const technician = role === 'TECHNICIAN';
  const owner = role === 'ASSET_OWNER';
  const coordinatorName = 'coordinator' in order ? order.coordinator?.displayName.trim() : undefined;
  const closedAt = 'closedAt' in order ? order.closedAt : null;

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
            <dt>{owner ? 'Urządzenie' : 'Obiekt'}</dt>
            <dd>
              {!owner ? order.serviceObjectName : (
                <Link to={`/service-objects/${encodeURIComponent(order.serviceObjectId)}`} className="cell-link">
                  {order.serviceObjectName}
                </Link>
              )}
            </dd>
          </div>
          <div>
            <dt>Lokalizacja</dt>
            <dd>
              {order.locationName}
              <span className="details-sub">{order.addressLine}, {order.city}</span>
            </dd>
          </div>
          {technician ? (
            <div>
              <dt>Koordynator</dt>
              <dd>{coordinatorName || 'Nie przypisano'}</dd>
            </div>
          ) : 'assetOwnerName' in order && (
            <div>
              <dt>Właściciel</dt>
              <dd>{order.assetOwnerName}</dd>
            </div>
          )}
          <div>
            <dt>Utworzono</dt>
            <dd>{formatDate(order.createdAt)}</dd>
          </div>
          {closedAt && (
            <div>
              <dt>Zakończono</dt>
              <dd>{formatDate(closedAt)}</dd>
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
