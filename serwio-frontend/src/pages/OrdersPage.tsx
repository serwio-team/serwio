import { ClipboardList, ListFilter } from 'lucide-react';
import { useEffect, useRef, type KeyboardEvent } from 'react';
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
import { useServiceOrderList } from '../queries';

const TABS = [
  { id: 'active', label: 'Aktywne', statuses: ACTIVE_ORDER_STATUSES },
  { id: 'finished', label: 'Zakończone', statuses: FINISHED_ORDER_STATUSES },
] as const;

const formatDate = (value: string) => new Date(value).toLocaleDateString('pl-PL', { dateStyle: 'medium' });

export function OrdersPage() {
  const [searchParams, setSearchParams] = useSearchParams();
  const tab = TABS.find((t) => t.id === searchParams.get('tab')) ?? TABS[0];
  const requestedStatus = searchParams.get('status');
  const status = tab.statuses.find((s) => s === requestedStatus);
  const list = useServiceOrderList(status ? [status] : [...tab.statuses]);
  const tabRefs = useRef<(HTMLButtonElement | null)[]>([]);

  useEffect(() => {
    if (requestedStatus && !status) {
      setSearchParams(tab.id === 'active' ? {} : { tab: tab.id }, { replace: true });
    }
  }, [requestedStatus, status, tab.id, setSearchParams]);

  const update = (tabId: string, statusId?: string) => {
    const params: Record<string, string> = {};
    if (tabId !== 'active') params.tab = tabId;
    if (statusId) params.status = statusId;
    setSearchParams(params, { replace: true });
  };

  function onTabKeyDown(event: KeyboardEvent, index: number) {
    if (event.key !== 'ArrowRight' && event.key !== 'ArrowLeft') return;
    event.preventDefault();
    const next = (index + (event.key === 'ArrowRight' ? 1 : TABS.length - 1)) % TABS.length;
    update(TABS[next].id);
    tabRefs.current[next]?.focus();
  }

  const items = list.data?.pages.flatMap((p) => p.items) ?? [];

  let empty;
  if (status) {
    empty = (
      <EmptyState
        icon={ListFilter}
        title={`Brak zleceń o statusie „${serviceOrderStatusLabel(status)}”`}
        description="Wybierz inny status albo pokaż wszystkie zlecenia z tej zakładki."
        action={<button type="button" className="btn btn-secondary" onClick={() => update(tab.id)}>Pokaż wszystkie</button>}
      />
    );
  } else if (tab.id === 'active') {
    empty = (
      <EmptyState
        icon={ClipboardList}
        title="Brak aktywnych zleceń"
        description="Zlecenie utworzysz z widoku urządzenia."
        action={<Link to="/service-objects" className="btn btn-primary">Przejdź do urządzeń</Link>}
      />
    );
  } else {
    empty = (
      <EmptyState
        icon={ClipboardList}
        title="Brak zakończonych zleceń"
        description="Tu pojawią się zamknięte i anulowane zlecenia."
      />
    );
  }

  return (
    <>
      <PageHeader title="Zlecenia" description="Zlecenia serwisowe dla Twoich urządzeń." />

      <div className="tabs" role="tablist" aria-label="Rodzaj zleceń">
        {TABS.map((t, index) => (
          <button
            key={t.id}
            ref={(el) => { tabRefs.current[index] = el; }}
            type="button"
            role="tab"
            id={`orders-tab-${t.id}`}
            aria-selected={t.id === tab.id}
            aria-controls="orders-panel"
            tabIndex={t.id === tab.id ? 0 : -1}
            className={`tab${t.id === tab.id ? ' tab-active' : ''}`}
            onClick={() => update(t.id)}
            onKeyDown={(e) => onTabKeyDown(e, index)}
          >
            {t.label}
          </button>
        ))}
      </div>

      <section className="card" id="orders-panel" role="tabpanel" aria-labelledby={`orders-tab-${tab.id}`}>
        <div className="card-toolbar">
          <label className="filter">
            <ListFilter size={14} aria-hidden className="muted" />
            <span className="sr-only">Status</span>
            <select value={status ?? ''} onChange={(e) => update(tab.id, e.target.value)}>
              <option value="">Wszystkie statusy</option>
              {tab.statuses.map((s) => <option key={s} value={s}>{serviceOrderStatusLabel(s)}</option>)}
            </select>
          </label>
        </div>

        {list.isError && !list.isFetchingNextPage ? (
          <ErrorState message={errorMessage(list.error)} onRetry={() => void list.refetch()} />
        ) : list.isSuccess && items.length === 0 ? (
          empty
        ) : (
          <div className="table-wrap">
            <table>
              <thead>
                <tr>
                  <th className="col-primary">Zlecenie</th>
                  <th className="col-wide">Lokalizacja</th>
                  <th className="col-wide">Status</th>
                  <th className="col-wide">Utworzono</th>
                </tr>
              </thead>
              {list.isPending ? (
                <TableSkeleton columns={4} />
              ) : (
                <tbody>
                  {items.map((o) => (
                    <tr key={o.id}>
                      <td>
                        <Link to={`/orders/${encodeURIComponent(o.id)}`} className="cell-title cell-link">
                          <span className="cell-icon" aria-hidden><ClipboardList size={14} /></span>
                          <span className="cell-text">
                            {o.serviceObjectName}
                            <span className="cell-meta">{serviceOrderTypeLabel(o.orderType)}</span>
                            <span className="cell-sub">{o.locationName} · {formatDate(o.createdAt)}</span>
                            <span className="cell-sub cell-badge"><ServiceOrderStatusBadge status={o.currentStatus} /></span>
                          </span>
                        </Link>
                      </td>
                      <td className="muted col-wide">{o.locationName}</td>
                      <td className="col-wide nowrap"><ServiceOrderStatusBadge status={o.currentStatus} /></td>
                      <td className="muted col-wide nowrap">{formatDate(o.createdAt)}</td>
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
          label="Załaduj więcej"
        />
      </section>
    </>
  );
}
