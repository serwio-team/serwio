import { ClipboardList, ListFilter } from 'lucide-react';
import { useEffect } from 'react';
import { Link, useSearchParams } from 'react-router-dom';
import { errorMessage } from '../api';
import { EmptyState, ErrorState, LoadMore, PageHeader, TableSkeleton } from '../components/DataState';
import { serviceOrderTypeLabel } from '../components/ServiceOrderForm';
import {
  ACTIVE_ORDER_STATUSES,
  FINISHED_ORDER_STATUSES,
  ServiceOrderStatusBadge,
  serviceOrderStatusLabel,
} from '../components/StatusBadge';
import { useCompanyServiceOrderList } from '../queries';

const STATUSES = [...ACTIVE_ORDER_STATUSES, ...FINISHED_ORDER_STATUSES];

const formatDate = (value: string) => new Date(value).toLocaleDateString('pl-PL', { dateStyle: 'medium' });

export function CompanyOrdersPage() {
  const [searchParams, setSearchParams] = useSearchParams();
  const requestedStatus = searchParams.get('status');
  const status = STATUSES.find((s) => s === requestedStatus);
  const list = useCompanyServiceOrderList(status);

  useEffect(() => {
    if (requestedStatus && !status) setSearchParams({}, { replace: true });
  }, [requestedStatus, status, setSearchParams]);

  const setFilter = (value: string) => setSearchParams(value ? { status: value } : {}, { replace: true });
  const items = list.data?.pages.flat() ?? [];

  return (
    <>
      <PageHeader title="Zlecenia" description="Zlecenia przesłane przez właścicieli obiektów." />

      <section className="card">
        <div className="card-toolbar">
          <label className="filter">
            <ListFilter size={14} aria-hidden className="muted" />
            <span className="sr-only">Status</span>
            <select value={status ?? ''} onChange={(e) => setFilter(e.target.value)}>
              <option value="">Wszystkie statusy</option>
              {STATUSES.map((s) => <option key={s} value={s}>{serviceOrderStatusLabel(s)}</option>)}
            </select>
          </label>
        </div>

        {list.isError && !list.isFetchingNextPage ? (
          <ErrorState message={errorMessage(list.error)} onRetry={() => void list.refetch()} />
        ) : list.isSuccess && items.length === 0 ? (
          status ? (
            <EmptyState
              icon={ListFilter}
              title={`Brak zleceń o statusie „${serviceOrderStatusLabel(status)}”`}
              description="Wybierz inny status albo pokaż wszystkie zlecenia."
              action={<button type="button" className="btn btn-secondary" onClick={() => setFilter('')}>Pokaż wszystkie</button>}
            />
          ) : (
            <EmptyState
              icon={ClipboardList}
              title="Brak zleceń"
              description="Tu pojawią się zlecenia przesłane przez właścicieli obiektów."
            />
          )
        ) : (
          <div className="table-wrap">
            <table>
              <thead>
                <tr>
                  <th className="col-primary">Obiekt</th>
                  <th className="col-wide">Lokalizacja</th>
                  <th className="col-wide">Typ</th>
                  <th className="col-wide">Utworzono</th>
                  <th className="col-wide">Status</th>
                </tr>
              </thead>
              {list.isPending ? (
                <TableSkeleton columns={5} />
              ) : (
                <tbody>
                  {items.map((o) => (
                    <tr key={o.id}>
                      <td>
                        <Link to={`/company/orders/${encodeURIComponent(o.id)}`} className="cell-title cell-link">
                          <span className="cell-icon" aria-hidden><ClipboardList size={14} /></span>
                          <span className="cell-text">
                            {o.serviceObjectName}
                            <span className="cell-sub">
                              {serviceOrderTypeLabel(o.orderType)} · {o.locationName} · {formatDate(o.createdAt)}
                            </span>
                            <span className="cell-sub cell-badge"><ServiceOrderStatusBadge status={o.currentStatus} /></span>
                          </span>
                        </Link>
                      </td>
                      <td className="muted col-wide">{o.locationName}</td>
                      <td className="muted col-wide">{serviceOrderTypeLabel(o.orderType)}</td>
                      <td className="muted col-wide nowrap">{formatDate(o.createdAt)}</td>
                      <td className="col-wide nowrap"><ServiceOrderStatusBadge status={o.currentStatus} /></td>
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
    </>
  );
}
