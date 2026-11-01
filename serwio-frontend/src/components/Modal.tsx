import { AlertTriangle, Loader2, X } from 'lucide-react';
import { useEffect, useId, useRef, type ReactNode } from 'react';

interface ModalProps {
  title: string;
  description?: string;
  onClose: () => void;
  children: ReactNode;
  trapFocus?: boolean;
}

export function Modal({ title, description, onClose, children, trapFocus = false }: ModalProps) {
  const titleId = useId();
  const dialog = useRef<HTMLDivElement>(null);

  useEffect(() => {
    if (!trapFocus) return;
    const previous = document.activeElement as HTMLElement | null;
    const element = dialog.current!;
    element.focus();
    const keepFocus = (event: KeyboardEvent) => {
      if (event.key !== 'Tab') return;
      const controls = Array.from(element.querySelectorAll<HTMLElement>('button, input, select, textarea, [tabindex="0"]'))
        .filter((control) => !control.matches(':disabled') && control.getClientRects().length > 0);
      const first = controls[0];
      const last = controls[controls.length - 1];
      if (event.shiftKey && (document.activeElement === first || document.activeElement === element)) {
        event.preventDefault(); last?.focus();
      } else if (!event.shiftKey && (document.activeElement === last || document.activeElement === element)) {
        event.preventDefault(); first?.focus();
      }
    };
    element.addEventListener('keydown', keepFocus);
    return () => {
      element.removeEventListener('keydown', keepFocus);
      previous?.focus();
    };
  }, [trapFocus]);

  useEffect(() => {
    const onKey = (event: KeyboardEvent) => {
      if (event.key === 'Escape') onClose();
    };
    window.addEventListener('keydown', onKey);
    document.body.classList.add('modal-open');
    return () => {
      window.removeEventListener('keydown', onKey);
      document.body.classList.remove('modal-open');
    };
  }, [onClose]);

  return (
    <div className="modal-backdrop" onMouseDown={(e) => e.target === e.currentTarget && onClose()}>
      <div className="modal" role="dialog" aria-modal="true" aria-labelledby={titleId} ref={dialog} tabIndex={trapFocus ? -1 : undefined}>
        <header className="modal-header">
          <div>
            <h2 id={titleId}>{title}</h2>
            {description && <p className="modal-description">{description}</p>}
          </div>
          <button type="button" className="btn-icon" onClick={onClose} aria-label="Zamknij">
            <X size={16} />
          </button>
        </header>
        {children}
      </div>
    </div>
  );
}

interface ConfirmDialogProps {
  title: string;
  message: ReactNode;
  confirmLabel: string;
  pendingLabel?: string;
  pending: boolean;
  onConfirm: () => void;
  onCancel: () => void;
}

export function ConfirmDialog({
  title,
  message,
  confirmLabel,
  pendingLabel = 'Usuwanie…',
  pending,
  onConfirm,
  onCancel,
}: ConfirmDialogProps) {
  return (
    <Modal title={title} onClose={() => !pending && onCancel()}>
      <div className="modal-body confirm-body">
        <span className="confirm-icon" aria-hidden><AlertTriangle size={18} /></span>
        <p>{message}</p>
      </div>
      <footer className="modal-footer">
        <button type="button" className="btn btn-secondary" onClick={onCancel} disabled={pending}>Anuluj</button>
        <button type="button" className="btn btn-danger" onClick={onConfirm} disabled={pending} autoFocus>
          {pending && <Loader2 size={14} className="spin" aria-hidden />}
          {pending ? pendingLabel : confirmLabel}
        </button>
      </footer>
    </Modal>
  );
}
