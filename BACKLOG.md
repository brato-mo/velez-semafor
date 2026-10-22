# Poboljšanja na čekanju

Zakazani zadatak "Velež Semafor – poboljšanja" svaki dan uzme **prvu neurađenu stavku** odozgo,
napravi je, testira i objavi, pa je ovdje označi sa [x] i datumom. Jasko može dopisati svoje želje
na vrh liste (ili ih reći Claudeu), one imaju prednost.

Pravila: jedna mala, završena promjena dnevno; nikad ne pokvariti postojeće; sve na bosanskom;
promjene na stranici (`docs/index.html`) stižu na telefon bez instalacije, a promjene u `android/`
traže novi APK, pa njih radi rjeđe i pažljivo.

## Lista

- [ ] **Strijelci sezone**: u kartici Utakmice → Rezultati dodati listu strijelaca FK Veleža (golovi iz `goals`) i najefikasnijih RK Veleža (iz `top`), za sezonu 2026/27.
- [ ] **Međusobni dueli**: kod sljedeće utakmice prikazati ranije rezultate protiv istog protivnika iz ove sezone (ako postoje u podacima).
- [ ] **Pretraga vijesti**: polje za pretragu naslova i sažetaka u kartici Vijesti.
- [ ] **Forma i statistika**: mali blok "Sezona u brojkama" (pobjede/neriješeno/porazi, golovi, domaćin/gost) za svaki klub.
- [ ] **Omladinci odvojeno**: filter u Vijestima "Prvi tim / Omladinska škola".
- [ ] **APK 1.1 – Dodaj u kalendar i Podijeli**: novi VelezApp metodi `calendar(...)` (Intent INSERT u kalendar telefona sa datumom, satnicom i mjestom) i `share(text)` (Android share sheet); dugmad na stranici pokazati samo kad most ima te metode.
- [ ] **Widget za početni ekran** (APK): sljedeća utakmica i odbrojavanje.
- [ ] **Uživo**: na dan utakmice češća provjera rezultata (svakih 15 min između početka i kraja) i obavijest za poluvrijeme i kraj.

## Urađeno

- [x] 2026-10-22 · Prva Android verzija (1.0): vijesti, raspored, rezultati, tabele, obavijesti, jutarnji pregled, rad bez interneta.
