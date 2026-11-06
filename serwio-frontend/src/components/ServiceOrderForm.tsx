import { useQueryClient } from '@tanstack/react-query';
import { Box, Loader2, MapPin } from 'lucide-react';
import { useEffect, useRef, useState, type FormEvent } from 'react';
import { useNavigate } from 'react-router-dom';
import { ApiError, errorMessage } from '../api';
import { queryKeys, useCreateServiceOrder, useLocation, useServiceObject } from '../queries';
import type { ServiceObjectResponse, ServiceOrderType } from '../types';
import { ErrorState } from './DataState';
import { TextareaField } from './Field';
import { Modal } from './Modal';
import { serviceObjectTypeLabel } from './ServiceObjectForm';
import { useToast } from './Toast';

const SERVICE_ORDER_TYPES: { value: ServiceOrderType; label: string; description: string; field: string; placeholder: string }[] = [
  {
    value: 'REPAIR',
    label: 'Naprawa',
    description: 'Usterka lub awaria do usunięcia.',
    field: 'Opis problemu',
    placeholder: 'Co się dzieje, od kiedy i w jakich sytuacjach?',
  },
  {
    value: 'INSPECTION',
    label: 'Przegląd',
    description: 'Okresowa kontrola stanu urządzenia.',
    field: 'Zakres przeglądu',
    placeholder: 'Co należy sprawdzić podczas przeglądu?',
  },
];

const MAX_DESCRIPTION_LENGTH = 100000;

export function serviceOrderTypeLabel(type: string): string {
  return SERVICE_ORDER_TYPES.find((t) => t.value === type)?.label ?? type;
}

export function serviceOrderDescriptionLabel(type: string): string {
  return SERVICE_ORDER_TYPES.find((t) => t.value === type)?.field ?? 'Opis problemu';
}

const GONE_MESSAGE = 'To urządzenie już nie istnieje. Lista została odświeżona.';

export function ServiceOrderCreateModal({ serviceObjectId, onClose }: { serviceObjectId: string; onClose: () => void }) {
  const object = useServiceObject(serviceObjectId);
  const toast = useToast();
  const queryClient = useQueryClient();
  const onCloseRef = useRef(onClose);
  onCloseRef.current = onClose;

  const gone = object.error instanceof ApiError && object.error.status === 404;
  useEffect(() => {
    if (!gone) return;
    toast.error(GONE_MESSAGE);
    void queryClient.invalidateQueries({ queryKey: queryKeys.serviceObjects });
    onCloseRef.current();
  }, [gone]);

  let body;
  if (object.isPending || gone) {
    body = <div className="modal-loading"><Loader2 size={18} className="spin" aria-label="Ładowanie" /></div>;
  } else if (object.isError) {
    body = (
      <div className="modal-body">
        <ErrorState message={errorMessage(object.error)} onRetry={() => void object.refetch()} />
      </div>
    );
  } else {
    body = <ServiceOrderForm object={object.data} onClose={onClose} />;
  }

  return (
    <Modal title="Utwórz zlecenie" description="Zlecenie trafi do firmy serwisowej obsługującej Twoje urządzenia." onClose={onClose}>
      {body}
    </Modal>
  );
}

function ObjectPreview({ object }: { object: ServiceObjectResponse }) {
  const location = useLocation(object.locationId);
  const equipment = [object.manufacturer, object.model].filter(Boolean).join(' ');

  return (
    <div className="order-preview">
      <div className="order-preview-item">
        <span className="cell-icon" aria-hidden><Box size={14} /></span>
        <div className="cell-text">
          <span className="order-preview-label">Urządzenie</span>
          <strong>{object.name}</strong>
          <span className="cell-sub">
            {[serviceObjectTypeLabel(object.type), equipment, object.serialNumber && `nr ${object.serialNumber}`]
              .filter(Boolean)
              .join(' · ')}
          </span>
        </div>
      </div>
      <div className="order-preview-item">
        <span className="cell-icon" aria-hidden><MapPin size={14} /></span>
        <div className="cell-text">
          <span className="order-preview-label">Lokalizacja</span>
          <strong>{location.data?.name ?? object.locationName}</strong>
          <span className="cell-sub">
            {location.data
              ? `${location.data.addressLine}, ${location.data.postalCode} ${location.data.city}`
              : location.isError
                ? 'Nie udało się pobrać adresu.'
                : <span className="skeleton skeleton-line" />}
          </span>
        </div>
      </div>
    </div>
  );
}

function submitErrorMessage(error: unknown): string {
  if (error instanceof ApiError && error.status === 403) return 'Nie masz uprawnień do tworzenia zleceń dla tego urządzenia.';
  if (error instanceof ApiError && error.status === 503) {
    return 'Nie można teraz utworzyć zlecenia, bo firma serwisowa jest niedostępna. Spróbuj później.';
  }
  return errorMessage(error);
}

