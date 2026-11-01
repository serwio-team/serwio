# Rejestracja właściciela

Publiczny `POST /api/auth/register/owner` tworzy w jednej transakcji konto `ASSET_OWNER` ze statusem `ACTIVE` oraz powiązany profil właściciela. Odpowiedź `201` korzysta z istniejącego `UserAccountResponse`; nie zawiera hasła, hasha ani tokenu. Rejestracja nie uwierzytelnia użytkownika i nie wysyła wiadomości aktywacyjnej.

Formularz `/sign-up` jest dostępny z `/sign-in`. Po sukcesie przekierowuje do logowania z komunikatem „Konto zostało utworzone. Możesz się zalogować.”. Istniejący routing po zalogowaniu prowadzi właściciela do `/locations`.

## Walidacja i bezpieczeństwo

- Imię i nazwisko są wymagane, pozbawiane otaczających spacji i ograniczone do 127 znaków każde. Istniejący model ma jedno pole `AssetOwner.name`, dlatego zapisuje ono imię i nazwisko rozdzielone spacją, maksymalnie 255 znaków.
- E-mail jest wymagany, walidowany, ograniczony do 254 znaków, pozbawiany otaczających spacji i zapisywany małymi literami. Wyszukiwanie podczas rejestracji i logowania pomija wielkość liter oraz otaczające spacje, również dla istniejących kont innych ról.
- Hasło zachowuje wszystkie znaki, w tym otaczające spacje. Obowiązuje polityka 8–72 znaków i ograniczenie istniejącego BCrypt do 72 bajtów UTF-8. Potwierdzenie musi być identyczne i nie jest zapisywane.
- Backend ustala rolę, status i powiązanie właściciela; pola uprawnień spoza DTO nie wpływają na konto.
- Błędy walidacji mają status `400` i istniejący format `ProblemDetail.errors`. Walidacja rejestracji przekazuje wyłącznie nazwy pól i komunikaty, aby standardowy wyjątek Spring nie wypisywał odrzuconych haseł w logach DEBUG. DTO ma również bezpieczne `toString()`.
- Zajęty adres, w tym konflikt równoczesnych zapisów, daje `409`. Unikalność zapewnia baza, niezależnie od sprawdzenia w serwisie.
- CSRF pozostaje włączony. Publiczny jest wyłącznie POST rejestracji. Frontend używa `api.ts` i `/api/csrf`; przy `401` lub `403` ponawia publiczne żądanie raz z nowym tokenem. Dotychczasowe reguły chronionych operacji pozostają zachowane.
- Nie dodano zależności ani mechanizmu rate limiting; projekt nie miał takiego mechanizmu.

## Schemat bazy

Projekt używa Hibernate `ddl-auto`, bez narzędzia migracyjnego. Po utworzeniu schematu przygotowywany jest unikalny indeks `ux_user_account_email_normalized`: PostgreSQL indeksuje `lower(trim(email))`, a H2 używa generowanej kolumny `email_normalized` z tym samym wyrażeniem. Indeks obejmuje wszystkie konta i chroni także zapisy pomijające endpoint rejestracji.

Istniejące konta i ich adresy nie są przepisywane. Jeśli trwała baza zawiera już adresy różniące się wyłącznie wielkością liter lub otaczającymi spacjami, utworzenie indeksu przerwie start aplikacji. Takie konflikty wymagają rozstrzygnięcia przez opiekuna danych przed wdrożeniem; konta nie są automatycznie usuwane ani łączone.

## Weryfikacja — 1 listopada 2026

Backend: Java 21, projektowy wrapper Maven, świeża H2 w trybie PostgreSQL, bez seedera. `cd serwio-backend; .\mvnw.cmd test`: **23 testy, 0 błędów, 0 pominiętych**.

Testy obejmują utworzenie konta i profilu, bezpieczny hash bez modyfikowania hasła, brak automatycznego logowania, logowanie istniejącym mechanizmem, wszystkie wymagane pola, niepoprawny e-mail, długości pól i hasła, limit UTF-8, różne hasła, brak hasła w logach, duplikaty bez rozróżniania wielkości liter, bezpośredni konflikt unikalności w bazie, dwie równoczesne rejestracje, rollback po błędzie zapisu profilu, ignorowanie pól uprawnień, publiczny dostęp z prawdziwym tokenem CSRF, odrzucenie żądań bez CSRF i anonimowego dostępu do zasobów, odmowę API pozostałych ról oraz zgodność logowania firmy i serwisanta.

Dwa nowe konta właścicieli sprawdzają listowanie własnych lokalizacji i obiektów, brak cudzych danych na listach, odmowę odczytu, edycji i usunięcia cudzego zasobu oraz odmowę tworzenia obiektu w cudzej lokalizacji.

Frontend: `cd serwio-frontend; npm.cmd run build`: **sukces**. Dodatkowo sprawdzono działającą aplikację w Chrome headless, sterowanym przez Node i Chrome DevTools Protocol, bez dodawania zależności projektu:

- Link do rejestracji, błędy pięciu pustych pól i różne hasła.
- Rejestracja → widoczny komunikat sukcesu → logowanie nowym kontem → `/locations`.
- Przekierowanie właściciela z `/company` i `/technician` do jego panelu.
- Blokada przycisku podczas oczekiwania, konflikt e-maila i zachowanie danych formularza.
- Symulowane odpowiedzi `400`, `429`, `500`, `503` oraz błąd sieci: komunikaty pól lub komunikaty ogólne bez szczegółów serwera.
- Odnowienie wygasłego CSRF po wylogowaniu: `401` → pobranie tokenu → `201`.
- Widok przy szerokościach 320 i 375 px: brak poziomego przewijania, czytelne pola, błędy i przyciski; dodatkowa kontrola zrzutu ekranu.

Scenariusz trwałości po restarcie oraz ścieżka PostgreSQL nie zostały wykonane: lokalny Docker daemon nie działa i trwała baza nie jest dostępna. Testy konkurencji i rollback wykonano na H2. Nie ma kodu tokenów ani wysyłki wiadomości aktywacyjnych w nowej ścieżce rejestracji.

Zmiany przygotowano w `VZ/owner-registration` na podstawie zweryfikowanego `origin/main` (`2188e96`). Nie wykonano merge ani rebase.
