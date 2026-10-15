# Serwio - platforma zarządzania zleceniami i oddziałami serwisowymi

## 1. Cel i etapy projektu

Uniwersalna platforma do serwisu fizycznych obiektów: urządzeń, maszyn, instalacji i sprzętu wymagającego przeglądów lub napraw. Model domenowy nie zakłada jednej branży i ma pozwalać na dostosowanie do różnych działalności serwisowych.

Etapy rozwoju:

1. **MVP** - aplikacja webowa dla właściciela obiektu serwisowego, firmy serwisowej i serwisanta, z obowiązkową strukturą oddziałów.
2. **Stan docelowy** - klient końcowy i dedykowana aplikacja mobilna serwisanta.
3. **SaaS** - wiele niezależnych firm na jednej platformie, z pełną izolacją danych.

## 2. Pojęcia i relacje

Należy konsekwentnie używać poniższych nazw. Ogólne określenia „klient”, „firma”, „produkt” i „urządzenie” prowadziły wcześniej do niejasności.

- **Właściciel obiektu serwisowego** - podmiot posiadający lub użytkujący serwisowany obiekt, np. firma, lokal usługowy, sklep, restauracja, hotel, zakład produkcyjny lub inna organizacja. Rejestruje obiekty, inicjuje zlecenia, ustala dostępność lokalizacji, zatwierdza terminy i śledzi realizację. Nie jest klientem końcowym ani firmą serwisową.
- **Firma serwisowa** - firma obsługująca technicznie obiekty właścicieli. Otrzymuje i przydziela zlecenia, zarządza oddziałami, serwisantami i obsługiwanymi obiektami, nadzoruje terminy i statusy oraz ma dostęp do historii prac.
- **Serwisant** - pracownik lub współpracownik firmy wykonujący przeglądy i naprawy. Otrzymuje zlecenia, proponuje terminy, dojeżdża na miejsce, identyfikuje obiekt przez QR, wykonuje prace, zmienia status i sporządza raport.
- **Klient końcowy** - osoba korzystająca z obiektu lub miejsca, w którym się znajduje, bez zarządzania serwisem, np. klient lokalu, użytkownik urządzenia lub pracownik korzystający ze sprzętu. Występuje dopiero w stanie docelowym i może zgłaszać usterki przez QR.
- **Obiekt serwisowy** - fizyczny przedmiot lub instalacja podlegająca serwisowi, np. urządzenie, maszyna, klimatyzator, ekspres do kawy, instalacja, automat, terminal lub inny sprzęt. To nadrzędne pojęcie modelu domenowego. „Urządzenie” można stosować w interfejsie lub wdrożeniu obsługującym wyłącznie urządzenia. Obiekt nie jest aplikacją, czyli systemem informatycznym.
- **Produkt** - ewentualny przyszły typ lub model z katalogu, np. „Ekspres X500”. Konkretny egzemplarz z numerem seryjnym pozostaje obiektem serwisowym.
- **Zgłoszenie klienta końcowego** - informacja o usterce, którą właściciel analizuje i może przekształcić w zlecenie. Samo zgłoszenie nie uruchamia formalnego serwisu.
- **Zlecenie serwisowe** - formalne zlecenie właściciela dla jednej firmy, dotyczące konkretnego obiektu. Zawiera m.in. obiekt, opis problemu lub zakres przeglądu, lokalizację i jej dostępność, przydzielone oddziały i/lub osoby wraz z ich oddziałami, uzgodniony termin, status i raport.
- **Oddział firmy serwisowej** - jednostka organizacji i przydziału pracy, obecna już w MVP, z nazwą i listą serwisantów. Nie jest osobną firmą, tenantem, czwartym głównym podmiotem ani rolą użytkownika. Nie należy zastępować tej nazwy ogólnym słowem „serwis”.

Relacje domenowe:

