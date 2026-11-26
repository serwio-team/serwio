# Wygląd Serwio

Zasady wizualne aplikacji webowej. Źródłem wartości są zmienne w
[`serwio-frontend/src/styles.css`](../serwio-frontend/src/styles.css). Przy zmianie
koloru lub odstępu aktualizuj najpierw zmienną, a potem ten opis.

## Znak

<img src="../serwio-frontend/public/logo.svg" alt="Znak Serwio" width="64" height="64" />

Litera S rysowana jako trasa: kółko oznacza zgłoszenie, a linia prowadzi do wizyty
i raportu. Znak ma postać zaokrąglonego kafelka z gradientem indygo i białą linią.

| Plik | Zastosowanie |
| --- | --- |
| [`public/logo.svg`](../serwio-frontend/public/logo.svg) | Źródło znaku, favicona w nowych przeglądarkach, README |
| [`src/components/BrandMark.tsx`](../serwio-frontend/src/components/BrandMark.tsx) | Znak w aplikacji: panel boczny, logowanie, rejestracja |
| `public/favicon.ico` | Favicona 32 px dla starszych przeglądarek |
| `public/apple-touch-icon.png` | Ikona ekranu głównego, 180 px, bez zaokrągleń (system dodaje je sam) |
| [`docs/images/social-preview.png`](images/social-preview.png) | Grafika 1280×640 do README i podglądu repozytorium |

- Najmniejszy rozmiar to 16 px. Poniżej 24 px kółko trasy jest mało widoczne, więc znak nie powinien wtedy występować sam, bez nazwy.
- Wokół znaku zostaw co najmniej 1/4 jego szerokości wolnego miejsca.
- Znak działa na jasnym tle i na ciemnym panelu (`#121216`). Nie zmieniaj jego kolorów, nie obracaj go i nie dodawaj cieni.
- Nazwa obok znaku: „Serwio”, Inter 600, odstęp 10 px od znaku. Zmiana `BrandMark` wymaga ponownego wygenerowania PNG i ICO.

## Kolory

| Rola | Zmienna | Wartość |
| --- | --- | --- |
| Akcent: przyciski główne, aktywne pola, linki | `--accent` / `--accent-hover` | `#5e6ad2` / `#4e5ac4` |
| Tło akcentu | `--accent-soft` | `#eef0fb` |
| Tło strony / karty | `--bg` / `--surface` | `#f7f7f8` / `#ffffff` |
| Tekst główny / drugorzędny / pomocniczy | `--text` / `--text-2` / `--text-3` | `#17171c` / `#55555f` / `#8a8a94` |
| Obramowanie | `--border` / `--border-strong` | `#e8e8eb` / `#d6d6dc` |
| Panel boczny | `--sidebar-bg` / `--sidebar-text-strong` | `#121216` / `#ececf1` |
| Błąd i usuwanie | `--danger` | `#d23a3a` |

Gradient znaku (`#6c78e0` → `#4c57c2`) występuje tylko w znaku. Powierzchnie interfejsu są płaskie.

## Statusy

Statusy pokazuje `StatusBadge`: jasne tło i ciemniejszy tekst w tym samym kolorze.

| Ton | Znaczenie | Przykłady |
| --- | --- | --- |
| `info` (niebieski) | Zlecenie czeka na działanie albo trwa | Nowe, Przypisane, W trakcie realizacji |
| `warning` (bursztynowy) | Potrzebna decyzja jednej ze stron | Uzgadnianie terminu, Wymaga ponownej wizyty |
| `success` (zielony) | Ustalone lub zakończone | Wizyta potwierdzona, Wykonane, Zamknięte |
| `neutral` (szary) | Wyłączone z procesu | Anulowane, konto nieaktywne |

Każdy status ma tekst. Kolor tylko go wzmacnia, więc nie może być jedyną informacją.

## Typografia i układ

- Font Inter Variable (`@fontsource-variable/inter`), 14 px, interlinia 1,5. Nagłówki stron 22 px, grubość 600.
- Zaokrąglenia: 6 px dla przycisków i pól, 12 px (`--radius-lg`) dla kart i okien.
- Panel boczny ma 232 px szerokości. Na wąskim ekranie chowa się za przyciskiem menu.
- Ikony pochodzą z `lucide-react`: 14–16 px, kontur bez wypełnienia.
- Przyciski i pozycje menu mają widoczny fokus: obrys 2 px w kolorze akcentu.
