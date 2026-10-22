# Velež Semafor

Neslužbena navijačka aplikacija za **FK Velež Mostar** (fudbal) i **RK Velež Mostar** (rukomet):
vijesti, raspored sa odbrojavanjem, rezultati sa strijelcima i tabele, uz obavijesti na telefonu.

## Preuzimanje (Android)

**[Preuzmi velez-semafor.apk](https://github.com/brato-mo/velez-semafor/releases/latest/download/velez-semafor.apk)**
(sve verzije: [Releases](https://github.com/brato-mo/velez-semafor/releases))

1. Otvori link na telefonu i preuzmi fajl.
2. Otvori preuzeti `velez-semafor.apk`. Ako telefon pita, dozvoli instalaciju iz ovog izvora
   (*Postavke → Instaliraj nepoznate aplikacije* za preglednik ili Fajlove).
3. Ako se pojavi Play Protect upozorenje za nepoznatu aplikaciju, izaberi *Ipak instaliraj*.
4. Otvori aplikaciju i dozvoli obavijesti.

Radi na Androidu 7.0 i novijem.

## Kako se ažurira

- **Vijesti, raspored, rezultati i tabele** su u [`docs/data/velez.json`](docs/data/velez.json).
  Claude ih provjerava svaki sat i ujutro, a GitHub Actions svakih 20 minuta dodaje nove objave sa fkvelez.ba.
  Aplikacija ih povlači pri svakom otvaranju i otprilike svaki sat u pozadini.
- **Izgled i funkcije** su u [`docs/index.html`](docs/index.html). Aplikacija pri otvaranju učita najnoviju
  verziju sa GitHuba, pa većina poboljšanja stiže **bez ponovne instalacije**.
- **Nova verzija APK-a** treba samo za promjene u samoj Android ljusci (obavijesti, rad u pozadini).
  Tada aplikacija sama pokaže dugme *Preuzmi*. Nova verzija se instalira preko stare, podaci ostaju.

Bez interneta aplikacija prikazuje zadnje sačuvane podatke.

## Obavijesti

- nova vijest o Veležu
- konačan rezultat sa strijelcima
- podsjetnik do tri sata prije utakmice
- jutarnji pregled u 7:00 (vrijeme se mijenja u postavkama aplikacije)

Ako obavijesti kasne, u postavkama aplikacije dozvoli rad u pozadini (štednja baterije).

## Za razvoj

| Dio | Gdje |
| --- | --- |
| Web aplikacija (sav izgled i logika prikaza) | `docs/index.html` |
| Podaci | `docs/data/velez.json`, alat `tools/velez_data.py` (provjera i upis) |
| Verzija, APK link i "Šta je novo" | `docs/data/app.json` |
| Android ljuska (WebView, obavijesti, rad u pozadini) | `android/` (Java, bez dodatnih biblioteka) |
| Izgradnja APK-a | `.github/workflows/android.yml` (pri promjeni u `android/`) |
| Automatske vijesti sa fkvelez.ba | `.github/workflows/news.yml`, `tools/fetch_fkvelez.py` |
| Ideje za sljedeća poboljšanja | `BACKLOG.md` |

Prije svake promjene:

```bash
python3 tools/velez_data.py validate     # podaci moraju biti ispravni
python3 tools/smoke_test.py              # stranica u headless Chromiumu (Playwright)
```

Pri promjeni `docs/index.html` povećaj `velez-version` u `<head>` (format `GGGGMMDDNN`) i upiši novost u
`docs/data/app.json`. Pri promjeni u `android/` povećaj `versionCode` i `versionName` u
`android/app/build.gradle` i isto u `docs/data/app.json` → `apk`.

Potpisni ključ `android/velez-release.p12` je zaključan lozinkom koja postoji samo kao GitHub Actions
secret `VELEZ_KEYSTORE_PASSWORD`.

Podaci su iz javnih izvora (fkvelez.ba, nfsbih.ba, rk-velez.com, svijet-rukometa.com, sportdc.net, klix.ba i dr.).
Ovo nije službena aplikacija klubova.