- Firma ma co najmniej jeden oddział; w prostej strukturze może on obejmować wszystkich serwisantów.
- Oddział należy do dokładnie jednej firmy i może skupiać wielu serwisantów. Każdy serwisant należy do dokładnie jednego oddziału, a przez niego do firmy.
- Właściciel może mieć wiele lokalizacji, a lokalizacja wiele obiektów.
- Obiekt ma jeden aktywny identyfikator QR i może mieć wiele zleceń w historii.
- Do zlecenia można przypisać całe oddziały i/lub wybranych serwisantów tej samej firmy. Oddział i serwisant mogą uczestniczyć w wielu zleceniach, także wspólnie z innymi oddziałami firmy.
- Przydział do oddziału i przydział indywidualny to odrębne relacje z tym samym zleceniem. Przydział osoby nie zmienia jej oddziału ani nie obejmuje jego pozostałych pracowników.
- Klient końcowy może zgłosić problem konkretnego obiektu, jeśli właściciel dopuścił publiczne zgłoszenia.
- Firma może obsługiwać obiekt przed pierwszym zleceniem na podstawie zgody właściciela. Relacja obsługi ma okres obowiązywania; pierwsze zlecenie może ją utworzyć. Nie daje dostępu do innych obiektów właściciela ani raportów innych firm.
- Zlecenie może mieć kilka kolejnych wizyt, każdą z własnym terminem, koordynatorem i wykonawcami, ale najwyżej jedną niezakończoną wizytę. Koordynator jest serwisantem pełniącym funkcję przy zleceniu lub wizycie, nie dodatkową rolą konta.

## 3. MVP: aplikacja webowa i obsługa zleceń

Wszyscy użytkownicy logują się do jednej aplikacji webowej; widok i dane zależą od roli oraz uprawnień. MVP obejmuje dokładnie trzy główne podmioty z etapu 1. Nie obejmuje klienta końcowego, publicznych zgłoszeń przez QR ani dedykowanej aplikacji mobilnej.

### 3.1. Właściciel obiektu serwisowego

Właściciel rejestruje konto, podaje dane organizacji lub lokalu, adresy lokalizacji i godziny ich dostępności, np. otwarcia i zamknięcia. Godziny te ograniczają proponowane terminy wizyt.

Prosty dashboard zapewnia:

- widok lokalizacji oraz przeglądanie, dodawanie i zarządzanie obiektami,
- tworzenie zleceń oraz podgląd aktywnych i zakończonych zleceń,
- ustawianie dostępności lokalizacji,
- widok proponowanych terminów, ich akceptację, odrzucenie lub propozycję zmiany,
- śledzenie statusów i przeglądanie historii serwisowej.

Po dodaniu obiektu właściciel może utworzyć **pierwsze zlecenie**, np. przegląd. Trafia ono do odpowiedniej firmy serwisowej.

### 3.2. Firma serwisowa

Panel operacyjny umożliwia zarządzanie właścicielami, obsługiwanymi obiektami, oddziałami, serwisantami, zleceniami, terminami i obciążeniem pracą oraz przeglądanie historii realizacji i raportów. Zapewnia terminarz, zlecenia na dziś, wyszukiwanie i filtrowanie obiektów oraz zleceń, także według oddziałów i serwisantów.

Firma tworzy oddziały przed dodaniem serwisantów i przypisuje każdego do jednego oddziału. Panel pozwala przeglądać i edytować oddziały, wyświetlać ich pracowników oraz zmieniać przynależność serwisanta. Zmiana lub usunięcie oddziału nie może pozostawić aktywnego serwisanta bez oddziału ani usuwać historii zleceń.

W MVP właściciel i firma mają po jednym koncie zarządzającym, a każdy serwisant własne konto. Firma wybiera koordynatora zlecenia, wykonawców i koordynatora wizyty oraz odpowiada za przydziały, anulowanie i zamknięcie. Nieaktywne konto lub serwisant nie uzyskuje dostępu.

