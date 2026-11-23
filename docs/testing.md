# Testy Serwio

## Backend

Z katalogu `serwio-backend` uruchom `./mvnw clean verify` na JDK 21. Raport JaCoCo
w formatach HTML i XML jest w `target/site/jacoco`. Testy jednostkowe obejmują
reguły domenowe i walidatory. Testy API z MockMvc sprawdzają uprawnienia, zapis,
filtry i współbieżność. Scenariusze `ServiceOrderFlowTest` uruchamiają backend
na losowym porcie lokalnym i używają trzech sesji HTTP z rzeczywistym CSRF.

Oba scenariusze A–Z zaczynają się od rejestracji nowego właściciela. Obejmują
utworzenie obiektu, przydział, potwierdzenie terminu, wykonanie wizyty, finalizację
raportu oraz zamknięcie z odczytem historii. Przypadek przeglądu wymaga drugiej wizyty.

Domyślnie testy korzystają z H2. Żeby uruchomić scenariusze A–Z na PostgreSQL 17,
wskaż pustą, odseparowaną bazę testową i uruchom polecenie poniżej. Aplikacja
tworzy schemat i dane startowe w tej bazie.

```sh
SERWIO_E2E_H2=false \
SERWIO_E2E_DB_URL=jdbc:postgresql://127.0.0.1:5432/serwio_e2e \
SERWIO_E2E_DB_USER=serwio_e2e \
SERWIO_E2E_DB_PASSWORD=serwio_e2e \
./mvnw -Dtest=ServiceOrderFlowTest test
```

## Frontend i CI

Z katalogu `serwio-frontend` uruchom `npm ci`, `npm test` i `npm run build` na
Node.js 24. Ten sam zestaw uruchamia GitHub Actions przy zmianach w PR-ach,
po wypchnięciu zmian na `main` i na żądanie. CI uruchamia także pełną regresję
backendu i zapisuje raport `backend-coverage`. Osobne zadanie uruchamia test A–Z
na izolowanym PostgreSQL 17.
