# IchSpreche Deutsch

Almanca ogrenen Turk kullanicilar icin **calisma takip ve destek sistemi**.

Site Almanca ogretmez. Kullanici Almancayi disarida (YouTube, kurs, kitap, podcast)
calisir; site hedefe gore program cikarir, gunluk gorevleri takip eder, kelimeleri
araliklarla tekrar ettirir, testlerle olcer ve sonuca gore programi ayarlar.

> Site bir ogretmen degil, disiplinli bir calisma yol arkadasidir.
> Arayuz dili Turkce, calisma icerigi Almancadir.

## Teknoloji

| Katman | Secim |
|---|---|
| Backend | Java 21 + Spring Boot 3.3 (Gradle Wrapper) |
| Veritabani | Supabase (PostgreSQL) + Flyway migration |
| Kimlik dogrulama | Supabase Auth (GoTrue), backend JWT dogrular |
| Frontend | HTML + CSS + vanilla JavaScript |
| Yayin | Backend: Render, Frontend: Vercel |

## Klasorler

```
backend/    Spring Boot REST API
frontend/   Statik site
content/    Seed verisi (CSV/JSON) - icerik eklemek kod degisikligi gerektirmez
docs/       Tasarim ve plan dokumanlari
```

## Yayinda

| Parca | Adres |
|---|---|
| Site | https://learndeutsch-alpi7.vercel.app |
| API | https://ichsprechedeutsch-api.onrender.com |

Render'in ucretsiz katmani 15 dakika hareketsizlikten sonra uykuya
gecer; ilk istek 30-50 saniye surebilir. Arayuz bu sirada kullaniciya
"Sunucu uyaniyor" bandi gosterir.

## Dokumanlar

- Tasarim: `docs/superpowers/specs/2026-09-19-almanca-takip-sistemi-design.md`
- Yayina alma: `DEPLOY.md`
- Icerik kaynaklari ve lisans: `content/ATTRIBUTION.md`

## Sayfalar

| Sayfa | Ne yapar |
|---|---|
| `index.html` | Ana sayfa (herkese acik) |
| `nasil-calisir.html` | Sistemin nasil isledigi |
| `kayit.html` / `giris.html` | Supabase Auth ile kayit ve giris |
| `app/baslangic.html` | Hedef secimi + 39 soruluk yerlestirme testi |
| `app/program.html` | Gun gun takvim, fizibilite uyarisi |
| `app/bugun.html` | Gunun gorevleri, tek tikla isaretleme |
| `app/kelimeler.html` | SM-2 tekrar oturumu |
| `app/quiz.html` | Gunun gramer quiz'i |
| `app/testler.html` | Testler ve gecmis sonuclar |
| `app/gelisim.html` | Grafikler, icgoruler, beceri tahmini |
| `app/ayarlar.html` | Hedef ve hesap ayarlari |

## API

Saglik ucu disinda hepsi Supabase JWT ister.

| Uc | Ne yapar |
|---|---|
| `GET /api/health` | Ayakta mi (acik) |
| `GET /api/me` · `PATCH /api/me` | Profil |
| `GET/POST /api/goals` | Hedef |
| `GET /api/placement/test` · `POST /api/placement/submit` | Yerlestirme |
| `POST /api/plan/generate` · `GET /api/plan` | Program |
| `GET /api/today` · `POST /api/tasks/{id}/complete` | Gunluk gorevler |
| `GET /api/words/session` · `POST /api/words/{id}/review` | Kelime tekrari |
| `GET /api/quiz/today` · `POST /api/quiz/submit` | Gunluk quiz |
| `GET /api/progress` | Gelisim paneli |

## Testler

```
cd backend
./gradlew test
```

Program uretici, SM-2, kelime kotasi ve oz bildirim dogrulamasi saf
siniflar oldugu icin veritabani olmadan test edilir.

## Calistirma

### Gereksinimler

- JDK 21 (kurulu: `C:\Users\alperen\.jdks\jdk-21.0.12.1+1`)
- Python 3 (frontend'i servis etmek icin)
- Supabase projesi (asagida)

Gradle'in JDK'yi bulmasi `backend/gradle.properties` ile saglanir.
Bu dosya makineye ozeldir ve repoya girmez.

### 1. Supabase baglantisi

`backend/.env.example` dosyasini `backend/.env` olarak kopyala ve doldur:

```
cp backend/.env.example backend/.env
```

Degerler Supabase panelinden alinir:

| Degisken | Nerede |
|---|---|
| `DB_URL`, `DB_USERNAME`, `DB_PASSWORD` | Project Settings > Database > Connection string |
| `SUPABASE_URL`, `SUPABASE_JWKS_URI` | Project Settings > API |

`.env` dosyasi `.gitignore` tarafindan engellenir, asla repoya girmez.

### 2. Backend

```
cd backend
./gradlew bootRun
```

Dogrulama: http://localhost:8080/api/health -> `{"status":"UP", ...}`

Uygulama ilk aciliste Flyway ile semayi kurar (V1__schema.sql).

#### Tek seferlik: Flyway kayit tablosunu kapat

Flyway kendi `flyway_schema_history` tablosunu `public` semasinda olusturur
ve o tabloda RLS kapali gelir; Supabase'in Data API'si `public` semasini
disari actigi icin anon anahtarla okunabilir hale gelir. Icinde gizli veri
yok (migration adlari) ama acik birakmanin faydasi da yok.

Bu, Flyway migration'i olarak YAPILAMAZ: Flyway migration boyunca kendi
tablosunu kilitli tutar, `ALTER TABLE` kendi kendini kilitler ve
`statement timeout` alinir. Onun yerine Supabase SQL Editor'de bir kez
calistir:

```sql
ALTER TABLE public.flyway_schema_history ENABLE ROW LEVEL SECURITY;
```

Dogrulama - bu sorgu 0 dondurmeli:

```sql
select count(*) from pg_class c
join pg_namespace n on n.oid = c.relnamespace
where n.nspname = 'public' and c.relkind = 'r' and not c.relrowsecurity;
```

### 3. Frontend

```
python -m http.server 5500 --directory frontend
```

Adres: http://localhost:5500