Zlecenie właściciela pojawia się w panelu firmy, która przydziela je zgodnie z zasadami w rozdziale 4.

### 3.3. Serwisant

Webowy dashboard zawiera informację o własnym oddziale, terminarz i zlecenia dostępne przez przydział całego oddziału lub przydział indywidualny, także przy współpracy kilku oddziałów. Wybór innego pracownika z tego samego oddziału nie daje dostępu do zlecenia.

Dla zlecenia serwisant widzi m.in. właściciela, lokalizację, dane obiektu, opis problemu, przydzielone oddziały i osoby, wcześniejsze naprawy, dostępne godziny wizyty oraz aktualny status. Ma dostęp do panelu obiektu, historii serwisowej, zmiany statusu i formularza raportu.

### 3.4. Uzgodnienie terminu

Serwisant wizyty proponuje datę, godzinę rozpoczęcia i zakończenia. Właściciel może zaakceptować, odrzucić lub złożyć kontrpropozycję. Propozycja oznacza zgodę autora; akceptuje ją wyłącznie druga strona. System zachowuje historię propozycji i odpowiedzi.

Każda wizyta ma jeden wspólny termin dla wszystkich wykonawców. Cały przedział musi mieścić się w godzinach lokalizacji i nie kolidować z potwierdzoną lub trwającą wizytą żadnego wykonawcy. Backend ponownie sprawdza warunki podczas akceptacji i chroni przed jednoczesną rezerwacją tego samego czasu.

MVP używa tygodniowych godzin lokalizacji w jej strefie czasowej. Wizyta mieści się w jednym dniu i jednym przedziale dostępności; okna przez północ rozbija się na dwa dni. Urlopy, dojazdy i wyjątki świąteczne pozostają poza MVP.

Po akceptacji termin, obsada i koordynator wizyty są zamrożone. Zmiana przed rozpoczęciem wymaga anulowania wizyty i nowego uzgodnienia. Podczas realizacji zmiana obsady wymaga zakończenia wizyty i zaplanowania kolejnej. Zmiana godzin lokalizacji nie może po cichu unieważnić potwierdzonej wizyty.

### 3.5. Realizacja i zamknięcie

W uzgodnionym terminie serwisant jedzie na miejsce, identyfikuje obiekt, skanuje QR i po autoryzacji otwiera jego panel. Przegląda historię i bieżące zlecenie, wykonuje przegląd lub naprawę, aktualizuje status i uzupełnia raport. Przy pierwszej wizycie umieszcza kod QR na obiekcie.

W razie potrzeby powrotu powstaje kolejna wizyta, bez nadpisywania poprzedniej. Po wykonaniu prac autor finalizuje raport zbiorczy, a firma zamyka zlecenie. Raport, wizyty i historia realizacji pozostają przypisane do obiektu.

## 4. Przydzielanie zleceń

Firma korzysta z rozwijanej, hierarchicznej listy wielokrotnego wyboru. Oddział jest podstawowym poziomem przydziału; jego rozwinięcie pozwala wybrać pracowników.

- Oddziały i ich serwisanci mają osobne pola wyboru.
- Wybór całego oddziału przypisuje zlecenie jednostce i udostępnia je wszystkim jej serwisantom.
- Wybór osoby przypisuje zlecenie tylko jej.
- Można łączyć jeden lub kilka całych oddziałów z jednym lub kilkoma pracownikami dowolnych oddziałów, wyłącznie w firmie obsługującej zlecenie.
- Rozwijanie i zwijanie listy nie zmienia wyboru. Wybór części pracowników daje oddziałowi stan częściowego zaznaczenia.
- Interfejs odróżnia cały oddział od wybranych osób i jasno podsumowuje przydział przed zapisem.
- System zapisuje przydziały oddziałowe i indywidualne osobno, bez powielania wpisów ani zleceń w widoku serwisanta, nawet gdy dostęp wynika z kilku przydziałów.

