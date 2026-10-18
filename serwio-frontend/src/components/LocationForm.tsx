import { useQueryClient } from '@tanstack/react-query';
import { Loader2 } from 'lucide-react';
import { useEffect, useRef, useState, type FormEvent } from 'react';
import { ApiError, errorMessage } from '../api';
import { queryKeys, useLocation, useSaveLocation } from '../queries';
import type { LocationRequest } from '../types';
import { ErrorState } from './DataState';
import { Field } from './Field';
import { Modal } from './Modal';
import { useToast } from './Toast';

const EMPTY: LocationRequest = {
  name: '',
  addressLine: '',
  city: '',
  postalCode: '',
  country: 'PL',
  timezone: 'Europe/Warsaw',
};

interface LocationFormModalProps {
  // Brak id = dodawanie nowej lokalizacji.
  editId?: string;
  onClose: () => void;
}

export function LocationFormModal({ editId, onClose }: LocationFormModalProps) {
  // Lista ma tylko podstawowe pola, więc do edycji pobieramy szczegóły po id.
  const details = useLocation(editId);
  const toast = useToast();
  const onCloseRef = useRef(onClose);
  onCloseRef.current = onClose;
  const queryClient = useQueryClient();

  // Lokalizacja zniknęła (404) — zamykamy formularz, lista odświeży się sama.
  const gone = details.error instanceof ApiError && details.error.status === 404;
  useEffect(() => {
    if (!gone) return;
    toast.error('Ta lokalizacja już nie istnieje. Lista została odświeżona.');
    void queryClient.invalidateQueries({ queryKey: queryKeys.locations });
    onCloseRef.current();
  }, [gone]);

  return (
    <Modal
      title={editId ? 'Edytuj lokalizację' : 'Dodaj lokalizację'}
      description="Adres miejsca, w którym znajdują się Twoje urządzenia."
      onClose={onClose}
    >
      {!editId ? (
        <LocationForm initial={EMPTY} onClose={onClose} />
      ) : details.isPending ? (
        <div className="modal-loading"><Loader2 size={18} className="spin" aria-label="Ładowanie" /></div>
      ) : details.isError ? (
        <div className="modal-body">
          <ErrorState message={errorMessage(details.error)} onRetry={() => void details.refetch()} />
        </div>
      ) : (
        <LocationForm
          editId={editId}
          initial={{
            name: details.data.name,
            addressLine: details.data.addressLine,
            city: details.data.city,
            postalCode: details.data.postalCode,
            country: details.data.country,
            timezone: details.data.timezone,
          }}
          onClose={onClose}
        />
      )}
    </Modal>
  );
}

function LocationForm({ editId, initial, onClose }: { editId?: string; initial: LocationRequest; onClose: () => void }) {
  const [values, setValues] = useState<LocationRequest>(initial);
  const save = useSaveLocation();
  const toast = useToast();
  const apiError = save.error instanceof ApiError ? save.error : null;
  const [fieldErrors, setFieldErrors] = useState<Record<string, string[]>>({});

  const set = (key: keyof LocationRequest) => (value: string) => {
    setValues((v) => ({ ...v, [key]: value }));
    setFieldErrors((f) => ({ ...f, [key]: [] }));
  };

  function submit(event: FormEvent) {
    event.preventDefault();
    setFieldErrors({});
    const data = { ...values, country: values.country.trim().toUpperCase(), timezone: values.timezone.trim() };
    save.mutate({ id: editId, data }, {
      onSuccess: () => {
        toast.success(editId ? 'Zapisano zmiany lokalizacji.' : 'Dodano lokalizację.');
        onClose();
      },
      onError: (e) => {
        if (e instanceof ApiError && e.status === 404) {
          toast.error('Ta lokalizacja już nie istnieje. Lista została odświeżona.');
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
        {save.isError && (
          <div className="inline-alert inline-alert-error" role="alert">
            {apiError ? apiError.message : errorMessage(save.error)}
          </div>
        )}
        <Field label="Nazwa" name="name" value={values.name} onChange={set('name')}
          errors={fieldErrors.name} required maxLength={255} autoFocus placeholder="np. Biuro" />
        <Field label="Adres" name="addressLine" value={values.addressLine} onChange={set('addressLine')}
          errors={fieldErrors.addressLine} required maxLength={500} placeholder="np. Testowa 1" />
        <div className="field-row">
          <Field label="Kod pocztowy" name="postalCode" value={values.postalCode} onChange={set('postalCode')}
            errors={fieldErrors.postalCode} required maxLength={32} placeholder="00-001" />
          <Field label="Miasto" name="city" value={values.city} onChange={set('city')}
            errors={fieldErrors.city} required maxLength={150} placeholder="np. Warszawa" />
        </div>
        <div className="field-row">
          <Field label="Kraj" name="country" value={values.country} onChange={set('country')}
            errors={fieldErrors.country} required maxLength={2} hint="Dwie wielkie litery, np. PL" />
          <Field label="Strefa czasowa" name="timezone" value={values.timezone} onChange={set('timezone')}
            errors={fieldErrors.timezone} required maxLength={100} hint="np. Europe/Warsaw" />
        </div>
      </div>
      <footer className="modal-footer">
        <button type="button" className="btn btn-secondary" onClick={onClose} disabled={save.isPending}>Anuluj</button>
        <button type="submit" className="btn btn-primary" disabled={save.isPending}>
          {save.isPending && <Loader2 size={14} className="spin" aria-hidden />}
          {save.isPending ? 'Zapisywanie…' : editId ? 'Zapisz zmiany' : 'Dodaj lokalizację'}
        </button>
      </footer>
    </form>
  );
}
