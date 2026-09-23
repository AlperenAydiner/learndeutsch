# Faz 0 — Analiz raporu

Tarih: 2026-09-22 · Kapsam: mevcut repo, SPEC ile karşılaştırma, hedef mimari, geçiş planı. Kod yazılmadı.

## 1. Mevcut durum

### Teknoloji yığını
- **Backend:** Spring Boot 4.1.1, Java 21, Gradle Wrapper 9.7.1. Render ücretsiz katmanında Docker imajı (15 dk hareketsizlikte uyur; ilk istek 30–50 sn).
- **Veritabanı ve kimlik:** Supabase Postgres (Frankfurt, Session pooler 5432) + Supabase Auth (ES256 JWT, JWKS ile doğrulama). Tüm tablolarda RLS açık ve politika yok; backend service rolüyle bağlanır.
- **Ön yüz:** vanilla HTML + CSS + JS (ES modülleri), Vercel'de statik.
- **Şema:** Flyway V1 (25 tablo), V2 (içerik kodları), V3 (eski şık silinince seçim NULL).
- **Test:** 43 JUnit testi, 4 dosya (PlanGenerator, SelfReportSpec, Sm2Scheduler, VocabularyQuota).

### Backend paketleri
| Paket | İşi |
|---|---|
| `common` | güvenlik (JWT, `@CurrentUser`, JSON hata gövdeleri, CORS), hata tipleri |
| `user` | `app_user`, `/me` |
| `goal` | `LearningGoal`: hedef seviye (A1/A2), sınav, gün sayısı, günlük dakika |
| `placement` | sabit 39 soruluk yerleştirme, seviye tahmini, kategori sonuçları |
| `plan` | `PlanGenerator` + `BlockScheduler`: içerik birimlerinden gün gün program |
| `task` | görev işaretleme (Yaptım/Yarım/Yapamadım), `SelfReportSpec` hazır seçenekleri |
| `vocabulary` | SM-2 (`Sm2Scheduler`), günlük yeni kelime kotası (`VocabularyQuota`), tekrar oturumu |
| `assessment` | günün gramer quiz'i |
| `progress` | Gelişim paneli verileri |
| `seed` | açılışta CSV → DB içerik tohumlayıcı |

### Tablolar
app_user, learning_goal, mistake_category, content_unit, content_unit_category, content_unit_block, word, content_unit_word, reading_passage, question, question_option, test, test_question, test_attempt, attempt_answer, placement_result, plan, plan_day, task, task_completion, user_word, word_review_log, user_category_stat, skill_estimate, user_activity.

### İçerik (`/content/*.csv`)
- 271 kelime (A1 140 · A2 131)
- 85 soru, 340 şık (A1 50 · A2 35), 39'u yerleştirme testinde
- 32 içerik birimi (A1 10 · A2 22), 25 hata kategorisi, blok şablonları
- **Yok:** okuma metni, Schreiben/Sprechen görevi, Kann-Beschreibung, dış link, B1–C1 içeriği, `verified` alanı, `estimatedMinutes` (birim düzeyinde var, öğe düzeyinde yok)

