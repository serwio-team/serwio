import { AlertCircle, Loader2, RotateCw, type LucideIcon } from 'lucide-react';
import type { ReactNode } from 'react';

// Stany listy: ładowanie, pusto, błąd i „Pokaż więcej”.

export function TableSkeleton({ columns, rows = 5 }: { columns: number; rows?: number }) {
  return (
    <tbody aria-busy="true" aria-label="Ładowanie">
      {Array.from({ length: rows }, (_, r) => (
        <tr key={r} className="skeleton-row">
          {Array.from({ length: columns }, (_, c) => (
            <td key={c}><span className="skeleton" style={{ width: `${45 + ((r * 7 + c * 13) % 40)}%` }} /></td>
          ))}
        </tr>
      ))}
    </tbody>
  );
}

interface EmptyStateProps {
  icon: LucideIcon;
  title: string;
  description: string;
  action?: ReactNode;
}

export function EmptyState({ icon: Icon, title, description, action }: EmptyStateProps) {
  return (
    <div className="empty-state">
      <span className="empty-icon" aria-hidden><Icon size={20} /></span>
      <h3>{title}</h3>
      <p>{description}</p>
      {action}
    </div>
  );
}

export function ErrorState({ message, onRetry }: { message: string; onRetry: () => void }) {
  return (
    <div className="error-state" role="alert">
      <AlertCircle size={16} aria-hidden />
      <span>{message}</span>
      <button type="button" className="btn btn-secondary btn-sm" onClick={onRetry}>
        <RotateCw size={14} aria-hidden /> Spróbuj ponownie
      </button>
    </div>
  );
}

interface LoadMoreProps {
  hasNextPage: boolean;
  isFetchingNextPage: boolean;
  onLoadMore: () => void;
}

export function LoadMore({ hasNextPage, isFetchingNextPage, onLoadMore }: LoadMoreProps) {
  if (!hasNextPage) return null;
  return (
    <div className="table-footer">
      <button type="button" className="btn btn-secondary btn-sm" onClick={onLoadMore} disabled={isFetchingNextPage}>
        {isFetchingNextPage && <Loader2 size={14} className="spin" aria-hidden />}
        {isFetchingNextPage ? 'Ładowanie…' : 'Pokaż więcej'}
      </button>
    </div>
  );
}

export function PageHeader({ title, description, action }: { title: string; description: string; action?: ReactNode }) {
  return (
    <header className="page-header">
      <div>
        <h1>{title}</h1>
        <p>{description}</p>
      </div>
      {action}
    </header>
  );
}
