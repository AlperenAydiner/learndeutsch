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

## Dokumanlar

- Tasarim: `docs/superpowers/specs/2026-09-19-almanca-takip-sistemi-design.md`

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

### 3. Frontend

```
python -m http.server 5500 --directory frontend
```

Adres: http://localhost:5500
