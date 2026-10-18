import { useQueryClient } from '@tanstack/react-query';
import { Loader2, ServerCrash, ShieldAlert } from 'lucide-react';
import { useEffect, useRef, type ReactNode } from 'react';
import { Navigate, Outlet, Route, Routes, useLocation, useNavigate } from 'react-router-dom';
import { ApiError, UNAUTHORIZED_EVENT, api, errorMessage } from './api';
import { AppLayout } from './components/AppLayout';
import { LocationsPage } from './pages/LocationsPage';
import { ServiceObjectsPage } from './pages/ServiceObjectsPage';
import { SignInPage } from './pages/SignInPage';
import { queryKeys, useCurrentUser } from './queries';

export function App() {
  return (
    <>
      <SessionWatcher />
      <Routes>
        <Route path="/sign-in" element={<SignInPage />} />
        <Route element={<RequireOwner />}>
          <Route index element={<Navigate to="/locations" replace />} />
          <Route path="/locations" element={<LocationsPage />} />
          <Route path="/service-objects" element={<ServiceObjectsPage />} />
        </Route>
        <Route path="*" element={<Navigate to="/" replace />} />
      </Routes>
    </>
  );
}

// Strony panelu wymagają zalogowanego właściciela (rola ASSET_OWNER).
function RequireOwner() {
  const currentUser = useCurrentUser();
  const location = useLocation();

  if (currentUser.isPending) {
    return (
      <div className="fullpage">
        <Loader2 size={20} className="spin muted" aria-label="Sprawdzanie sesji" />
      </div>
    );
  }
  if (currentUser.isError) {
    if (currentUser.error instanceof ApiError && currentUser.error.status === 401) {
      return <Navigate to="/sign-in" replace state={{ from: location.pathname + location.search }} />;
    }
    return (
      <FullPageMessage icon={<ServerCrash size={20} />} title="Brak połączenia z serwerem">
        <p>{errorMessage(currentUser.error)}</p>
        <p className="muted">Upewnij się, że backend działa na <code>localhost:8080</code>.</p>
        <button type="button" className="btn btn-primary" onClick={() => void currentUser.refetch()}>
          Spróbuj ponownie
        </button>
      </FullPageMessage>
    );
  }
  if (currentUser.data.role !== 'ASSET_OWNER') {
    return (
      <FullPageMessage icon={<ShieldAlert size={20} />} title="Brak dostępu">
        <p>Konto {currentUser.data.email} nie jest kontem właściciela obiektów.</p>
        <LogoutButton />
      </FullPageMessage>
    );
  }
  return (
    <AppLayout user={currentUser.data}>
      <Outlet />
    </AppLayout>
  );
}

// Po 401 w trakcie pracy (wygasła sesja) przenosi na logowanie i czyści dane poprzedniej sesji.
// Przy pierwszym wejściu bez sesji przekierowuje RequireOwner, więc tu nic nie robimy.
function SessionWatcher() {
  const queryClient = useQueryClient();
  const navigate = useNavigate();
  const location = useLocation();
  const current = useRef(location);
  current.current = location;

  useEffect(() => {
    const onUnauthorized = () => {
      if (!queryClient.getQueryData(queryKeys.currentUser)) return;
      const { pathname, search } = current.current;
      queryClient.clear();
      navigate('/sign-in', { replace: true, state: { reason: 'expired', from: pathname + search } });
    };
    window.addEventListener(UNAUTHORIZED_EVENT, onUnauthorized);
    return () => window.removeEventListener(UNAUTHORIZED_EVENT, onUnauthorized);
  }, [queryClient, navigate]);

  return null;
}

function LogoutButton() {
  const queryClient = useQueryClient();
  const navigate = useNavigate();
  return (
    <button
      type="button"
      className="btn btn-secondary"
      onClick={async () => {
        await api.logout().catch(() => undefined);
        queryClient.clear();
        navigate('/sign-in', { replace: true, state: { reason: 'signed-out' } });
      }}
    >
      Wyloguj się
    </button>
  );
}

function FullPageMessage({ icon, title, children }: { icon: ReactNode; title: string; children: ReactNode }) {
  return (
    <div className="fullpage">
      <div className="fullpage-card">
        <span className="empty-icon" aria-hidden>{icon}</span>
        <h1>{title}</h1>
        {children}
      </div>
    </div>
  );
}
