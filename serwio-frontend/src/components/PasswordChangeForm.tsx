import { Loader2 } from 'lucide-react';
import { useState, type FormEvent } from 'react';
import { ApiError, errorMessage } from '../api';
import { useChangeOwnPassword } from '../queries';
import type { PasswordChangeRequest } from '../types';
import { Field } from './Field';
import { useToast } from './Toast';

const EMPTY = { currentPassword: '', newPassword: '', confirmPassword: '' };

export function PasswordChangeForm() {
  const [values, setValues] = useState(EMPTY);
  const [fieldErrors, setFieldErrors] = useState<Record<string, string[]>>({});
  const [formError, setFormError] = useState<string | null>(null);
  const changePassword = useChangeOwnPassword();
  const toast = useToast();

  const set = (key: keyof typeof EMPTY) => (value: string) => {
    setValues((previous) => ({ ...previous, [key]: value }));
    setFieldErrors((previous) => ({
      ...previous,
      [key]: [],
      ...(key === 'newPassword' ? { confirmPassword: [] } : {}),
    }));
    setFormError(null);
  };

  function submit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    if (changePassword.isPending) return;
    const form = event.currentTarget;
    const errors: Record<string, string[]> = {};
    if (!values.currentPassword.trim()) errors.currentPassword = ['Podaj aktualne hasło.'];
    if (!values.newPassword.trim()) errors.newPassword = ['Podaj nowe hasło.'];
    else if (values.newPassword.length < 8 || values.newPassword.length > 72) {
      errors.newPassword = ['Nowe hasło musi mieć od 8 do 72 znaków.'];
    }
    if (!values.confirmPassword) errors.confirmPassword = ['Powtórz nowe hasło.'];
    else if (values.newPassword !== values.confirmPassword) errors.confirmPassword = ['Hasła muszą być takie same.'];
    setFieldErrors(errors);
    setFormError(null);
    if (Object.keys(errors).length) {
      form.querySelector<HTMLInputElement>(`[name="${Object.keys(errors)[0]}"]`)?.focus();
      return;
    }

    const data: PasswordChangeRequest = { currentPassword: values.currentPassword, newPassword: values.newPassword };
    changePassword.mutate(data, {
      onSuccess: () => {
        setValues(EMPTY);
        changePassword.reset();
        toast.success('Hasło zostało zmienione.');
      },
      onError: (error) => {
        if (error instanceof ApiError && error.status === 401) return;
        if (error instanceof ApiError && error.status === 400) {
          const errors = Object.keys(error.fieldErrors).length
            ? error.fieldErrors
            : { currentPassword: [error.message] };
          setFieldErrors(errors);
          form.querySelector<HTMLInputElement>(`[name="${Object.keys(errors)[0]}"]`)?.focus();
        } else {
          setFormError(errorMessage(error));
        }
      },
    });
  }

  return (
    <form onSubmit={submit} noValidate aria-busy={changePassword.isPending}>
      {formError && <div className="inline-alert inline-alert-error" role="alert">{formError}</div>}
      <Field label="Aktualne hasło" name="currentPassword" id="currentPassword" type="password"
        autoComplete="current-password" value={values.currentPassword} onChange={set('currentPassword')}
        errors={fieldErrors.currentPassword} required disabled={changePassword.isPending} />
      <Field label="Nowe hasło" name="newPassword" type="password" autoComplete="new-password"
        value={values.newPassword} onChange={set('newPassword')} errors={fieldErrors.newPassword}
        required disabled={changePassword.isPending} />
      <Field label="Powtórz nowe hasło" name="confirmPassword" type="password" autoComplete="new-password"
        value={values.confirmPassword} onChange={set('confirmPassword')} errors={fieldErrors.confirmPassword}
        required disabled={changePassword.isPending} />
      <button type="submit" className="btn btn-primary" disabled={changePassword.isPending}>
        {changePassword.isPending && <Loader2 size={14} className="spin" aria-hidden />}
        {changePassword.isPending ? 'Zapisywanie…' : 'Zmień hasło'}
      </button>
    </form>
  );
}
