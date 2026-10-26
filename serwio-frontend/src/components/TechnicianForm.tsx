import { useQueryClient } from '@tanstack/react-query';
import { Loader2 } from 'lucide-react';
import { useEffect, useRef, useState, type FormEvent, type ReactNode } from 'react';
import { ApiError, errorMessage, hasFieldErrors } from '../api';
import {
  queryKeys,
  useAllBranches,
  useCreateTechnician,
  useSetTechnicianPassword,
  useTechnician,
  useUpdateTechnician,
} from '../queries';
import type { ServiceBranchResponse, TechnicianResponse } from '../types';
import { ErrorState } from './DataState';
import { Field, SelectField } from './Field';
import { Modal } from './Modal';
import { useToast } from './Toast';

type FieldErrors = Record<string, string[]>;

const PASSWORD_HINT = 'Od 8 do 72 znaków. Serwisant zmieni je po pierwszym zalogowaniu.';

function FormAlert({ error }: { error: unknown }) {
  if (!error || hasFieldErrors(error)) return null;
  return <div className="inline-alert inline-alert-error" role="alert">{errorMessage(error)}</div>;
}

function SubmitFooter({ pending, label, onClose }: { pending: boolean; label: string; onClose: () => void }) {
  return (
    <footer className="modal-footer">
      <button type="button" className="btn btn-secondary" onClick={onClose} disabled={pending}>Anuluj</button>
      <button type="submit" className="btn btn-primary" disabled={pending}>
        {pending && <Loader2 size={14} className="spin" aria-hidden />}
        {pending ? 'Zapisywanie…' : label}
      </button>
    </footer>
  );
}

function BranchOptions({ branches, currentId }: { branches: ServiceBranchResponse[]; currentId?: string }) {
  return (
    <>
      {branches
        .filter((b) => b.active || b.id === currentId)
        .map((b) => (
          <option key={b.id} value={b.id}>{b.active ? b.name : `${b.name} (nieaktywny)`}</option>
        ))}
    </>
  );
}

function LoadingOrError({ query, children }: { query: { isPending: boolean; isError: boolean; error: unknown; refetch: () => unknown }; children: ReactNode }) {
  if (query.isPending) {
    return <div className="modal-loading"><Loader2 size={18} className="spin" aria-label="Ładowanie" /></div>;
  }
  if (query.isError) {
    return (
      <div className="modal-body">
        <ErrorState message={errorMessage(query.error)} onRetry={() => void query.refetch()} />
      </div>
    );
  }
  return <>{children}</>;
}

export function TechnicianCreateModal({ defaultBranchId, onClose }: { defaultBranchId?: string; onClose: () => void }) {
  const branches = useAllBranches();

  return (
    <Modal title="Dodaj serwisanta" description="Serwisant zaloguje się adresem e-mail i hasłem startowym." onClose={onClose}>
      <LoadingOrError query={branches}>
        {branches.data && <TechnicianCreateForm branches={branches.data} defaultBranchId={defaultBranchId} onClose={onClose} />}
      </LoadingOrError>
    </Modal>
  );
}

function TechnicianCreateForm({ branches, defaultBranchId, onClose }: {
  branches: ServiceBranchResponse[];
  defaultBranchId?: string;
  onClose: () => void;
}) {
  const active = branches.filter((b) => b.active);
  const initialBranch = active.find((b) => b.id === defaultBranchId)?.id ?? (active.length === 1 ? active[0].id : '');
  const [values, setValues] = useState({ email: '', displayName: '', branchId: initialBranch, password: '' });
  const [fieldErrors, setFieldErrors] = useState<FieldErrors>({});
  const create = useCreateTechnician();
  const toast = useToast();

  const set = (key: keyof typeof values) => (value: string) => {
    setValues((v) => ({ ...v, [key]: value }));
    setFieldErrors((f) => ({ ...f, [key]: [] }));
  };

  function submit(event: FormEvent) {
    event.preventDefault();
    if (!values.branchId) {
      setFieldErrors({ branchId: ['Wybierz oddział.'] });
      return;
    }
    setFieldErrors({});
    create.mutate({ ...values, email: values.email.trim() }, {
      onSuccess: (created) => {
        toast.success(`Dodano serwisanta ${created.displayName}.`);
        onClose();
      },
      onError: (e) => {
        if (e instanceof ApiError) setFieldErrors(e.fieldErrors);
      },
    });
  }

  return (
    <form onSubmit={submit} noValidate>
      <div className="modal-body">
        <FormAlert error={create.error} />
        <Field label="Imię i nazwisko" name="displayName" value={values.displayName} onChange={set('displayName')}
          errors={fieldErrors.displayName} required maxLength={255} autoFocus placeholder="np. Jan Kowalski" />
        <Field label="E-mail" name="email" type="email" value={values.email} onChange={set('email')}
          errors={fieldErrors.email} required maxLength={254} autoComplete="off" placeholder="jan@firma.pl" />
        <SelectField label="Oddział" name="branchId" value={values.branchId} onChange={set('branchId')}
          errors={fieldErrors.branchId} required>
          <option value="" disabled>Wybierz oddział</option>
          <BranchOptions branches={branches} />
        </SelectField>
        <Field label="Hasło startowe" name="password" type="password" value={values.password} onChange={set('password')}
          errors={fieldErrors.password} required minLength={8} maxLength={72} autoComplete="new-password" hint={PASSWORD_HINT} />
      </div>
      <SubmitFooter pending={create.isPending} label="Dodaj serwisanta" onClose={onClose} />
    </form>
  );
}

