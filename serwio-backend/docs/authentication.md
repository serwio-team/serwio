# Logowanie i tworzenie danych właściciela

## Konto testowe

Włącz `SEED_DATA=true`. `SeedConfig` utworzy aktywne konto
`owner@example.com` z rolą `ASSET_OWNER`, profil właściciela oraz
lokalizację testową z trzema urządzeniami: klimatyzatorem, pompą ciepła
i centralą wentylacyjną. Kolejne uruchomienia nie duplikują urządzeń ani
nie nadpisują ich zmian lub oznaczenia jako usunięte.

Hasło ustaw przez `SEED_PASSWORD`. Jeśli go nie ustawisz, seed wygeneruje
losowe hasło i wypisze je w logach przy inicjalizacji konta. Baza przechowuje
wyłącznie hash hasła.

Przykładowy start z tymczasową bazą H2:

```sh
TEMP_DB=true SEED_DATA=true SEED_PASSWORD='wstaw-wlasne-haslo' ./mvnw spring-boot:run
```

Kolejny start nie zmienia hasła ani nie duplikuje danych. Stare konto seeda
z hasłem `!seed-account-without-login` zostaje aktywowane z nowym hasłem;
identyfikatory właściciela i lokalizacji zostają zachowane. Zmiana
`SEED_PASSWORD` nie resetuje hasła wcześniej aktywowanego konta.

## Logowanie

Otwórz `/login`: jest to formularz generowany przez Spring Security.
W polu użytkownika wpisz `owner@example.com`. Po poprawnym logowaniu następuje
przekierowanie na `GET /api`, który zwraca surowy JSON z komunikatem
`Zalogowano pomyślnie` i listą dostępnych endpointów w polu `endpoints`.

Logowanie używa sesji i ciasteczka `JSESSIONID`. Rejestracja nie jest dostępna.

Przepływ dla Postmana lub frontendu:

1. `GET /api/csrf` — zachowaj ciasteczko sesji i odczytaj `token` oraz `headerName`.
2. `POST /login` — formularz `application/x-www-form-urlencoded` z polami
   `username` i `password`, ciasteczkiem oraz nagłówkiem CSRF wskazanym w kroku 1.
3. Po zalogowaniu ponownie wywołaj `GET /api/csrf`, ponieważ logowanie resetuje
   token. Zachowaj aktualne ciasteczko sesji.
4. W żądaniach `POST`, `PUT` i `DELETE` wysyłaj token w nagłówku `headerName`
   (domyślnie `X-CSRF-TOKEN`) i ciasteczko sesji. JSON wysyłaj z
   `Content-Type: application/json`.
5. Wylogowanie: `POST /logout`, również z tokenem CSRF i ciasteczkiem sesji.

## Tworzenie lokalizacji

`POST /api/locations`

```json
{
  "name": "Biuro",
  "addressLine": "Testowa 1",
  "city": "Warszawa",
  "postalCode": "00-001",
  "country": "PL",
  "timezone": "Europe/Warsaw"
}
```

Właściciel jest ustalany na podstawie zalogowanego użytkownika.
Pole `assetOwnerId` zostało usunięte z żądań tworzenia i edycji oraz
z parametrów listowania lokalizacji.

## Tworzenie urządzenia

`POST /api/service-objects`

```json
{
  "locationId": "UUID-lokalizacji-z-poprzedniej-odpowiedzi",
  "name": "Klimatyzator",
  "type": "AC"
}
```

Można wskazać wyłącznie własną lokalizację. Listowanie, odczyt, edycja
i usuwanie lokalizacji oraz urządzeń również ograniczają dane do zalogowanego
właściciela. Listy zwracają bezpośrednio tablicę JSON, bez opakowania `items`
i bez pól `page`, `size`, `totalElements`, `totalPages` w odpowiedzi.
Parametry żądania `page` (domyślnie 0) i `size` (domyślnie 20, maksymalnie 100)
nadal pozwalają pobierać kolejne strony.

Brak sesji dla API zwraca `401` (żądania zapisu muszą też przejść kontrolę CSRF).
Brak wymaganej roli, profilu właściciela przy tworzeniu lokalizacji lub
prawidłowego tokena CSRF zwraca `403`. Cudzy lub nieistniejący zasób zwraca `404`.
Filtrowanie listy urządzeń po cudzej lokalizacji zwraca pustą listę.

Dokumentacja mechanizmów: [formularz logowania Spring Security](https://docs.spring.io/spring-security/reference/servlet/authentication/passwords/form.html),
[ochrona CSRF](https://docs.spring.io/spring-security/reference/servlet/exploits/csrf.html).