Przykładowe poprawne wybory:

- cały oddział Warszawa,
- całe oddziały Warszawa i Kraków,
- cały oddział Warszawa i jeden serwisant z Krakowa,
- dwóch serwisantów z Warszawy i jeden z Krakowa, bez całych oddziałów.

Nowe zlecenie może czekać na przydział. Status „przypisane” wymaga co najmniej jednego całego oddziału lub serwisanta. Firma może zmieniać przydział; system zachowuje historię zmian.

Przydział całego oddziału nie wymaga udziału wszystkich pracowników. Firma wskazuje faktycznych wykonawców i jednego z nich jako koordynatora wizyty. Wykonawcy i koordynatorzy muszą mieć dostęp przez przydział oddziałowy lub indywidualny. Obciążenie czasowe wynika z wizyt wykonawców, a liczba zleceń z przydziałów.

System zachowuje historię przynależności do oddziałów. Przeniesienie w ramach firmy zmienia dostęp przez oddział; dla kontynuującego pracę wykonawcy lub koordynatora firma zapewnia przydział indywidualny. Przy zamknięciu lub anulowaniu kończy się przydziały, a dostęp do zakończonego zlecenia wynika z przydziałów i członkostwa bezpośrednio przed jego zamknięciem. Późniejsze przeniesienie nie odbiera tego dostępu ani nie nadaje nowemu pracownikowi cudzej historii. Przenoszenie między firmami jest poza MVP.

## 5. Obiekty, QR, statusy i raporty

### 5.1. Dodawanie obiektów

Formularz powinien być prosty. Zawiera nazwę, typ i lokalizację; może zawierać opis, producenta, model, numer seryjny, zdjęcia, dokumentację i pola zależne od typu obiektu. Dodatkowe atrybuty są walidowane według typu w backendzie; MVP nie obejmuje edytora własnych typów.

QR nie musi służyć do dodawania obiektu; jego główną rolą jest identyfikacja istniejącego obiektu. Przy pierwszym zleceniu system automatycznie generuje indywidualny kod, jeśli obiekt jeszcze go nie ma. Kod można wydrukować w oddziale, umieścić na obiekcie przy pierwszej wizycie i używać przy kolejnych zleceniach.

### 5.2. QR w MVP

Kod prowadzi do adresu aplikacji webowej identyfikującego obiekt, np.:

`https://platforma.example.com/service-objects/{publicId}`

Po skanie aplikacja identyfikuje obiekt, sprawdza zalogowanie, rolę i uprawnienia, a dopiero potem udostępnia panel przeznaczony w MVP dla serwisanta. Osoba niezalogowana lub bez uprawnień widzi logowanie albo komunikat o braku dostępu, bez historii i danych wewnętrznych.

**QR identyfikuje obiekt, ale nie jest hasłem ani mechanizmem autoryzacji.** Nie zawiera poufnych danych; znajomość kodu lub identyfikatora nie daje dostępu do panelu ani danych.

### 5.3. Panel obiektu

Panel uprawnionego serwisanta zawiera co najmniej:

- identyfikację obiektu, typ/model, jeśli przechowywany, oraz numer seryjny lub inny identyfikator techniczny,
- lokalizację, aktualne zlecenie i status,
- historię zleceń, napraw i przeglądów,
- wcześniejsze raporty i opisy rozwiązań problemów,
- zdjęcia i dokumenty zgodnie z uprawnieniami.

### 5.4. Statusy

Serwisant aktualizuje status widoczny dla firmy i właściciela. Przykładowe statusy:

- nowe,
- przypisane,
- oczekuje na ustalenie terminu,
- termin zaakceptowany,
- w drodze,
- w trakcie realizacji,
- oczekuje na części,
- wymaga ponownej wizyty,
- wykonane,
- zamknięte,
- anulowane.

