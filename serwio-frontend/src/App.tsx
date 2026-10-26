import { useQueryClient } from '@tanstack/react-query';
import { Loader2, SearchX, ServerCrash, ShieldAlert } from 'lucide-react';
import { useEffect, useRef, type ReactNode } from 'react';
import { Link, Navigate, Outlet, Route, Routes, useLocation, useNavigate } from 'react-router-dom';
import { ApiError, UNAUTHORIZED_EVENT, api, errorMessage } from './api';
import { AppLayout } from './components/AppLayout';
import { BranchDetailsPage } from './pages/BranchDetailsPage';
import { BranchesPage } from './pages/BranchesPage';
import { CompanyDashboardPage } from './pages/CompanyDashboardPage';
import { LocationsPage } from './pages/LocationsPage';
import { ServiceObjectsPage } from './pages/ServiceObjectsPage';
import { SignInPage } from './pages/SignInPage';
import { TechnicianDashboardPage } from './pages/TechnicianDashboardPage';
import { TechniciansPage } from './pages/TechniciansPage';
import { queryKeys, useCurrentUser } from './queries';

const HOME_BY_ROLE: Record<string, string> = {
  ASSET_OWNER: '/locations',
  SERVICE_COMPANY: '/company',
  TECHNICIAN: '/technician',
};

export function App() {
  return (
    <>
      <SessionWatcher />
      <Routes>
        <Route path="/sign-in" element={<SignInPage />} />
        <Route index element={<RequireRole />} />
        <Route element={<RequireRole role="ASSET_OWNER" />}>
          <Route path="/locations" element={<LocationsPage />} />
          <Route path="/service-objects" element={<ServiceObjectsPage />} />
        </Route>
        <Route element={<RequireRole role="TECHNICIAN" />}>
          <Route path="/technician" element={<TechnicianDashboardPage />} />
        </Route>
        <Route element={<RequireRole role="SERVICE_COMPANY" />}>
          <Route path="/company" element={<CompanyDashboardPage />} />
          <Route path="/company/branches" element={<BranchesPage />} />
          <Route path="/company/branches/:id" element={<BranchDetailsPage />} />
          <Route path="/company/technicians" element={<TechniciansPage />} />
        </Route>
        <Route path="*" element={<NotFound />} />
      </Routes>
    </>
  );
}

function RequireRole({ role }: { role?: string }) {
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
  const home = HOME_BY_ROLE[currentUser.data.role];
  if (!home) {
    return (
      <FullPageMessage icon={<ShieldAlert size={20} />} title="Brak dostępu">
        <p>Konto {currentUser.data.email} nie ma dostępu do panelu.</p>
        <LogoutButton />
      </FullPageMessage>
    );
  }
  if (currentUser.data.role !== role) {
    return <Navigate to={home} replace />;
  }
  return (
    <AppLayout user={currentUser.data}>
      <Outlet />
    </AppLayout>
  );
}

function NotFound() {
  return (
    <FullPageMessage icon={<SearchX size={20} />} title="Nie znaleziono strony">
      <p>Ta strona nie istnieje.</p>
      <Link to="/" className="btn btn-primary">Przejdź do panelu</Link>
    </FullPageMessage>
  );
}

// Po 401 w trakcie pracy (wygasła sesja) przenosi na logowanie i czyści dane poprzedniej sesji.
// Przy pierwszym wejściu bez sesji przekierowuje RequireRole, więc tu nic nie robimy.
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
