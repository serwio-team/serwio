import { Loader2 } from 'lucide-react';
import { useState, type FormEvent } from 'react';
import { ApiError, errorMessage, hasFieldErrors } from '../api';
import { useDeactivateBranch, useSaveBranch } from '../queries';
import type { ServiceBranchResponse } from '../types';
import { Field } from './Field';
import { ConfirmDialog, Modal } from './Modal';
import { useToast } from './Toast';

interface BranchFormModalProps {
  branch?: ServiceBranchResponse;
  onClose: () => void;
}

export function BranchFormModal({ branch, onClose }: BranchFormModalProps) {
  const [name, setName] = useState(branch?.name ?? '');
  const [fieldErrors, setFieldErrors] = useState<Record<string, string[]>>({});
  const save = useSaveBranch();
  const toast = useToast();

  function submit(event: FormEvent) {
    event.preventDefault();
    setFieldErrors({});
    save.mutate({ id: branch?.id, data: { name } }, {
      onSuccess: (saved) => {
        toast.success(branch ? `Zmieniono nazwę oddziału na „${saved.name}”.` : `Dodano oddział „${saved.name}”.`);
        onClose();
      },
      onError: (e) => {
        if (e instanceof ApiError && e.status === 404) {
          toast.error('Ten oddział już nie istnieje. Lista została odświeżona.');
          onClose();
        } else if (e instanceof ApiError) {
          setFieldErrors(e.fieldErrors);
        }
      },
    });
  }

  return (
    <Modal
      title={branch ? 'Zmień nazwę oddziału' : 'Dodaj oddział'}
      description={branch ? undefined : 'Nowy oddział jest od razu aktywny.'}
      onClose={onClose}
    >
      <form onSubmit={submit} noValidate>
        <div className="modal-body">
          {save.isError && !hasFieldErrors(save.error) && (
            <div className="inline-alert inline-alert-error" role="alert">{errorMessage(save.error)}</div>
          )}
          <Field
            label="Nazwa"
            name="name"
            value={name}
            onChange={(value) => {
              setName(value);
              setFieldErrors({});
            }}
            errors={fieldErrors.name}
            required
            maxLength={255}
            autoFocus
            placeholder="np. Kraków"
          />
        </div>
        <footer className="modal-footer">
          <button type="button" className="btn btn-secondary" onClick={onClose} disabled={save.isPending}>Anuluj</button>
          <button type="submit" className="btn btn-primary" disabled={save.isPending}>
            {save.isPending && <Loader2 size={14} className="spin" aria-hidden />}
            {save.isPending ? 'Zapisywanie…' : branch ? 'Zapisz nazwę' : 'Dodaj oddział'}
          </button>
        </footer>
      </form>
    </Modal>
  );
}

export function BranchDeactivateDialog({ branch, onClose, onDone }: {
  branch: ServiceBranchResponse;
  onClose: () => void;
  onDone?: () => void;
}) {
  const deactivate = useDeactivateBranch();
  const toast = useToast();

  function confirm() {
    deactivate.mutate(branch.id, {
      onSuccess: () => {
        toast.success(`Dezaktywowano oddział „${branch.name}”.`);
        onDone?.();
      },
      onError: (e) => {
        if (e instanceof ApiError && e.status === 404) {
          toast.error('Ten oddział już nie istnieje. Lista została odświeżona.');
        } else {
          toast.error(errorMessage(e));
        }
      },
      onSettled: onClose,
    });
  }

  return (
    <ConfirmDialog
      title="Dezaktywować oddział?"
      message={<>Oddział <strong>{branch.name}</strong> zostanie dezaktywowany. Nie będzie można przypisywać do niego serwisantów. Tej operacji nie można cofnąć.</>}
      confirmLabel="Dezaktywuj oddział"
      pendingLabel="Dezaktywowanie…"
      pending={deactivate.isPending}
      onConfirm={confirm}
      onCancel={onClose}
    />
  );
}
