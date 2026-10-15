# Serwio MVP - wyjaśnienie nazw tabel ERD

Krótkie wyjaśnienie wszystkich 21 tabel ERD na podstawie specyfikacji Serwio MVP.

## 1. Użytkownicy i struktura organizacyjna

| Nazwa w ERD | Polska nazwa i znaczenie |
|---|---|
| `USER_ACCOUNT` | **Konto użytkownika** - dane logowania, rola i status konta. |
| `ASSET_OWNER` | **Właściciel obiektu serwisowego** - podmiot posiadający lub użytkujący obiekty wymagające serwisu. |
| `SERVICE_COMPANY` | **Firma serwisowa** - firma realizująca zlecenia, zarządzająca oddziałami i serwisantami. |
| `SERVICE_BRANCH` | **Oddział firmy serwisowej** - jednostka organizacyjna grupująca serwisantów. |
| `TECHNICIAN` | **Serwisant** - osoba wykonująca przeglądy i naprawy, przypisana do jednego oddziału. |
| `TECHNICIAN_BRANCH_HISTORY` | **Historia przynależności serwisanta do oddziałów** - zapisuje, w jakich oddziałach pracował serwisant i w jakich okresach. |

## 2. Lokalizacje i obiekty

| Nazwa w ERD | Polska nazwa i znaczenie |
|---|---|
| `LOCATION` | **Lokalizacja** - miejsce należące do właściciela, np. restauracja, sklep lub zakład produkcyjny. |
| `LOCATION_AVAILABILITY` | **Dostępność lokalizacji** - dni tygodnia i godziny, w których można realizować wizyty. |
| `SERVICE_OBJECT` | **Obiekt serwisowy** - konkretna maszyna, urządzenie lub instalacja wymagająca serwisowania. |
| `COMPANY_OBJECT_SERVICE` | **Upoważnienie firmy do obsługi obiektu** - określa, która firma może serwisować dany obiekt i w jakim okresie. |

## 3. Zlecenia i przydziały

| Nazwa w ERD | Polska nazwa i znaczenie |
|---|---|
| `SERVICE_ORDER` | **Zlecenie serwisowe** - formalne zlecenie naprawy lub przeglądu konkretnego obiektu. |
| `ORDER_BRANCH_ASSIGNMENT` | **Przydział zlecenia do oddziału** - udostępnia zlecenie wszystkim serwisantom wybranego oddziału. |
| `ORDER_TECHNICIAN_ASSIGNMENT` | **Przydział zlecenia do serwisanta** - udostępnia zlecenie konkretnemu serwisantowi. |
| `ORDER_STATUS_HISTORY` | **Historia statusów zlecenia** - zapisuje kolejne statusy, osoby dokonujące zmian i czas ich wykonania. |

## 4. Wizyty serwisowe

| Nazwa w ERD | Polska nazwa i znaczenie |
|---|---|
| `SERVICE_VISIT` | **Wizyta serwisowa** - konkretny przyjazd serwisantów w ustalonym terminie. Jedno zlecenie może obejmować wiele wizyt. |
| `VISIT_TECHNICIAN` | **Wykonawcy wizyty** - określa serwisantów faktycznie uczestniczących w danej wizycie. |
| `VISIT_PROPOSAL` | **Propozycja terminu wizyty** - zapisuje proponowane terminy, akceptacje, odrzucenia i kontrpropozycje. |

## 5. Raporty i pliki

| Nazwa w ERD | Polska nazwa i znaczenie |
|---|---|
| `SERVICE_REPORT` | **Raport serwisowy** - zbiorcze podsumowanie wykonanych prac, diagnozy, rozwiązania i zaleceń dla zlecenia. |
| `STORED_FILE` | **Przechowywany plik** - informacje o przesłanym zdjęciu lub dokumencie. |
| `SERVICE_OBJECT_FILE` | **Plik obiektu serwisowego** - powiązanie zdjęć i dokumentacji z obiektem. |
| `SERVICE_REPORT_FILE` | **Załącznik raportu serwisowego** - powiązanie zdjęć i dokumentów z raportem. |

