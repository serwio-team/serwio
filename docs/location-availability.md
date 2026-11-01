# Dostępność lokalizacji

Baza PR: `ms/location-availability-service-orders`, uzgodniona przed implementacją. Backend z tej gałęzi udostępnia wymagane GET/PUT i poprawkę normalizacji północy. Nie włączono rejestracji właściciela ani zmian backendu.

Akcja na `/locations` otwiera tygodniowy edytor. Istniejąca ochrona trasy dopuszcza wyłącznie `ASSET_OWNER`. Zapytania korzystają z `api.ts`, obsługi sesji i CSRF oraz klucza TanStack Query zawierającego ID lokalizacji. Zapis zastępuje cały tydzień, a formularz i cache otrzymują odpowiedź serwera. Puste dni są pomijane, więc zamknięcie całego tygodnia wysyła `days: []`. Błędy pól z API są mapowane z indeksów wysłanych dni na dni formularza. Błąd nie usuwa edycji.

Walidacja obejmuje format i zakres godzin, różne końce, limit 100 przedziałów, unikalne dni, nakładanie w jednym dniu, między dniami i na granicy tygodnia. `24:00` jest dostępne w tekstowym polu końca, ponieważ natywne pole `time` nie reprezentuje tej wartości. Godziny nie są przeliczane między strefami.

Fixture'y `serwio-frontend/src/fixtures/availability.ts` obejmują pusty tydzień, wiele okien, noc, pełną dobę, granicę tygodnia i normalizację. Nie są importowane przez kod aplikacji ani używane zamiast API.

## Testy automatyczne

W `serwio-frontend`:

```powershell
npm.cmd test
npm.cmd run build
```

10 testów Node obejmuje reguły harmonogramu, fixture'y, round trip, serializację bez `endsAtMidnight`, GET/PUT, CSRF oraz błędy 400/401/403/404 i sieci. Testy klienta API używają podmienionego `fetch`; nie są testami rzeczywistego backendu. Nie dodano zależności.

## Test przeglądarkowy z backendem

`serwio-frontend/tests/browser/availability.mjs` wymaga osobno dostępnego `playwright-core` i Chrome. Nie jest częścią `npm test`. Używaj wyłącznie izolowanego backendu z tymczasową H2: skrypt zastępuje harmonogram lokalizacji testowej i na końcu go czyści. Uruchom backend z `TEMP_DB=true`, `SEED_DATA=true`, ustalonym `SEED_PASSWORD`, a frontend przez `npm.cmd run dev -- --host 127.0.0.1`.

Przykład w katalogu frontendu, gdy Playwright jest zainstalowany poza projektem:

```powershell
$env:PLAYWRIGHT_MODULE_URL = 'file:///C:/sciezka/node_modules/playwright-core/index.mjs'
$env:SEED_PASSWORD = 'haslo-izolowanego-backendu'
node tests/browser/availability.mjs
```

Opcjonalne zmienne: `CHROME_PATH`, `SERWIO_TEST_URL` (domyślnie `http://127.0.0.1:5173`), `SCREENSHOT_PATH`.

Wykonano i uzyskano wynik pozytywny:

- Rzeczywisty backend: logowanie właściciela, GET, PUT, ponowny odczyt, kilka okien, noc niedziela–poniedziałek, pełna doba, normalizacja długiego okna na granicy tygodnia i ponowny zapis odpowiedzi, usunięcie tygodnia.
- Rzeczywisty backend: 400 dla równych godzin, 404 dla niedostępnego ID przy GET i PUT, 401 bez sesji, 403 przy braku CSRF i odczycie przez firmę; przekierowanie firmy z `/locations`.
- Podmienione odpowiedzi w przeglądarce: ładowanie i 404 przy GET, 400 z `detail` i błędem pola dla wtorku wysłanego jako pierwszy dzień, błąd sieci, 404 przy PUT, zachowanie edycji, blokada podczas zapisu, podstawienie odmiennego harmonogramu i strefy z odpowiedzi.
- UI: brak poziomego przepełnienia przy 320 px, pętla fokusu Tab/Shift+Tab, Escape, powrót fokusu do akcji oraz strefa API przy innej strefie przeglądarki. Brak błędów wykonania JavaScript.

Nie wykonano testu z dwiema rzeczywistymi sesjami różnych właścicieli: seeder bazy udostępnia jednego właściciela. Kontrola kodu backendu potwierdza filtrowanie lokalizacji według ID zalogowanego właściciela przy GET i PUT. Nie jest to równoważne testowi end-to-end dwóch właścicieli. Nie stwierdzono rozbieżności kontraktu z backendem uzgodnionej bazy.