Bieżący status, historia i stan wizyty zmieniają się w jednej transakcji. Statusy operacyjne aktualizuje koordynator zlecenia lub wizyty; firma zarządza przydziałami, anulowaniem i zamknięciem.

### 5.5. Raport serwisowy

Po pracy serwisant tworzy raport umożliwiający zapis co najmniej:

- wykonanych czynności,
- diagnozy przyczyny i sposobu rozwiązania problemu,
- użytych części lub materiałów, jeśli są ewidencjonowane,
- dodatkowych zaleceń,
- zdjęć i dokumentów.

Diagnoza i opis rozwiązania, poza informacją o wykonaniu zlecenia, są potrzebne do przyszłego wyszukiwania podobnych awarii i funkcji AI.

W MVP zlecenie ma jeden raport zbiorczy obejmujący wszystkie wizyty i jednego autora. Domyślnie jest nim koordynator zlecenia; firma może wskazać innego uprawnionego wykonawcę przed finalizacją, z zachowaniem audytu. Autor finalnego raportu musi uczestniczyć w co najmniej jednej zakończonej wizycie. Autor edytuje wersję roboczą. Finalizacja jest możliwa po wykonaniu zlecenia i wymaga czynności, diagnozy i rozwiązania, także opisu wyniku przeglądu bez usterki. Potem treść i załączniki są niezmienne; współautorstwo, korekty i wersjonowanie pozostają poza MVP.

## 6. Stan docelowy: klient końcowy i aplikacja mobilna

### 6.1. Publiczne zgłoszenia

Klient końcowy nie musi mieć konta właściciela ani firmy serwisowej. Skan QR zwykłym aparatem telefonu może otwierać publiczny formularz usterki, automatycznie powiązany z obiektem, bez jego wyszukiwania.

Właściciel w dashboardzie włącza lub wyłącza publiczne zgłoszenia dla obiektu lub lokalizacji:

- Włączone: zwykły skan QR otwiera formularz danego obiektu.
- Wyłączone: formularz jest niedostępny, a wejście na adres QR nie ujawnia panelu ani historii. Uprawniony serwisant nadal może używać kodu, zwłaszcza skanera aplikacji mobilnej.

Przepływ zgłoszenia:

1. Klient końcowy zgłasza problem właścicielowi obiektu.
2. Właściciel analizuje zgłoszenie.
3. Jeśli potrzebna jest interwencja, tworzy formalne zlecenie z własnym opisem dla firmy serwisowej.
4. Pierwotne zgłoszenie może pozostać przy obiekcie i być dostępne serwisantowi.

Zgłoszenie, także przypadkowe lub niepełne, nie tworzy automatycznie zlecenia.

### 6.2. Aplikacja mobilna serwisanta

Dedykowana aplikacja ma przejąć większość pracy terenowej i zastąpić korzystanie z pełnej strony podczas wizyty. Zapewnia co najmniej:

- listę zleceń dostępnych według zasad MVP oraz podgląd przydzielonych oddziałów i osób,
- terminarz i szybki dostęp do adresu oraz danych lokalizacji,
- zmianę statusu,
- panel obiektu i historię serwisową,
- tworzenie raportów,
- wykonywanie i dodawanie zdjęć,
- wbudowany skaner QR.

Opcjonalnie otwiera lokalizację w Google Maps lub Apple Maps bez własnej nawigacji.

### 6.3. Jeden QR, dwa sposoby obsługi

Obiekt nadal ma jeden kod dla klienta końcowego i serwisanta. Zachowanie zależy od kanału skanowania:

- **Aparat telefonu i przeglądarka** - otwierają adres obiektu i formularz, jeśli właściciel go włączył; przy wyłączonych zgłoszeniach nie pokazują formularza ani danych serwisowych.
- **Skaner aplikacji serwisanta** - odczytuje ten sam identyfikator i bezpośrednio otwiera mobilny panel obiektu. Wymaga zalogowania i uprawnień weryfikowanych przez backend. Panel udostępnia m.in. dane obiektu, bieżące zlecenie, historię, raporty, zgłoszenia klientów końcowych, dokumentację i zdjęcia zgodnie z uprawnieniami.

