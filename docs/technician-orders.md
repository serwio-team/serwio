# Zlecenia serwisanta (SCRUM-39)

Zalogowany użytkownik z rolą `TECHNICIAN` odczytuje otwarte zlecenia swojej
firmy, które mają aktualny przydział na niego lub na cały jego bieżący oddział.
Oba endpointy sprawdzają bieżący stan konta, profilu, oddziału i przydziałów.
Przydział indywidualny innej osoby z tego samego oddziału nie daje dostępu.
Po przeniesieniu do innego oddziału w tej samej firmie własny aktywny przydział
indywidualny nadal działa. Lista i szczegóły odzwierciedlają aktualny stan
przy następnym żądaniu, także w tej samej sesji.

## Lista zleceń

`GET /api/service-orders?status=ASSIGNED&page=0&size=20`

| Parametr | Wartość domyślna | Znaczenie |
| --- | --- | --- |
| `status` | Brak filtra | `NEW`, `ASSIGNED`, `AWAITING_TECHNICIAN_REVIEW` lub `AWAITING_OWNER_CONFIRMATION` |
| `page` | `0` | Numer strony od zera |
| `size` | `20` | Rozmiar strony od 1 do 100 |

Odpowiedź 200 zawiera tablicę. Zlecenie z przydziałem indywidualnym i oddziałowym
występuje w niej raz. Filtrowanie odbywa się przed podziałem na strony.
Kolejność to `createdAt` malejąco, a przy remisie `id` rosnąco.
Pusta strona zwraca `[]`.

```json
[
  {
    "id": "a31560f4-d372-4f04-981b-1ad8d4a1d5a1",
    "serviceObjectId": "684fbf1f-8efb-42f4-8c7f-78cb044b2837",
    "serviceObjectName": "Klimatyzator",
    "locationId": "130cae98-5857-4c56-ba87-90c0276feda4",
    "locationName": "Biuro Warszawa",
    "orderType": "REPAIR",
    "currentStatus": "ASSIGNED",
    "createdAt": "2026-11-23T08:15:00Z"
  }
]
```

## Szczegóły zlecenia

`GET /api/service-orders/{id}`

Odpowiedź 200 zawiera dane miejsca pracy, opis, status i koordynatora.
Identyfikator `coordinator.id` to identyfikator profilu serwisanta, zgodny
z `coordinatorTechnicianId` z API przydziałów. Jeżeli w starszym zleceniu
brakuje koordynatora, pole `coordinator` przyjmuje `null`.

```json
{
  "id": "a31560f4-d372-4f04-981b-1ad8d4a1d5a1",
  "serviceObjectId": "684fbf1f-8efb-42f4-8c7f-78cb044b2837",
  "serviceObjectName": "Klimatyzator",
  "locationId": "130cae98-5857-4c56-ba87-90c0276feda4",
  "locationName": "Biuro Warszawa",
  "addressLine": "Testowa 1",
  "city": "Warszawa",
  "timezone": "Europe/Warsaw",
  "orderType": "REPAIR",
  "description": "Klimatyzator nie chłodzi.",
  "currentStatus": "ASSIGNED",
  "createdAt": "2026-11-23T08:15:00Z",
  "coordinator": {
    "id": "966a3a74-53e5-4dd8-8216-342ae2992372",
    "displayName": "Anna Kowalska"
  }
}
```

## Błędy

| Kod | Warunek |
| --- | --- |
| 400 | Nieprawidłowy UUID, status lub parametr stronicowania |
| 401 | Brak ważnej sesji, także po zablokowaniu konta |
| 403 | Rola bez prawa do operacji, np. serwisant wywołujący `POST /api/service-orders` albo `GET` lub `PUT /api/service-orders/{id}/assignment` |
| 404 | Zlecenie nie istnieje lub jest niedostępne dla konta |
