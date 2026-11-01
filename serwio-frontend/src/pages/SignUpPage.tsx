import { AlertCircle, Loader2 } from 'lucide-react';
import { useRef, useState, type FormEvent } from 'react';
import { Link, useNavigate } from 'react-router-dom';
import { api, ApiError } from '../api';
import { Field } from '../components/Field';
import type { OwnerRegistrationRequest } from '../types';

export function SignUpPage() {
  const navigate = useNavigate();
  const submitting = useRef(false);
  const [data, setData] = useState<OwnerRegistrationRequest>({
    firstName: '', lastName: '', email: '', password: '', confirmPassword: '',
  });
  const [pending, setPending] = useState(false);
  const [errors, setErrors] = useState<Record<string, string[]>>({});
  const [error, setError] = useState<string | null>(null);

  function change(name: keyof OwnerRegistrationRequest, value: string) {
    setData((previous) => ({ ...previous, [name]: value }));
  }

  async function submit(event: FormEvent) {
    event.preventDefault();
    if (submitting.current) return;
    const request = { ...data, firstName: data.firstName.trim(), lastName: data.lastName.trim(), email: data.email.trim() };
    const invalid: Record<string, string[]> = {};
    if (!request.firstName) invalid.firstName = ['Podaj imię.'];
    else if (request.firstName.length > 127) invalid.firstName = ['Imię może mieć najwyżej 127 znaków.'];
    if (!request.lastName) invalid.lastName = ['Podaj nazwisko.'];
    else if (request.lastName.length > 127) invalid.lastName = ['Nazwisko może mieć najwyżej 127 znaków.'];
    if (!request.email) invalid.email = ['Podaj adres e-mail.'];
    else if (request.email.length > 254 || !/^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(request.email)) {
      invalid.email = ['Podaj poprawny adres e-mail (do 254 znaków).'];
    }
    if (!request.password.trim()) invalid.password = ['Podaj hasło.'];
    else if (request.password.length < 8 || request.password.length > 72) invalid.password = ['Hasło musi mieć od 8 do 72 znaków.'];
    else if (new TextEncoder().encode(request.password).length > 72) invalid.password = ['Hasło może zajmować najwyżej 72 bajty UTF-8.'];
    if (!request.confirmPassword) invalid.confirmPassword = ['Potwierdź hasło.'];
    else if (request.password !== request.confirmPassword) invalid.confirmPassword = ['Hasła muszą być identyczne.'];
    setErrors(invalid);
    setError(null);
    if (Object.keys(invalid).length) return;
    submitting.current = true;
    setPending(true);
    try {
      await api.registerOwner(request);
      navigate('/sign-in', { replace: true, state: { reason: 'registered' } });
    } catch (cause) {
      if (cause instanceof ApiError && cause.status === 409) {
        setErrors({ email: ['Konto z tym adresem e-mail już istnieje.'] });
      } else if (cause instanceof ApiError && cause.status === 400 && Object.keys(cause.fieldErrors).length) {
        setErrors(cause.fieldErrors);
      } else {
        setError(cause instanceof ApiError && cause.status === 429
          ? 'Zbyt wiele prób. Spróbuj ponownie później.'
          : 'Nie udało się utworzyć konta. Spróbuj ponownie za chwilę.');
      }
    } finally {
      submitting.current = false;
      setPending(false);
    }
  }

  return (
    <div className="auth-page">
      <div className="auth-card">
        <div className="auth-brand">
          <span className="brand-mark" aria-hidden>S</span>
          <span className="brand-name">Serwio</span>
        </div>
        <h1>Zarejestruj się</h1>
        <p className="auth-subtitle">Utwórz konto właściciela obiektów</p>
        {error && <div className="inline-alert inline-alert-error" role="alert"><AlertCircle size={16} aria-hidden />{error}</div>}
        <form onSubmit={submit} className="auth-form" noValidate aria-busy={pending}>
          <div aria-live="polite">
            <Field label="Imię" name="firstName" autoComplete="given-name" maxLength={127} required
              value={data.firstName} onChange={(value) => change('firstName', value)} errors={errors.firstName} />
            <Field label="Nazwisko" name="lastName" autoComplete="family-name" maxLength={127} required
              value={data.lastName} onChange={(value) => change('lastName', value)} errors={errors.lastName} />
            <Field label="Adres e-mail" name="email" type="email" autoComplete="email" maxLength={254} required
              value={data.email} onChange={(value) => change('email', value)} errors={errors.email} />
            <Field label="Hasło" name="password" type="password" autoComplete="new-password" minLength={8} maxLength={72} required
              value={data.password} onChange={(value) => change('password', value)} errors={errors.password} hint="Od 8 do 72 znaków, maksymalnie 72 bajty UTF-8." />
            <Field label="Potwierdź hasło" name="confirmPassword" type="password" autoComplete="new-password" maxLength={72} required
              value={data.confirmPassword} onChange={(value) => change('confirmPassword', value)} errors={errors.confirmPassword} />
          </div>
          <button type="submit" className="btn btn-primary btn-block" disabled={pending}>
            {pending && <Loader2 size={14} className="spin" aria-hidden />}
            {pending ? 'Rejestrowanie…' : 'Zarejestruj się'}
          </button>
        </form>
        <p className="auth-link"><Link to="/sign-in">Masz już konto? Zaloguj się</Link></p>
      </div>
    </div>
  );
}
