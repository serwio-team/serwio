import { KeyRound, Pencil, UserCheck, UserX, Users } from 'lucide-react';
import { useState, type ReactNode } from 'react';
import { errorMessage } from '../api';
import { useTechnicianList, useUpdateTechnician } from '../queries';
import type { TechnicianResponse } from '../types';
import { EmptyState, ErrorState, LoadMore, TableSkeleton } from './DataState';
import { ConfirmDialog } from './Modal';
import { AccountStatusBadge } from './StatusBadge';
import { TechnicianEditModal, TechnicianPasswordModal } from './TechnicianForm';
import { useToast } from './Toast';

interface TechnicianTableProps {
  branchId?: string;
  showBranch?: boolean;
  emptyTitle: string;
  emptyDescription: string;
  emptyAction?: ReactNode;
}

export function TechnicianTable({ branchId, showBranch = true, emptyTitle, emptyDescription, emptyAction }: TechnicianTableProps) {
  const list = useTechnicianList(branchId);
  const update = useUpdateTechnician();
  const toast = useToast();
  const [editId, setEditId] = useState<string | null>(null);
  const [passwordFor, setPasswordFor] = useState<TechnicianResponse | null>(null);
  const [toDeactivate, setToDeactivate] = useState<TechnicianResponse | null>(null);
  const [activatingId, setActivatingId] = useState<string | null>(null);
  const items = list.data?.pages.flat() ?? [];
  const columns = showBranch ? 5 : 4;

  function setActive(technician: TechnicianResponse, active: boolean) {
    const { id, displayName, branchId: currentBranchId } = technician;
    if (active) setActivatingId(id);
    update.mutate({ id, data: { displayName, branchId: currentBranchId, active } }, {
      onSuccess: () => toast.success(active ? `Aktywowano serwisanta ${displayName}.` : `Dezaktywowano serwisanta ${displayName}.`),
      onError: (e) => toast.error(errorMessage(e)),
      onSettled: () => {
        setActivatingId(null);
        setToDeactivate(null);
      },
    });
  }

  return (
    <>
      {list.isError && !list.isFetchingNextPage ? (
        <ErrorState message={errorMessage(list.error)} onRetry={() => void list.refetch()} />
      ) : list.isSuccess && items.length === 0 ? (
        <EmptyState icon={Users} title={emptyTitle} description={emptyDescription} action={emptyAction} />
      ) : (
        <div className="table-wrap">
          <table>
            <thead>
              <tr>
                <th>Serwisant</th>
                <th className="col-wide">E-mail</th>
                {showBranch && <th className="col-wide">Oddział</th>}
                <th className="col-wide">Status konta</th>
                <th className="col-actions"><span className="sr-only">Akcje</span></th>
              </tr>
            </thead>
            {list.isPending ? (
              <TableSkeleton columns={columns} />
            ) : (
              <tbody>
                {items.map((t) => (
                  <tr key={t.id}>
                    <td>
                      <span className="cell-title">
                        <span className="avatar avatar-sm" aria-hidden>{t.displayName.charAt(0).toUpperCase()}</span>
                        <span className="cell-text">
                          {t.displayName}
                          <span className="cell-sub">{showBranch ? `${t.email} · ${t.branchName}` : t.email}</span>
                          <span className="cell-sub cell-badge"><AccountStatusBadge status={t.accountStatus} /></span>
                        </span>
                      </span>
                    </td>
                    <td className="muted col-wide">{t.email}</td>
                    {showBranch && <td className="muted col-wide">{t.branchName}</td>}
                    <td className="col-wide"><AccountStatusBadge status={t.accountStatus} /></td>
                    <td className="col-actions">
                      <div className="row-actions">
                        <button type="button" className="btn-icon" onClick={() => setEditId(t.id)}
                          aria-label={`Edytuj: ${t.displayName}`} title="Edytuj">
                          <Pencil size={14} />
                        </button>
                        <button type="button" className="btn-icon" onClick={() => setPasswordFor(t)}
                          aria-label={`Nowe hasło startowe: ${t.displayName}`} title="Nowe hasło startowe">
                          <KeyRound size={14} />
                        </button>
                        {t.active ? (
                          <button type="button" className="btn-icon btn-icon-danger" onClick={() => setToDeactivate(t)}
                            aria-label={`Dezaktywuj: ${t.displayName}`} title="Dezaktywuj">
                            <UserX size={14} />
                          </button>
                        ) : (
                          <button type="button" className="btn-icon" onClick={() => setActive(t, true)}
                            disabled={activatingId === t.id}
                            aria-label={`Aktywuj: ${t.displayName}`} title="Aktywuj">
                            <UserCheck size={14} />
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

      {editId && <TechnicianEditModal id={editId} onClose={() => setEditId(null)} />}
      {passwordFor && <TechnicianPasswordModal technician={passwordFor} onClose={() => setPasswordFor(null)} />}
      {toDeactivate && (
        <ConfirmDialog
          title="Dezaktywować serwisanta?"
          message={<><strong>{toDeactivate.displayName}</strong> nie będzie mógł się zalogować, a otwarta sesja zakończy się przy następnym żądaniu. Konto można później aktywować ponownie.</>}
          confirmLabel="Dezaktywuj"
          pendingLabel="Dezaktywowanie…"
          pending={update.isPending}
          onConfirm={() => setActive(toDeactivate, false)}
          onCancel={() => setToDeactivate(null)}
        />
      )}
    </>
  );
}