function ServiceOrderForm({ object, onClose }: { object: ServiceObjectResponse; onClose: () => void }) {
  const [orderType, setOrderType] = useState<ServiceOrderType | ''>('');
  const [description, setDescription] = useState('');
  const [fieldErrors, setFieldErrors] = useState<Record<string, string[]>>({});
  const formRef = useRef<HTMLFormElement>(null);
  const create = useCreateServiceOrder();
  const toast = useToast();
  const navigate = useNavigate();
  const selected = SERVICE_ORDER_TYPES.find((t) => t.value === orderType);
  const typeInvalid = Boolean(fieldErrors.orderType?.length);
  const formErrorShown = create.isError && !fieldErrors.orderType?.length && !fieldErrors.description?.length;

  function showErrors(errors: Record<string, string[]>) {
    setFieldErrors(errors);
    requestAnimationFrame(() => formRef.current?.querySelector<HTMLElement>('[aria-invalid="true"]')?.focus());
  }

  function submit(event: FormEvent) {
    event.preventDefault();
    const errors: Record<string, string[]> = {};
    if (!orderType) errors.orderType = ['Wybierz typ zlecenia.'];
    if (description.trim() === '') errors.description = [`Uzupełnij pole „${selected?.field ?? 'Opis problemu'}”.`];
    if (Object.keys(errors).length > 0 || !orderType) {
      create.reset();
      showErrors(errors);
      return;
    }
    setFieldErrors({});
    create.mutate({ serviceObjectId: object.id, orderType, description: description.trim() }, {
      onSuccess: (created) => {
        toast.success(`Utworzono zlecenie: ${serviceOrderTypeLabel(created.orderType).toLowerCase()} — ${created.serviceObjectName}.`);
        navigate(`/orders/${encodeURIComponent(created.id)}`);
      },
      onError: (e) => {
        if (e instanceof ApiError && e.status === 404) {
          toast.error(GONE_MESSAGE);
          onClose();
        } else if (e instanceof ApiError) {
          showErrors(e.fieldErrors);
        }
      },
    });
  }

  if (!object.active) {
    return (
      <>
        <div className="modal-body">
          <ObjectPreview object={object} />
          <div className="inline-alert inline-alert-info">To urządzenie jest nieaktywne, więc nie można utworzyć dla niego zlecenia.</div>
        </div>
        <footer className="modal-footer">
          <button type="button" className="btn btn-secondary" onClick={onClose}>Zamknij</button>
        </footer>
      </>
    );
  }

  return (
    <form ref={formRef} onSubmit={submit} noValidate>
      <div className="modal-body">
        {formErrorShown && (
          <div className="inline-alert inline-alert-error" role="alert">
            {submitErrorMessage(create.error)}
            {fieldErrors.serviceObjectId?.length ? ` Urządzenie: ${fieldErrors.serviceObjectId.join(' ')}` : null}
          </div>
        )}
        <ObjectPreview object={object} />
        <fieldset className={`field choice-field${typeInvalid ? ' field-invalid' : ''}`}>
          <legend className="field-label">Typ zlecenia</legend>
          <div className="choice-group">
            {SERVICE_ORDER_TYPES.map((t, index) => (
              <label key={t.value} className={`choice${orderType === t.value ? ' choice-selected' : ''}`}>
                <input
                  type="radio"
                  name="orderType"
                  value={t.value}
                  checked={orderType === t.value}
                  onChange={() => {
                    setOrderType(t.value);
                    setFieldErrors((f) => ({ ...f, orderType: [] }));
                  }}
                  aria-invalid={typeInvalid && index === 0 ? true : undefined}
                  aria-describedby={typeInvalid ? 'orderType-error' : undefined}
                  autoFocus={index === 0}
                />
                <span className="choice-text">
                  <span className="choice-title">{t.label}</span>
                  <span className="choice-description">{t.description}</span>
                </span>
              </label>
            ))}
          </div>
          {typeInvalid && <span id="orderType-error" className="field-error">{fieldErrors.orderType!.join(' ')}</span>}
        </fieldset>
        <TextareaField
          label={selected?.field ?? 'Opis problemu'}
          name="description"
          rows={5}
          value={description}
          onChange={(v) => {
            setDescription(v);
            setFieldErrors((f) => ({ ...f, description: [] }));
          }}
          errors={fieldErrors.description}
          required
          maxLength={MAX_DESCRIPTION_LENGTH}
          placeholder={selected?.placeholder ?? 'Co się dzieje, od kiedy i w jakich sytuacjach?'}
        />
      </div>
      <footer className="modal-footer">
        <button type="button" className="btn btn-secondary" onClick={onClose} disabled={create.isPending}>Anuluj</button>
        <button type="submit" className="btn btn-primary" disabled={create.isPending}>
          {create.isPending && <Loader2 size={14} className="spin" aria-hidden />}
          {create.isPending ? 'Tworzenie…' : 'Utwórz zlecenie'}
        </button>
      </footer>
    </form>
  );
}
