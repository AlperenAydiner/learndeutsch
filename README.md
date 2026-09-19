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

Adim 0 tamamlandiginda bu bolum doldurulacak.
