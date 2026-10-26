import { useQueryClient } from '@tanstack/react-query';
import { Loader2, MapPin, Plus } from 'lucide-react';
import { useEffect, useRef, useState, type FormEvent } from 'react';
import { ApiError, errorMessage } from '../api';
import { queryKeys, useAllLocations, useSaveServiceObject, useServiceObject } from '../queries';
import type { LocationListItemResponse, ServiceObjectRequest } from '../types';
import { EmptyState, ErrorState } from './DataState';
import { Field, SelectField, TextareaField } from './Field';
import { Modal } from './Modal';
import { useToast } from './Toast';

const SERVICE_OBJECT_TYPES = [
  { value: 'AC', label: 'Klimatyzacja' },
  { value: 'HEAT_PUMP', label: 'Pompa ciepła' },
  { value: 'VENTILATION', label: 'Wentylacja' },
];

export function serviceObjectTypeLabel(type: string): string {
  return SERVICE_OBJECT_TYPES.find((t) => t.value === type)?.label ?? type;
}

interface ServiceObjectFormValues {
  locationId: string;
  name: string;
  type: string;
  manufacturer: string;
  model: string;
  serialNumber: string;
  description: string;
}

interface ServiceObjectFormModalProps {
  editId?: string;
  defaultLocationId?: string;
  onClose: () => void;
  onAddLocation: () => void;
}

export function ServiceObjectFormModal({ editId, defaultLocationId, onClose, onAddLocation }: ServiceObjectFormModalProps) {
  const locations = useAllLocations();
  const details = useServiceObject(editId);
  const toast = useToast();
  const onCloseRef = useRef(onClose);
  onCloseRef.current = onClose;
  const queryClient = useQueryClient();

  const gone = details.error instanceof ApiError && details.error.status === 404;
  useEffect(() => {
    if (!gone) return;
    toast.error('To urządzenie już nie istnieje. Lista została odświeżona.');
    void queryClient.invalidateQueries({ queryKey: queryKeys.serviceObjects });
    onCloseRef.current();
  }, [gone]);

  const title = editId ? 'Edytuj urządzenie' : 'Dodaj urządzenie';
  const loadError = locations.error ?? details.error;

  let body;
  if (locations.isPending || (editId && details.isPending)) {
    body = <div className="modal-loading"><Loader2 size={18} className="spin" aria-label="Ładowanie" /></div>;
  } else if (loadError) {
    body = (
      <div className="modal-body">
        <ErrorState
          message={errorMessage(loadError)}
          onRetry={() => { void locations.refetch(); if (editId) void details.refetch(); }}
        />
      </div>
    );
  } else if (locations.data!.length === 0) {
    body = (
      <div className="modal-body">
        <EmptyState
          icon={MapPin}
          title="Najpierw dodaj lokalizację"
          description="Każde urządzenie musi być przypisane do lokalizacji, a nie masz jeszcze żadnej."
          action={
            <button type="button" className="btn btn-primary" onClick={onAddLocation}>
              <Plus size={14} aria-hidden /> Dodaj lokalizację
            </button>
          }
        />
      </div>
    );
  } else {
    const d = details.data;
    body = (
      <ServiceObjectForm
        editId={editId}
        locations={locations.data!}
        initial={{
          locationId: d?.locationId ?? defaultLocationId ?? (locations.data!.length === 1 ? locations.data![0].id : ''),
          name: d?.name ?? '',
          type: d?.type ?? '',
          manufacturer: d?.manufacturer ?? '',
          model: d?.model ?? '',
          serialNumber: d?.serialNumber ?? '',
          description: d?.description ?? '',
        }}
        attributes={d?.attributes ?? null}
        onClose={onClose}
      />
    );
  }

  return (
    <Modal title={title} description="Obiekt serwisowy przypisany do jednej z Twoich lokalizacji." onClose={onClose}>
      {body}
    </Modal>
  );
}

