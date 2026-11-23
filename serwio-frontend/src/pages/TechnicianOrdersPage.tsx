import { ClipboardList, ShieldAlert } from 'lucide-react';
import { Link, useOutletContext } from 'react-router-dom';
import { ApiError, errorMessage } from '../api';
import { EmptyState, ErrorState, LoadMore, PageHeader, TableSkeleton } from '../components/DataState';
import { ServiceOrderStatusBadge } from '../components/StatusBadge';
import { useTechnicianServiceOrderList } from '../queries';
import type { UserAccountResponse } from '../types';

export function TechnicianOrdersPage() {
  const user = useOutletContext<UserAccountResponse>();
  const orders = useTechnicianServiceOrderList(user.id);
  const items = orders.data?.pages.flat() ?? [];
  const status = orders.error instanceof ApiError ? orders.error.status : undefined;

  if (status === 401) return null;

  return (
    <>
      <PageHeader title="Moje zlecenia" description="Zlecenia serwisowe przypisane do Ciebie." />

      <section className="card" aria-label="Lista zleceń">
        {status === 403 ? (
          <EmptyState icon={ShieldAlert} title="Brak dostępu do zleceń"
            description="Twoje konto nie ma uprawnień do wyświetlenia listy zleceń." />
        ) : orders.isError ? (
          <ErrorState
            message={status === 404 ? 'Lista zleceń jest niedostępna.' : errorMessage(orders.error)}
            onRetry={() => void (orders.isFetchNextPageError ? orders.fetchNextPage() : orders.refetch())}
          />
        ) : orders.isSuccess && items.length === 0 ? (
          <EmptyState icon={ClipboardList} title="Nie masz jeszcze przypisanych zleceń"
            description="Tutaj pojawią się Twoje zlecenia." />
        ) : (
          <>
            <div className="table-wrap">
              <table>
                <thead>
                  <tr>
                    <th className="col-primary">Obiekt</th>
                    <th className="col-wide">Lokalizacja</th>
                    <th className="col-wide">Status</th>
                  </tr>
                </thead>
                {orders.isPending ? (
                  <TableSkeleton columns={3} />
                ) : (
                  <tbody>
                    {items.map((order) => (
                      <tr key={order.id}>
                        <td>
                          <Link to={`/technician/orders/${encodeURIComponent(order.id)}`} className="cell-title cell-link">
                            <span className="cell-icon" aria-hidden><ClipboardList size={14} /></span>
                            <span className="cell-text">
                              {order.serviceObjectName}
                              <span className="cell-sub">{order.locationName}</span>
                              <span className="cell-sub cell-badge"><ServiceOrderStatusBadge status={order.currentStatus} /></span>
                            </span>
                          </Link>
                        </td>
                        <td className="muted col-wide">{order.locationName}</td>
                        <td className="col-wide"><ServiceOrderStatusBadge status={order.currentStatus} /></td>
                      </tr>
                    ))}
                  </tbody>
                )}
              </table>
            </div>
            <LoadMore
              hasNextPage={orders.hasNextPage}
              isFetchingNextPage={orders.isFetchingNextPage}
              onLoadMore={() => void orders.fetchNextPage()}
              label="Załaduj więcej"
            />
          </>
        )}
      </section>
    </>
  );
}
