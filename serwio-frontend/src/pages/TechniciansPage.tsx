import { Building2, Plus } from 'lucide-react';
import { useEffect, useState } from 'react';
import { useLocation as useRouterLocation, useNavigate, useSearchParams } from 'react-router-dom';
import { PageHeader } from '../components/DataState';
import { TechnicianCreateModal } from '../components/TechnicianForm';
import { TechnicianTable } from '../components/TechnicianTable';
import { useAllBranches } from '../queries';

export function TechniciansPage() {
  const routerLocation = useRouterLocation();
  const navigate = useNavigate();
  const [searchParams, setSearchParams] = useSearchParams();
  const branchId = searchParams.get('branchId') ?? undefined;
  const branches = useAllBranches();
  const [creating, setCreating] = useState(Boolean((routerLocation.state as { create?: boolean } | null)?.create));

  useEffect(() => {
    if (routerLocation.state) {
      navigate(routerLocation.pathname + routerLocation.search, { replace: true, state: null });
    }
  }, [routerLocation, navigate]);

  useEffect(() => {
    if (branchId && branches.data && !branches.data.some((b) => b.id === branchId)) {
      setSearchParams({}, { replace: true });
    }
  }, [branchId, branches.data, setSearchParams]);

  const setFilter = (id: string) => setSearchParams(id ? { branchId: id } : {}, { replace: true });
  const addButton = (
    <button type="button" className="btn btn-primary" onClick={() => setCreating(true)}>
      <Plus size={14} aria-hidden /> Dodaj serwisanta
    </button>
  );

  return (
    <>
      <PageHeader title="Serwisanci" description="Konta serwisantów Twojej firmy." action={addButton} />

      <section className="card">
        <div className="card-toolbar">
          <label className="filter">
            <Building2 size={14} aria-hidden className="muted" />
            <span className="sr-only">Oddział</span>
            <select value={branchId ?? ''} onChange={(e) => setFilter(e.target.value)} disabled={branches.isPending}>
              <option value="">Wszystkie oddziały</option>
              {branches.data?.map((b) => (
                <option key={b.id} value={b.id}>{b.active ? b.name : `${b.name} (nieaktywny)`}</option>
              ))}
            </select>
          </label>
          {branches.isError && <span className="field-error">Nie udało się pobrać oddziałów do filtra.</span>}
        </div>
        <TechnicianTable
          branchId={branchId}
          emptyTitle={branchId ? 'Brak serwisantów w tym oddziale' : 'Brak serwisantów'}
          emptyDescription={branchId
            ? 'Wybierz inny oddział albo dodaj tu serwisanta.'
            : 'Dodaj pierwszego serwisanta i przekaż mu hasło startowe.'}
          emptyAction={addButton}
        />
      </section>

      {creating && <TechnicianCreateModal defaultBranchId={branchId} onClose={() => setCreating(false)} />}
    </>
  );
}
