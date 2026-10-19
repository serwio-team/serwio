import { useQueryClient } from '@tanstack/react-query';
import { AlertCircle, Info, Loader2 } from 'lucide-react';
import { useState, type FormEvent } from 'react';
import { Navigate, useLocation, useNavigate } from 'react-router-dom';
import { api, errorMessage } from '../api';
import { useCurrentUser } from '../queries';

interface SignInState {
  reason?: 'expired' | 'signed-out';
  from?: string;
}

const NOTICES = {
  expired: 'Sesja wygasła. Zaloguj się ponownie.',
  'signed-out': 'Wylogowano.',
};

export function SignInPage() {
  const location = useLocation();
  const state = (location.state ?? {}) as SignInState;
  const navigate = useNavigate();
  const queryClient = useQueryClient();
  const currentUser = useCurrentUser();
  const [email, setEmail] = useState('');
  const [password, setPassword] = useState('');
  const [pending, setPending] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const target = state.from && state.from !== '/sign-in' ? state.from : '/';

  // Ktoś już zalogowany nie musi widzieć formularza.
  if (currentUser.isSuccess) return <Navigate to={target} replace />;

  async function submit(event: FormEvent) {
    event.preventDefault();
    setPending(true);
    setError(null);
    try {
      await api.login(email.trim(), password);
      // Nowa sesja — dane z poprzedniej nie mogą zostać w pamięci.
      queryClient.clear();
      navigate(target, { replace: true });
    } catch (e) {
      setError(errorMessage(e));
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
        <h1>Zaloguj się</h1>
        <p className="auth-subtitle">Panel właściciela obiektów serwisowych</p>

        {state.reason && !error && (
          <div className="inline-alert inline-alert-info" role="status">
            <Info size={16} aria-hidden /> {NOTICES[state.reason]}
          </div>
        )}
        {error && (
          <div className="inline-alert inline-alert-error" role="alert">
            <AlertCircle size={16} aria-hidden /> {error}
          </div>
        )}

        <form onSubmit={submit} className="auth-form">
          <label className="field">
            <span className="field-label">E-mail</span>
            <input
              type="email"
              name="username"
              autoComplete="username"
              value={email}
              onChange={(e) => setEmail(e.target.value)}
              required
              autoFocus
            />
          </label>
          <label className="field">
            <span className="field-label">Hasło</span>
            <input
              type="password"
              name="password"
              autoComplete="current-password"
              value={password}
              onChange={(e) => setPassword(e.target.value)}
              required
            />
          </label>
          <button type="submit" className="btn btn-primary btn-block" disabled={pending}>
            {pending && <Loader2 size={14} className="spin" aria-hidden />}
            {pending ? 'Logowanie…' : 'Zaloguj się'}
          </button>
        </form>
      </div>
    </div>
  );
}