export function TechnicianEditModal({ id, onClose }: { id: string; onClose: () => void }) {
  const technician = useTechnician(id);
  const branches = useAllBranches();
  const toast = useToast();
  const queryClient = useQueryClient();
  const onCloseRef = useRef(onClose);
  onCloseRef.current = onClose;

  const gone = technician.error instanceof ApiError && technician.error.status === 404;
  useEffect(() => {
    if (!gone) return;
    toast.error('Ten serwisant już nie istnieje. Lista została odświeżona.');
    void queryClient.invalidateQueries({ queryKey: queryKeys.technicians });
    onCloseRef.current();
  }, [gone]);

  return (
    <Modal title="Edytuj serwisanta" onClose={onClose}>
      <LoadingOrError query={technician}>
        <LoadingOrError query={branches}>
          {technician.data && branches.data && (
            <TechnicianEditForm technician={technician.data} branches={branches.data} onClose={onClose} />
          )}
        </LoadingOrError>
      </LoadingOrError>
    </Modal>
  );
}

function TechnicianEditForm({ technician, branches, onClose }: {
  technician: TechnicianResponse;
  branches: ServiceBranchResponse[];
  onClose: () => void;
}) {
  const [displayName, setDisplayName] = useState(technician.displayName);
  const [branchId, setBranchId] = useState(technician.branchId);
  const [active, setActive] = useState(technician.active);
  const [fieldErrors, setFieldErrors] = useState<FieldErrors>({});
  const update = useUpdateTechnician();
  const toast = useToast();
  const options = branches.some((b) => b.id === technician.branchId)
    ? branches
    : [...branches, { id: technician.branchId, name: technician.branchName, active: false, activeTechnicianCount: 0, createdAt: '' }];

  function submit(event: FormEvent) {
    event.preventDefault();
    setFieldErrors({});
    update.mutate({ id: technician.id, data: { displayName, branchId, active } }, {
      onSuccess: (saved) => {
        toast.success(`Zapisano zmiany serwisanta ${saved.displayName}.`);
        onClose();
      },
      onError: (e) => {
        if (e instanceof ApiError && e.status === 404) {
          toast.error('Nie znaleziono serwisanta lub oddziału. Dane zostały odświeżone.');
          onClose();
        } else if (e instanceof ApiError) {
          setFieldErrors(e.fieldErrors);
        }
      },
    });
  }

  return (
    <form onSubmit={submit} noValidate>
      <div className="modal-body">
        <FormAlert error={update.error} />
        <Field label="E-mail" name="email" value={technician.email} onChange={() => undefined} required disabled
          hint="Adresu e-mail nie można zmienić." />
        <Field label="Imię i nazwisko" name="displayName" value={displayName}
          onChange={(v) => { setDisplayName(v); setFieldErrors((f) => ({ ...f, displayName: [] })); }}
          errors={fieldErrors.displayName} required maxLength={255} autoFocus />
        <SelectField label="Oddział" name="branchId" value={branchId}
          onChange={(v) => { setBranchId(v); setFieldErrors((f) => ({ ...f, branchId: [] })); }}
          errors={fieldErrors.branchId} required>
          <BranchOptions branches={options} currentId={technician.branchId} />
        </SelectField>
        <label className="check-field">
          <input type="checkbox" name="active" checked={active} onChange={(e) => setActive(e.target.checked)} />
          <span>
            <span className="field-label">Konto aktywne</span>
            <span className="field-hint">Nieaktywny serwisant nie może się zalogować.</span>
          </span>
        </label>
      </div>
      <SubmitFooter pending={update.isPending} label="Zapisz zmiany" onClose={onClose} />
    </form>
  );
}

export function TechnicianPasswordModal({ technician, onClose }: { technician: TechnicianResponse; onClose: () => void }) {
  const [password, setPassword] = useState('');
  const [fieldErrors, setFieldErrors] = useState<FieldErrors>({});
  const setTechnicianPassword = useSetTechnicianPassword();
  const toast = useToast();

  function submit(event: FormEvent) {
    event.preventDefault();
    setFieldErrors({});
    setTechnicianPassword.mutate({ id: technician.id, data: { password } }, {
      onSuccess: () => {
        toast.success(`Ustawiono nowe hasło startowe dla ${technician.displayName}.`);
        onClose();
      },
      onError: (e) => {
        if (e instanceof ApiError && e.status === 404) {
          toast.error('Ten serwisant już nie istnieje. Lista została odświeżona.');
          onClose();
        } else if (e instanceof ApiError) {
          setFieldErrors(e.fieldErrors);
        }
      },
    });
  }

  return (
    <Modal
      title="Nowe hasło startowe"
      description={`${technician.displayName} (${technician.email})`}
      onClose={onClose}
    >
      <form onSubmit={submit} noValidate>
        <div className="modal-body">
          <FormAlert error={setTechnicianPassword.error} />
          {technician.active && (
            <div className="inline-alert inline-alert-info">
              Status konta zmieni się na „Oczekuje na aktywację”, a dotychczasowe hasło przestanie działać.
            </div>
          )}
          <Field label="Hasło startowe" name="password" type="password" value={password}
            onChange={(v) => { setPassword(v); setFieldErrors({}); }}
            errors={fieldErrors.password} required minLength={8} maxLength={72} autoFocus autoComplete="new-password"
            hint={PASSWORD_HINT} />
        </div>
        <SubmitFooter pending={setTechnicianPassword.isPending} label="Ustaw hasło" onClose={onClose} />
      </form>
    </Modal>
  );
}