## 7. SaaS i izolacja danych

Architektura ma docelowo obsługiwać wiele niezależnych firm, zamiast jednej konkretnej. Każda tworzy konto/tenant z własnym, logicznie odseparowanym obszarem danych.

Firma nie może widzieć klientów, obiektów, oddziałów, serwisantów, zleceń, raportów ani dokumentów innej firmy. Izolację egzekwują backend i baza danych zgodnie z przyjętą architekturą; filtr frontendu nie wystarcza.

Oddziały pozostają jednostkami wewnątrz tenanta. Przydziały obejmujące wiele oddziałów lub osób nie mogą przekraczać granic firmy ani tenanta.

## 8. Zakres techniczny

- **Backend:** Java / Spring Boot.
- **Baza danych:** PostgreSQL.
- **Frontend:** React + TypeScript + Vite; TanStack Query do pobierania i zarządzania danymi backendu.
- **Uwierzytelnianie i autoryzacja:** Spring Security, role i uprawnienia.
- **Model i przydziały:** obowiązkowy jeden oddział serwisanta, osobne relacje zlecenia z oddziałami i osobami, hierarchiczny wybór wielokrotny we frontendzie, walidacja przydziałów i egzekwowanie dostępu w backendzie.
- **Wizyty i historia:** przedziały czasu, faktyczni wykonawcy, koordynatorzy, historia członkostwa oraz upoważnienia firmy do obsługi obiektu. Wymagane pola, unikalność, spójność relacji i ochrona operacji równoczesnych są określone w dokumentacji ERD v2.
- **Pliki:** dodawanie, przechowywanie i udostępnianie zdjęć oraz dokumentów zgodnie z uprawnieniami; technologia przechowywania do ustalenia.
- **Testy:** zakres i narzędzia do ustalenia.
- **Konteneryzacja:** Docker.
- **CI/CD:** automatyzacja testów, budowania i wdrażania; narzędzia do ustalenia.
- **Wdrożenie i hosting:** prywatny VPS.
- **Aplikacja mobilna po MVP:** technologia do ustalenia.

## 9. Cyberbezpieczeństwo

Obowiązkowe są:

- bezpieczne logowanie i przechowywanie haseł,
- kontrola dostępu według ról i uprawnień oraz autoryzacja zasobów w backendzie,
- sprawdzanie przynależności przydzielanych oddziałów i osób do firmy obsługującej zlecenie,
- dostęp serwisanta wynikający z przydziału jego oddziału lub przydziału indywidualnego,
- izolacja danych firm w SaaS,
- walidacja danych w backendzie,
- bezpieczny upload zdjęć i dokumentów,
- HTTPS,
- sekrety i hasła poza repozytorium,
- kopie zapasowe bazy,
- logowanie najważniejszych operacji,
- ochrona API przed nieautoryzowanym dostępem,
- brak poufnych danych w QR i autoryzacja niezależna od znajomości kodu.

## 10. Funkcje po MVP i AI

Po ukończeniu MVP można rozwijać aplikację mobilną, publiczne zgłoszenia i otwieranie lokalizacji w mapach oraz:

- magazyn części,
- automatyczne przypomnienia o przeglądach i powiadomienia,
- raporty PDF,
- statystyki serwisantów i ewidencję czasu pracy,
- integracje z kalendarzami i zaawansowane planowanie,
- system podań i urlopów, wyłącznie jeśli zostanie uznany za część domeny.

Potencjalne funkcje AI:

- klasyfikacja zgłoszeń na podstawie opisu usterki,
- wyszukiwanie podobnych awarii w historii,
- sugerowanie wcześniejszych rozwiązań podobnych problemów,
- tworzenie krótkiego raportu z notatek serwisanta.