const optional = (value: string) => (value.trim() === '' ? null : value.trim());

interface ServiceObjectFormProps {
  editId?: string;
  locations: LocationListItemResponse[];
  initial: ServiceObjectFormValues;
  attributes: Record<string, unknown> | null;
  onClose: () => void;
}

function ServiceObjectForm({ editId, locations, initial, attributes, onClose }: ServiceObjectFormProps) {
  const [values, setValues] = useState(initial);
  const [fieldErrors, setFieldErrors] = useState<Record<string, string[]>>({});
  const save = useSaveServiceObject();
  const toast = useToast();

  const set = (key: keyof ServiceObjectFormValues) => (value: string) => {
    setValues((v) => ({ ...v, [key]: value }));
    setFieldErrors((f) => ({ ...f, [key]: [] }));
  };

  function submit(event: FormEvent) {
    event.preventDefault();
    if (!values.locationId) {
      setFieldErrors({ locationId: ['Wybierz lokalizację.'] });
      return;
    }
    setFieldErrors({});
    const data: ServiceObjectRequest = {
      locationId: values.locationId,
      name: values.name,
      type: values.type.trim(),
      description: optional(values.description),
      manufacturer: optional(values.manufacturer),
      model: optional(values.model),
      serialNumber: optional(values.serialNumber),
      attributes,
    };
    save.mutate({ id: editId, data }, {
      onSuccess: () => {
        toast.success(editId ? 'Zapisano zmiany urządzenia.' : 'Dodano urządzenie.');
        onClose();
      },
      onError: (e) => {
        if (e instanceof ApiError && e.status === 404) {
          toast.error('Urządzenie lub wybrana lokalizacja już nie istnieje. Odśwież dane.');
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
          <div className="inline-alert inline-alert-error" role="alert">{errorMessage(save.error)}</div>
        )}
        <SelectField label="Lokalizacja" name="locationId" value={values.locationId} onChange={set('locationId')}
          errors={fieldErrors.locationId} required>
          <option value="" disabled>Wybierz lokalizację…</option>
          {locations.map((l) => <option key={l.id} value={l.id}>{l.name} — {l.city}</option>)}
        </SelectField>
        <div className="field-row">
          <Field label="Nazwa" name="name" value={values.name} onChange={set('name')}
            errors={fieldErrors.name} required maxLength={255} autoFocus placeholder="np. Klimatyzator — recepcja" />
          <Field label="Typ" name="type" value={values.type} onChange={set('type')}
            errors={fieldErrors.type} required maxLength={100} list="service-object-types"
            hint="Wybierz z listy albo wpisz własny" />
        </div>
        <datalist id="service-object-types">
          {SERVICE_OBJECT_TYPES.map((t) => <option key={t.value} value={t.value}>{t.label}</option>)}
        </datalist>
        <div className="field-row">
          <Field label="Producent" name="manufacturer" value={values.manufacturer} onChange={set('manufacturer')}
            errors={fieldErrors.manufacturer} maxLength={255} />
          <Field label="Model" name="model" value={values.model} onChange={set('model')}
            errors={fieldErrors.model} maxLength={255} />
        </div>
        <Field label="Numer seryjny" name="serialNumber" value={values.serialNumber} onChange={set('serialNumber')}
          errors={fieldErrors.serialNumber} maxLength={255} />
        <TextareaField label="Opis" name="description" rows={3} value={values.description}
          onChange={set('description')} errors={fieldErrors.description} />
      </div>
      <footer className="modal-footer">
        <button type="button" className="btn btn-secondary" onClick={onClose} disabled={save.isPending}>Anuluj</button>
        <button type="submit" className="btn btn-primary" disabled={save.isPending}>
          {save.isPending && <Loader2 size={14} className="spin" aria-hidden />}
          {save.isPending ? 'Zapisywanie…' : editId ? 'Zapisz zmiany' : 'Dodaj urządzenie'}
        </button>
      </footer>
    </form>
  );
}
