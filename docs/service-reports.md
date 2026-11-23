# Podstawowy raport serwisowy (SCRUM-42)

Zlecenie ma jeden raport obejmujący wszystkie wizyty i jednego autora. Poniższe
ścieżki są względne do `/api/service-orders/{orderId}`. Operacje zapisu wymagają
sesji i tokenu CSRF.

| Metoda i ścieżka | Kto | Odpowiedź |
| --- | --- | --- |
| `GET /report` | Autor odczytuje szkic. Finalny raport odczytują firma zlecenia, właściciel obiektu i uprawniony serwisant. | `200` z raportem |
| `PUT /report` | Autor z bieżącym dostępem do otwartego zlecenia | `200` z zapisanym szkicem |
| `POST /report/finalize` | Autor z bieżącym dostępem po wykonaniu zlecenia | `200` ze sfinalizowanym raportem |
| `PUT /report/author` | Firma zlecenia, przed finalizacją | `204` |

Domyślnym autorem nowego szkicu jest koordynator zlecenia. Kolejny zapis zastępuje
tekst tego samego szkicu. Puste pola można zostawić jako `null`. Każde pole mieści
do 100 000 znaków. `resolution` zawiera też wynik przeglądu. `usedMaterials`
i `recommendations` są opcjonalne.

```json
{
  "actionsDone": "Oczyszczono filtr i sprawdzono przepływ powietrza.",
  "diagnosis": "Zabrudzenie filtra ograniczało przepływ.",
  "resolution": "Po czyszczeniu przepływ wrócił do normy.",
  "usedMaterials": null,
  "recommendations": null
}
```

Odpowiedź `GET`, zapis szkicu i finalizacja zwracają ten sam format.

```json
{
  "id": "403204a1-a746-4c4c-a69f-f5cd85737d9c",
  "serviceOrderId": "a31560f4-d372-4f04-981b-1ad8d4a1d5a1",
  "authorTechnicianId": "67627a93-b4ad-4c39-87db-b41c74e28851",
  "actionsDone": "Oczyszczono filtr i sprawdzono przepływ powietrza.",
  "diagnosis": "Zabrudzenie filtra ograniczało przepływ.",
  "resolution": "Po czyszczeniu przepływ wrócił do normy.",
  "usedMaterials": null,
  "recommendations": null,
  "createdAt": "2026-11-24T08:00:00Z",
  "updatedAt": "2026-11-24T08:20:00Z",
  "finalizedAt": null
}
```

Firma może wskazać autora spośród serwisantów z aktualnym dostępem do zlecenia,
którzy byli w obsadzie zakończonej wizyty. Autorstwo zapisuje się w historii.
Przy zmianie autora treść istniejącego szkicu jest zachowana. Dla zlecenia bez
raportu powstaje pusty szkic z wybranym autorem.

```json
{"technicianId": "67627a93-b4ad-4c39-87db-b41c74e28851"}
```

Finalizacja nie wymaga treści żądania. Dotyczy ostatniego zapisanego szkicu.
Zlecenie musi mieć status `COMPLETED`, autor musi być w obsadzie przynajmniej
jednej jego wizyty w stanie `COMPLETED`, a `actionsDone`, `diagnosis` i
`resolution` muszą być uzupełnione. Wizyta zakończona wynikiem
`REVISIT_REQUIRED` także stanowi podstawę uczestnictwa. Po zapisaniu
`finalizedAt` raport jest dostępny tylko do odczytu. Szczegóły zlecenia firmy
i właściciela zwracają `reportFinalized: true`, a firma może zamknąć zlecenie.
Po zamknięciu odczyt serwisanta korzysta z historycznych uprawnień do zlecenia.
[Kontrakt realizacji i zamknięcia](service-visits.md)

| HTTP | Znaczenie |
| --- | --- |
| `400` | Nieprawidłowe dane lub brak wymaganych pól przy finalizacji. `errors` przypisuje komunikaty do pól. |
| `401` | Brak ważnej sesji |
| `403` | Brak uprawnienia do danej akcji lub tokenu CSRF |
| `404` | Zlecenie lub raport niedostępny dla konta |
| `409` | Konflikt wskazany przez `code`, np. `INVALID_ORDER_STATE`, `REPORT_ALREADY_FINALIZED`, `REPORT_AUTHOR_NOT_ASSIGNED` lub `REPORT_AUTHOR_NOT_PARTICIPANT`. |
