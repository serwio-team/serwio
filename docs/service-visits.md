# Planowanie wizyty (SCRUM-40)

Kontrakt dla paneli firmy (SCRUM-45), właściciela (SCRUM-46) i serwisanta
(SCRUM-49). Wszystkie ścieżki są względne do `/api/service-orders/{orderId}`.
Żądania wymagają sesji, a zapis także tokenu CSRF.

| Metoda i ścieżka | Kto | Wynik |
| --- | --- | --- |
| `GET /visits` | Firma zlecenia, właściciel lokalizacji, serwisant z bieżącym dostępem | `200` z godzinami lokalizacji i wizytami, od najnowszej |
| `GET /visits/candidates` | Firma zlecenia | `200` z tablicą kandydatów, każdy z `id`, `displayName` i `branchId` |
| `POST /visits` | Firma zlecenia | `201` ze szczegółami utworzonej wizyty |
| `PUT /visits/{visitId}/staffing` | Firma zlecenia | `200` ze szczegółami wizyty po zmianie obsady |
| `POST /visits/{visitId}/proposals` | Koordynator wizyty | `200` ze szczegółami wizyty z nową propozycją |
| `POST /visits/{visitId}/proposals/{proposalId}/counter` | Druga strona uzgodnienia | `200` ze szczegółami wizyty z nową propozycją |
| `POST /visits/{visitId}/proposals/{proposalId}/reject` | Druga strona uzgodnienia | `200` ze szczegółami wizyty po odrzuceniu |
| `POST /visits/{visitId}/proposals/{proposalId}/accept` | Druga strona uzgodnienia | `200` ze szczegółami wizyty z potwierdzonym terminem |
| `POST /visits/{visitId}/cancel` | Firma zlecenia | `200` ze szczegółami anulowanej wizyty |

## Odczyt i obsada

`GET /visits` zwraca strefę IANA, tygodniowe okna w formacie API dostępności oraz
tablicę `visits`. Odczyt zlecenia bez wizyt daje `visits: []`. Operacje zapisu
zwracają pojedynczą wizytę w tym samym formacie co element `visits`.

```json
{
  "timezone": "Europe/Warsaw",
  "availability": [{"dayOfWeek": 2, "intervals": [{"from": "08:00", "to": "16:00"}]}],
  "visits": [{
    "id": "831a08e6-7b59-4b43-b0e3-bc7daecaf701",
    "status": "DRAFT",
    "coordinatorTechnicianId": "67627a93-b4ad-4c39-87db-b41c74e28851",
    "technicians": [{"id": "67627a93-b4ad-4c39-87db-b41c74e28851", "displayName": "Anna Kowalska", "branchId": "1fe19bc2-b3c7-43b9-9aa6-118cb3905dfd"}],
    "currentProposal": {
      "id": "7882188d-ab36-493a-a150-d54ff9ba01fb",
      "proposedBy": "COORDINATOR",
      "startsAt": "2026-12-01T09:00:00+01:00",
      "endsAt": "2026-12-01T10:00:00+01:00"
    },
    "startsAt": null,
    "endsAt": null
  }]
}
```

`POST /visits` i `PUT /visits/{visitId}/staffing` przyjmują tę samą obsadę.

```json
{
  "technicianIds": ["67627a93-b4ad-4c39-87db-b41c74e28851"],
  "coordinatorTechnicianId": "67627a93-b4ad-4c39-87db-b41c74e28851"
}
```

Lista wykonawców jest niepusta i bez powtórzeń. Koordynator jest jednym
z wykonawców. Kandydat ma aktywne konto, profil i oddział oraz aktualny przydział
osobisty lub oddziałowy do tego zlecenia. Zmiana obsady lub koordynatora podczas
uzgadniania wygasza propozycję. Identyczny zapis zachowuje ją w mocy.

## Uzgadnianie terminu

Propozycja i kontrpropozycja przyjmują ten sam przedział. Akceptacja,
odrzucenie i anulowanie nie wymagają treści żądania.

```json
{"startsAt": "2026-12-01T09:00:00+01:00", "endsAt": "2026-12-01T10:00:00+01:00"}
```

Terminy należy wysyłać w ISO 8601 z przesunięciem obowiązującym w strefie
lokalizacji. Przesunięcie rozstrzyga powtórzoną godzinę. Przedział musi mieć
dodatnią długość, zaczynać się w przyszłości i mieścić w jednym zapisanym
oknie. `24:00` jest prawą granicą dnia. Sąsiadujące okna pozostają osobne.
Rezerwacje wszystkich wykonawców porównuje się według chwil UTC. Koniec
jednej wizyty może pokrywać się z początkiem kolejnej.

Koordynator inicjuje uzgodnienie. Autor bieżącej propozycji (`OWNER` albo
`COORDINATOR`) czeka na odpowiedź drugiej strony. Kontrpropozycja zamyka
poprzednią propozycję i staje się bieżącą. Odrzucenie pozostawia `DRAFT`
z `currentProposal: null`. Koordynator może wtedy ponownie zaproponować termin.
Potwierdzenie rezerwuje czas, ustawia `startsAt` i `endsAt`, a bieżąca
propozycja znika z odpowiedzi. Zmiana godzin lokalizacji nie zmienia
wcześniej potwierdzonych chwil wizyty.

| Zdarzenie | Stan wizyty | Status zlecenia |
| --- | --- | --- |
| Utworzenie dla otwartego zlecenia w `ASSIGNED` | `DRAFT` | `AWAITING_SCHEDULING` |
| Akceptacja przez drugą stronę | `CONFIRMED` | `VISIT_CONFIRMED` |
| Anulowanie szkicu lub potwierdzonej wizyty przed początkiem terminu | `CANCELLED` | `ASSIGNED` |

Zlecenie ma najwyżej jedną niezakończoną wizytę. Po potwierdzeniu obsada,
koordynator i termin są zablokowane. Zmiana wymaga anulowania i utworzenia
nowej wizyty z nowym ID. Historia wizyt i propozycji pozostaje zapisana.
Stany `IN_PROGRESS` i `COMPLETED` są przeznaczone dla realizacji w SCRUM-41.

## Błędy

| HTTP | Znaczenie |
| --- | --- |
| `400` | Niepoprawne dane, np. nieprawidłowa obsada lub przesunięcie czasu. `errors` zawiera listy komunikatów według pola. |
| `401` | Brak ważnej sesji |
| `403` | Niedozwolona akcja, np. odpowiedź autora na własną propozycję, lub brak CSRF |
| `404` | Zlecenie albo wizyta niedostępne dla bieżącego konta |
| `409` | Konflikt stanu, propozycji lub terminu. `code` rozróżnia przyczynę. |

Kody `409` to `INVALID_VISIT_STATE`, `STALE_PROPOSAL`,
`OUTSIDE_LOCATION_AVAILABILITY` i `TECHNICIAN_TIME_CONFLICT`.
Po `STALE_PROPOSAL` klient pobiera aktualny stan wizyt.
