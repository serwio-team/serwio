import { Building2, Info, Loader2, MapPin, UserRound } from 'lucide-react';
import { ApiError, errorMessage } from '../api';
import { EmptyState, ErrorState, PageHeader } from '../components/DataState';
import { PasswordChangeForm } from '../components/PasswordChangeForm';
import { useCurrentTechnician, useCurrentUser } from '../queries';

export function TechnicianDashboardPage() {
  const currentUser = useCurrentUser();
  const profile = useCurrentTechnician();
  const missingProfile = profile.error instanceof ApiError && profile.error.status === 404;
  const account = currentUser.data;

  return (
    <>
      <PageHeader title="Mój panel" description="Twoje dane, firma serwisowa i bezpieczeństwo konta." />

      {account?.passwordChangeRequired && (
        <div className="inline-alert inline-alert-info technician-password-notice" role="status">
          <Info size={18} aria-hidden />
          <div>
            <strong>Korzystasz z hasła startowego</strong>
            <p>Ustaw własne hasło, aby zabezpieczyć swoje konto.</p>
          </div>
          <a className="btn btn-secondary" href="#currentPassword">Zmień hasło</a>
        </div>
      )}

      <div className="technician-dashboard">
        <div className="technician-profile">
          <section className="card technician-card" aria-labelledby="technician-account-title">
            <h2 id="technician-account-title"><UserRound size={18} aria-hidden /> Dane serwisanta</h2>
            <dl className="technician-details">
              {profile.isSuccess && (
                <div><dt>Imię i nazwisko</dt><dd>{profile.data.displayName}</dd></div>
              )}
              <div><dt>E-mail</dt><dd>{account?.email}</dd></div>
              {account && (
                <div>
                  <dt>Konto od</dt>
                  <dd><time dateTime={account.createdAt}>{new Date(account.createdAt).toLocaleDateString('pl-PL', {
                    day: 'numeric', month: 'long', year: 'numeric',
                  })}</time></dd>
                </div>
              )}
            </dl>
          </section>

          <section className="card technician-card" aria-labelledby="technician-company-title">
            <h2 id="technician-company-title"><Building2 size={18} aria-hidden /> Firma i oddział</h2>
            {profile.isPending ? (
              <div className="technician-loading" role="status">
                <Loader2 size={18} className="spin" aria-hidden /> Ładowanie danych serwisanta…
              </div>
            ) : missingProfile ? (
              <EmptyState icon={MapPin} title="Brak przypisanego oddziału"
                description="Twoje konto nie ma profilu serwisanta. Skontaktuj się ze swoją firmą serwisową, aby uzupełnić przypisanie." />
            ) : profile.isError ? (
              <ErrorState message={errorMessage(profile.error)} onRetry={() => void profile.refetch()} />
            ) : (
              <dl className="technician-details">
                <div><dt>Firma serwisowa</dt><dd>{profile.data.companyName}</dd></div>
                <div><dt>Oddział</dt><dd>{profile.data.branchName}</dd></div>
              </dl>
            )}
          </section>
        </div>

        <section className="card technician-card" aria-labelledby="technician-password-title">
          <h2 id="technician-password-title">Zmiana hasła</h2>
          <p className="muted technician-card-description">Nowe hasło musi mieć od 8 do 72 znaków.</p>
          <PasswordChangeForm />
        </section>
      </div>
    </>
  );
}