### Ön yüz
- 12 sayfa: `index`, `nasil-calisir`, `giris`, `kayit`, `app/{bugun, program, kelimeler, quiz, testler, gelisim, ayarlar, baslangic}`
- Tasarım sistemi: `tokens.css`, `base.css`, `layout.css`, `app.css` + sayfa CSS'leri (minimal, geniş ekran düzeni — yerelde, henüz yayında değil)
- `core/`: `api.js` (token ekler, uyanma olayı), `auth.js` (Supabase), `guard.js`, `config.js`, `tarih.js` (yerel takvim, UTC'siz biçimleme)
- `components/`: `nav.js`, `wakeBanner.js`

### Mevcut kullanıcı akışı
kayıt/giriş → hedef (A1/A2, sınav, 30–120 gün, günlük saat) → sabit 39 soruluk yerleştirme → gün gün zorunlu program → her gün görev işaretleme → SM-2 kelime tekrarı → günün gramer quiz'i → Gelişim paneli.

## 2. SPEC'e göre boşluklar

| Alan | Şimdi | SPEC |
|---|---|---|
| Seviye | Gramer/kelime sonuçlarından "beceri tahmini" — **K1 ve K2'yi ihlal ediyor** | Yalnız 4 beceri; kanıt → tahmin; güven kademesi (Bölüm 4) |
| Yerleştirme | Sabit 39 soru, A1–A2 | Adaptif A1–C1, kaba ve düşük güvenli başlangıç (4.1) |
| Onboarding | Hedef A1/A2, gün sayısı, saat | Sıfırdan/biraz biliyorum, hedef A1–C1, amaç, 15–90 dk × haftada gün (Bölüm 3) |
| Plan | Gün gün zorunlu program | Haftalık pay dağılımı + en fazla 3 gerekçeli öneri, zorunluluk yok (5.3, 5.4) |
| Kayıt | Görev işaretleme | Otomatik aktivite kaydı + "Bugün ne yaptın?" (Bölüm 6) |
| SRS | SM-2 | Merdiven 1→3→7→14→30→60→120; Bilemedim/Zorlandım/Bildim (8.1) |
| Hata | Kategori istatistiği | Etiketli hata kaydı: aktif → düzeliyor → çözüldü (8.4) |
| Cevap kontrolü | Yalnız çoktan seçmeli | Yazmalı sorular, ä/ö/ü/ß butonları, `acceptedAnswers`, esnek yazım (8.3) |
| İçerik | CSV → DB tabloları | Seviye bazlı JSON, depolamaya yazılmaz, `verified:false`, doğrulayıcılar (9.1, 10) |
| Menü | Bugün / Program / Kelimeler / Testler / Gelişim | Ana Sayfa · Öğren · Koçluk (Bölüm 7) |
| Zaman | Servislerde `LocalDate.now()` | Tarih parametre (test ve demo modu için şart) |
| Kullanıcı verisi | Dışa aktarma yok | JSON dışa/içe aktarma + sıfırlama (9.2) |

## 3. Korunacak / refactor / silinecek

Kalıcılık kararı **B (hesaplı yapı)** olduğu için sınıflandırma buna göredir.

### Korunur
- Spring Boot iskeleti, güvenlik katmanı (JWT/JWKS ES256, `@CurrentUser`, JSON hata gövdeleri, CORS), `app_user`, Flyway, Supabase Auth.
- Ön yüz yığını ve tasarım sistemi; `api.js`, `auth.js`, `guard.js`, `wakeBanner.js`, `nav.js`, `tarih.js`; giriş ve kayıt sayfaları.
- `tools/devserver.py`, `SITEYI-AC.bat`, `tools/backend.cmd`.
- İçerik: 271 kelime ve 85 soru JSON'a dönüştürülüp `verified:false` ile yeniden kullanılır; ayrı inceleme oturumunda gözden geçirilir.

### Refactor
- Tüm uygulama sayfaları yeni bilgi mimarisine (Ana Sayfa · Öğren · Koçluk).
- `LearningGoal` → Goal + StudyRhythm.
- Ana sayfa metni: "Kredi kartı yok" SPEC'in kaçınılmasını istediği ücretli-SaaS dili → "%100 ücretsiz" + ürünün gerçek bir özelliği.

### Silinir / değiştirilir
| Parça | Gerekçe |
|---|---|
| `PlanGenerator`, `BlockScheduler`, `plan*` tabloları | Gün gün zorunlu program; SPEC haftalık pay + öneri istiyor |
| `task`, `SelfReportSpec`, `task_completion` | Görev işaretleme yerine aktivite kaydı |
| `Sm2Scheduler`, `VocabularyQuota` | SM-2 yerine aralık merdiveni; kota kuralı Ek A'ya göre |
| Sabit yerleştirme | Adaptif A1–C1 |
| `skill_estimate`, `user_category_stat` mantığı | K1/K2 ihlali (gramer/kelimeyi beceri sayıyor) |
| İçerik tabloları + CSV tohumlayıcı | 9.2: içerik depolamaya yazılmaz. İçerik JSON'dan backend belleğine yüklenir; DB yalnız kullanıcı durumunu tutar. Açılışta canlı DB'ye yazan tohumlayıcı riski (bir kez 502'ye yol açtı) de kalkar. |

## 4. Hedef mimari

```
content/{a0,a1,a2,b1,b2,c1}/   words, grammar, questions, reading, writing,
                               speaking, cando, links, placement (.json)
backend/.../content/           ContentCatalog (açılışta JSON okur, salt okunur)
                               ContentValidator (JUnit, her build'de)
backend/.../level/             kanıt→tahmin, güven, genel tahmin, çalışma
                               seviyesi, hedef tamamlama, adaptif yerleştirme
backend/.../coach/             8 kural + haftalık/günlük plan
                               (arayüz: ileride AI ile değiştirilebilir)
backend/.../srs/               aralık merdiveni
backend/.../answer/            cevap kontrolü
backend/.../errors/            hata hafızası
backend/.../config/            Ek A → @ConfigurationProperties
```

- **Alan mantığı:** saf, veritabanından ve arayüzden bağımsız sınıflar; bugünün tarihini parametre alır. Örnek desen: mevcut `PlanGenerator` (DB'siz saf sınıf + JUnit).
- **Config (K7):** Ek A değerleri `application.yml` altında tipli `@ConfigurationProperties` sınıflarında; koda sabit sayı yazılmaz. Ön yüz ihtiyaç duyduğu değerleri API'den alır.
- **Kalıcılık:** repository arayüzleri arkasında kullanıcı durumu tabloları: goal, study_rhythm, level_assessment, skill_evidence, activity_log, session, vocabulary_progress, grammar_progress, test_result, error_record, weekly_plan.
- **Doğrulayıcı:** şema; tek doğru cevap veya `acceptedAnswers`; doğru cevap şıklarda; etiket gramer ağacında; örnek cümle kelimeyi içeriyor; artikel ve çoğul biçimi; yineleme; zorunlu alanlar (Türkçe anlam, seviye, `estimatedMinutes`).
- **Kullanıcı işlemleri:** `/me/export` (JSON), `/me/import`, `/me/reset`.
- **Demo modu (Faz 4):** ayrı demo kullanıcısı, simüle tarih yalnız demo kullanıcısında geçerli, ekranda "DEMO" etiketi; gerçek kullanıcı verisine asla yazmaz.

## 5. Geçiş ve migrasyon planı

- **Kullanıcı verisi:** sıfırdan başlanır (karar K-002), taşıma yok.
- **Şema:** yeni Flyway migrasyonu eski alan tablolarını kaldırır ve kullanıcı durumu tablolarını kurar.
- **Yedek (9.2):** Flyway `beforeMigrate` callback'i, bekleyen migrasyon varsa kullanıcı tablolarını tarihli `backup_*` şemasına kopyalar. Eski veri bu yolla veritabanında korunur.
- **İçerik:** CSV → JSON dönüştürme betiği (tek seferlik), sonra CSV'ler silinir.
- **Yayın:** eski uygulama canlıda kalır; yeni sürüm yerelde hazırlanır ve kullanıcı "publish" deyince tek seferde yayına alınır.

## 6. Kalıcılık modeli (9.2)

Önerim A (hesapsız, cihazda) idi; gerekçeler: SPEC'in istemci yönlü yapısı, Render uyku süresi, Supabase duraklatma riski. **Kullanıcı B'yi seçti** (cihazlar arası erişim, mevcut giriş sistemi). Karar ve bedelleri `DECISIONS.md` K-001'de.

## 7. SPEC 5.3 / Ek A itirazları

1. **Yerleştirme blok boyutu:** 4 şıklı soruda 5'lik blokta şansla 3/5 (%60) alma olasılığı ≈ %10; tek şanslı blok bir seviye yukarı iter. Öneri: blok 6, en fazla 24 soru. **Kabul edildi** (K-003).
2. **`verified:false` bir kademe düşük:** `verified`'ı yalnız kullanıcı işaretleyebildiği için site içi Lesen kanıtı "orta" yerine "düşük" sayılır; "güvenilir" durum orta/yüksek kanıt gerektirdiğinden yalnız site içi çalışmayla ulaşılamaz. Varsayılan korunur, arayüz nedenini gösterir (K-008).
3. **"Hedefe yakın"** "tahmini hedef seviyede" → "hedef seviyede veya üstünde" olarak yorumlanır (K-007).
4. **Eski brief'le çelişkiler** SPEC lehine çözülür (K-006).

Koç kurallarının öncelik sırasına itirazım yok.
