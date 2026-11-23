# Przydział zlecenia do oddziałów i serwisantów (SCRUM-38)

Kontrakt backendu. Dotyczy tylko konta `SERVICE_COMPANY` i zleceń własnej firmy.
Wszystkie żądania wymagają sesji, a `PUT` także tokenu CSRF.

Wpływ przydziałów na widoczność zleceń opisuje [kontrakt API serwisanta](technician-orders.md).

## Endpointy

| Metoda | Ścieżka | Rola |
| --- | --- | --- |
| GET | `/api/service-orders/{id}/assignment` | `SERVICE_COMPANY` |
| PUT | `/api/service-orders/{id}/assignment` | `SERVICE_COMPANY` |

`PUT` zastępuje cały aktualny przydział zlecenia jednym zestawem.

### Żądanie `PUT`

```json
{
  "expectedVersion": 0,
  "branchIds": ["<uuid oddziału>"],
  "technicianIds": ["<uuid serwisanta>"],
  "coordinatorTechnicianId": "<uuid serwisanta>"
}
```

- `expectedVersion` — wersja przydziału odczytana wcześniej przez `GET`.
  Nowe zlecenie ma wersję `0`.
- `branchIds` — całe oddziały. Przydział oddziału nie jest rozwijany do listy
  jego pracowników.
- `technicianIds` — pojedynczy serwisanci, także z innych oddziałów niż wybrane.
- `coordinatorTechnicianId` — wymagany. Musi należeć do oddziału z `branchIds`
  albo być na liście `technicianIds`.
- Co najmniej jedna z list `branchIds`, `technicianIds` musi być niepusta.
- Identyfikatory w listach muszą być unikalne.

### Odpowiedź `GET` i `PUT` (200)

```json
{
  "serviceOrderId": "<uuid>",
  "status": "ASSIGNED",
  "version": 1,
  "branches": [{ "id": "<uuid>", "name": "Oddział Warszawa" }],
  "technicians": [{ "id": "<uuid>", "displayName": "Jan Kowalski", "branchId": "<uuid>" }],
  "coordinatorTechnicianId": "<uuid>"
}
```

Dla zlecenia bez przydziału: `version: 0`, puste listy, `coordinatorTechnicianId: null`,
`status: "NEW"`.

## Reguły

- Zapis jest atomowy. Błąd walidacji nie zmienia niczego.
- Przy pierwszym poprawnym zapisie status zmienia się z `NEW` na `ASSIGNED`
  i dopisuje się jeden wpis historii statusów.
- Zapis zestawu identycznego z aktualnym (listy i koordynator) nie zmienia
  nic: żadnych nowych przydziałów, wpisów historii ani wersji. Odpowiedź to 200
  z aktualnym stanem.
- Realna zmiana zestawu lub koordynatora zwiększa `version` o 1. Zdjęte
  przydziały dostają `unassigned_at`, nie są usuwane. Dodane są nowymi wierszami.
- Zmiana zestawu przy statusie `ASSIGNED` nie dopisuje wpisu historii statusów.
- Przydział można edytować tylko w statusach `NEW` i `ASSIGNED`. Blokadę po
  potwierdzeniu wizyty doda SCRUM-40.
- Zdjęcie wszystkich odbiorców nie jest obsługiwane.
- Każdy zapis sprawdza cały zestaw na nowo. Jeśli oddział lub serwisant z
  aktualnego przydziału został w międzyczasie dezaktywowany, trzeba go usunąć z
  zestawu, inaczej zapis zwróci 400.
- Zablokowanie zlecenia na czas zapisu (`SELECT … FOR UPDATE`) i porównanie
  `expectedVersion` sprawiają, że z dwóch równoczesnych zapisów tej samej wersji
  jeden się powiedzie, a drugi dostanie 409.

## Sprawdzenie

- `./mvnw test`: `OrderAssignmentTest` (11 testów, H2) obejmuje reguły
  z tego dokumentu, role i równoczesny zapis.
- Próba ręczna na PostgreSQL 16 (2026-11-23): pusty schemat i schemat sprzed
  zmiany. Kolumna `assignment_version` dodaje się z wartością 0 dla istniejących
  zleceń.

## Błędy

Format `ProblemDetail`. Błędy pól są w `errors` (jak w pozostałych endpointach).

| Kod | Kiedy |
| --- | --- |
| 400 | Pusty przydział, brak koordynatora, duplikaty ID, oddział lub serwisant nieaktywny albo spoza firmy zlecenia, konto serwisanta nieaktywne, koordynator spoza przydziału |
| 401 | Brak sesji |
| 403 | Rola inna niż `SERVICE_COMPANY` (właściciel, serwisant), brak CSRF |
| 404 | Zlecenie nie istnieje albo należy do innej firmy |
| 409 | `expectedVersion` nieaktualna (ktoś zmienił przydział) albo status zlecenia inny niż `NEW`/`ASSIGNED` |

Pola `errors` w odpowiedzi 400: `branchIds`, `technicianIds`, `coordinatorTechnicianId`.
