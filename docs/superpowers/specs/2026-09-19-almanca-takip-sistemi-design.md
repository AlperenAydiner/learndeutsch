# Almanca Çalışma Takip Sistemi — Tasarım Dokümanı

**Tarih:** 2026-09-19
**Durum:** Onaylandı
**Proje kodu:** IchSpreche Deutsch

---

## 1. Amaç ve Sınırlar

Almanca öğrenen Türk kullanıcılar için bir çalışma takip ve destek sistemi.

Sistem Almanca **öğretmez**. Ders anlatımı, konu anlatımı veya uzun okuma içeriği
üretmez. Kullanıcı Almancayı dışarıda (YouTube, kurs, kitap, podcast) çalışır.
Sistem şunu yapar:

- hedefe göre bir çalışma programı çıkarır,
- günlük görevleri verir ve takip eder,
- kelime verir ve aralıklı tekrarla kalıcı hale getirir,
- testlerle seviyeyi ve gelişimi ölçer,
- sonuçlara göre programı ayarlar.

**Temel döngü:** Sistem planlar → Kullanıcı dışarıda çalışır → Siteye girip görevi
işaretler → Kelime tekrarını yapar → Testle ölçülür → Program kendini ayarlar.

### Değişmez kurallar

1. Rutini kullanıcı değil, **sistem** kurar. Kullanıcı kendi programını tasarlamaz.
2. Kullanıcı hangi kaynaktan, ne kadar süre, ne zaman çalıştığını **manuel girmez**.
3. **Serbest metin girişi yoktur.** Her veri önceden tanımlı seçeneklerden seçilir.
4. Kelimeleri kullanıcı eklemez, **sistem verir**.
5. Bir görevi işaretlemek **birkaç saniye** sürmelidir.

Arayüz dili Türkçe, çalışma içeriği Almancadır.

---

## 2. Teknoloji Kararları

| Konu | Karar | Gerekçe |
|---|---|---|
| Runtime | Java 21 (LTS) + Spring Boot 4.1 | Spring Boot 4 için JDK 17+ zorunlu; 21 LTS |
| Build | Gradle Wrapper (`./gradlew`) | Makineye ayrıca Gradle/Maven kurmak gerekmez |
| Veritabanı | Supabase (PostgreSQL) | Yönetilen Postgres, bulutta, ücretsiz katman |
| Migration | Flyway | Şema sürümlenir, elle SQL çalıştırma yok |
| Kimlik doğrulama | Supabase Auth (GoTrue) | Kayıt/giriş/şifre sıfırlama hazır gelir |
| Frontend | HTML + CSS + vanilla JS | Framework'süz; PC öncelikli, mobilde de düzgün |
| Yayın | Backend → Render, Frontend → Vercel | Ücretsiz katman, GitHub push ile otomatik deploy |

### Kimlik doğrulama akışı

```
Tarayıcı ──(supabase-js: signUp/signIn)──> Supabase Auth
Tarayıcı <──(access_token: JWT)──────────── Supabase Auth
Tarayıcı ──(Authorization: Bearer <JWT>)──> Spring Boot API
                                            └─ JWKS ile imza doğrular
                                            └─ auth_user_id → app_user eşler
```

Backend veritabanına `service_role` ile bağlanır. **Tüm tablolarda RLS açık ve
"deny all"** bırakılır: frontend anon key ile tabloya doğrudan erişemez, tek kapı
API'dir. İş mantığı tek yerde toplanır.

---

## 3. Klasör Yapısı

```
IchSprecheDeutsch/
├── backend/        Spring Boot 4.1 + Java 21
├── frontend/       Statik HTML/CSS/JS
├── content/        Seed verisi (CSV/JSON)
├── docs/
└── README.md
```

### Backend — özellik bazlı paketleme

Katman bazlı (`controllers/`, `services/`) değil, özellik bazlı. Her özellik kendi
klasöründe kapalı bir birim; ileride modül eklemek kolay olsun diye.

```
backend/src/main/java/com/ichsprechedeutsch/
├── IchSprecheDeutschApplication.java
├── common/
│   ├── config/       SecurityConfig, CorsConfig, JacksonConfig
│   ├── security/     SupabaseJwtDecoder, JwtAuthFilter, @CurrentUser
│   ├── error/        GlobalExceptionHandler, ApiError, domain exception'ları
│   └── util/
├── user/             AppUser — profil, Supabase kullanıcısıyla senkron
├── goal/             LearningGoal — hedef seviye, sınav, gün, günlük süre
├── content/          ContentUnit, ContentUnitBlock, MistakeCategory (salt okunur)
├── placement/        Yerleştirme testi akışı ve sonucu
├── plan/             ← projenin kalbi
│   ├── domain/       Plan, PlanDay, Task
│   ├── generator/    WorkloadCalculator, UnitSelector, DayReserver,
│   │                 UnitDistributor, BlockScheduler, FeasibilityChecker
│   ├── api/          PlanController, DTO'lar
│   └── PlanService.java
├── task/             TaskCompletion — işaretleme, tipe özel cevaplar
├── vocabulary/       Word, UserWord, Sm2Scheduler, ReviewSessionService
├── assessment/       Question, Test, TestAttempt, AttemptAnswer, ScoringService
├── progress/         Gelişim hesapları, seri, içgörüler
└── seed/             ContentSeeder — CSV yükleyici (idempotent)

backend/src/main/resources/
├── application.yml, application-local.yml
└── db/migration/     V1__schema.sql, V2__reference_data.sql
```

`generator/` içindeki altı sınıf bilinçli olarak ayrı tutuldu: her biri tek iş
yapar, saf fonksiyon gibi davranır, **veritabanına dokunmaz**. Program üreticiyi
ancak böyle test edebiliriz.

### Frontend — sayfa başına bir JS modülü

```
frontend/
├── index.html              Ana sayfa (herkese açık)
├── nasil-calisir.html
├── giris.html, kayit.html
├── app/                    Korumalı sayfalar
│   ├── baslangic.html      Hedef seçimi + yerleştirme testi
│   ├── bugun.html
│   ├── program.html
│   ├── kelimeler.html
│   ├── testler.html
│   ├── gelisim.html
│   └── ayarlar.html
└── assets/
    ├── css/    tokens.css (renkler, artikel renkleri), base, layout,
    │           components, pages/
    ├── js/
    │   ├── core/        api.js, auth.js, guard.js, store.js, format.js
    │   ├── components/  nav.js, taskCard.js, wordCard.js, progressBar.js,
    │   │                chart.js
    │   └── pages/       bugun.js, program.js, kelimeler.js, ...
    └── img/
```

### content/ — içerik havuzu

`content_units.csv`, `content_unit_blocks.csv`, `words_a1.csv`, `words_a2.csv`,
`mistake_categories.csv`, `questions_grammar.csv`, `reading_passages.json`

**İçerik eklemek kod değişikliği gerektirmez:** CSV'ye satır eklenir, seeder yükler.

---

## 4. Veritabanı Şeması

Kimlik `auth.users` tablosunda (Supabase yönetir), geri kalan her şey `public`
şemasında.

### 4.1 Kullanıcı ve hedef

**`app_user`**
`id` uuid PK · `auth_user_id` uuid UNIQUE NOT NULL → auth.users · `email` text ·
`display_name` text · `timezone` text default 'Europe/Istanbul' ·
`created_at`, `updated_at` timestamptz

**`learning_goal`**
`id` uuid PK · `user_id` FK → app_user ON DELETE CASCADE ·
`target_level` text CHECK IN (A1,A2,B1) · `exam_type` text (GOETHE_A2, NONE) ·
`total_days` int CHECK > 0 · `daily_minutes` int CHECK > 0 · `start_date` date ·
`status` text (ACTIVE, ARCHIVED) · index (user_id, status)

**`placement_result`**
`id` uuid PK · `user_id` FK · `test_attempt_id` FK · `estimated_level` text ·
`skill_scores` jsonb · `mastered_category_codes` text[] · `created_at`

### 4.2 İçerik havuzu (seed, kullanıcıdan bağımsız)

**`mistake_category`**
`id` serial PK · `code` text UNIQUE (DATIV, AKKUSATIV, PERFEKT, ARTIKEL,
ADJEKTIV_DEKLINATION, VERBSTELLUNG, TRENNBARE_VERBEN, PRAETERITUM, NEBENSATZ,
KOMPARATIV, REFLEXIV, WORTSCHATZ …) · `name_tr` text ·
`skill` text (GRAMMAR, VOCAB, READING, LISTENING)

**`content_unit`**
`id` uuid PK · `code` text UNIQUE (ör. A2-U09) · `level` text ·
`phase` text (TEMEL, GRAMER, SINAV_ODAK, KELIME_KAMPI, DENEME, SON_TEKRAR, PROVA) ·
`sequence_no` int · `title_tr` text · `grammar_summary` text · `vocab_theme` text ·
`estimated_minutes` int · `is_new_content` bool

**`content_unit_category`**
`content_unit_id` FK · `mistake_category_id` FK · `weight` smallint · PK(ikisi)
→ *Yerleştirmede atlama ve adaptif ağırlık buradan çalışır.*

**`content_unit_block`**
`id` uuid PK · `content_unit_id` FK · `task_type` text · `reference_minutes` int ·
`instruction_tr` text
`task_type` ∈ {KELIME, GRAMER, HOEREN, LESEN, SCHREIBEN, SPRECHEN, SINAV_PRATIGI}

**`word`**
`id` uuid PK · `lemma` text NOT NULL · `word_type` text (NOUN, VERB, ADJ, ADV,
PREP, PHRASE) · `article` text (DER, DIE, DAS) · `plural_form` text ·
`meaning_tr` text NOT NULL · `example_de` text · `example_tr` text · `level` text ·
`theme` text · `source` text · `license` text · UNIQUE (lemma, meaning_tr)

**`content_unit_word`** · `content_unit_id` + `word_id`, PK(ikisi)

**`reading_passage`**
`id` uuid PK · `level` · `title_de` · `body_de` · `word_count` · `source` · `license`

**`question`**
`id` uuid PK · `question_type` text (MULTIPLE_CHOICE, GAP_FILL, ARTICLE, MATCHING) ·
`skill` text (GRAMMAR, VOCAB, LESEN) · `level` text · `mistake_category_id` FK ·
`content_unit_id` FK · `passage_id` FK · `prompt_de` text · `explanation_tr` text ·
`difficulty` smallint

**`question_option`**
`id` uuid PK · `question_id` FK · `option_text` text · `is_correct` bool · `order_no` int

**`test`** · `id` uuid PK · `test_type` text (PLACEMENT, CHECKPOINT, MINI_MOCK,
FULL_MOCK) · `level` · `title_tr` · `question_count` int · `time_limit_minutes` int

**`test_question`** · `test_id` + `question_id` + `order_no`

> `word` ve `reading_passage` tablolarındaki `source` + `license` alanları madde
> 13'ün gereği: her içeriğin nereden geldiği veride durur.

### 4.3 Kullanıcının programı

**`plan`**
`id` uuid PK · `user_id` FK · `learning_goal_id` FK · `start_date`, `end_date` date ·
`total_days`, `daily_minutes` int · `status` text (ACTIVE, COMPLETED, RECALCULATED) ·
`feasibility` text (OK, TIGHT, UNREALISTIC) · `generated_at` · `version` int
→ *Kısmi UNIQUE index: kullanıcı başına yalnızca bir ACTIVE plan.*

**`plan_day`**
`id` uuid PK · `plan_id` FK · `day_number` int · `date` date ·
`content_unit_id` FK NULL · `day_type` text (NORMAL, LIGHT, TEST, MOCK_EXAM) ·
`planned_minutes` int · `status` text (PENDING, IN_PROGRESS, DONE, MISSED) ·
UNIQUE (plan_id, day_number)

**`task`**
`id` uuid PK · `plan_day_id` FK · `task_type` text · `planned_minutes` int ·
`content_unit_id` FK · `order_no` int · `ref_type` text + `ref_id` uuid
(teste/tekrara bağlama) · `is_makeup` bool · `source_task_id` uuid

**`task_completion`**
`id` uuid PK · `task_id` FK UNIQUE · `user_id` FK ·
`status` text (DONE, PARTIAL, MISSED) · `completed_at` timestamptz ·
`self_report` jsonb · `auto_score` numeric

`self_report` jsonb çünkü her görev tipinin 1–2 farklı hazır seçeneği var.
Ayrı sütun/tablo açmak şemayı şişirirdi. **Serbest metin yok** — geçerli değerler
backend'de enum olarak doğrulanır:

| Görev tipi | `self_report` alanları |
|---|---|
| KELIME | *(yok — performans tekrar oturumundan otomatik gelir)* |
| GRAMER | *(yok — `auto_score` quiz'den gelir)* |
| HOEREN | `anlama`: AZ / YARISI / COGU |
| LESEN | `anlama`: AZ / YARISI / COGU (+ `auto_score` kısa okuma sorularından) |
| SCHREIBEN | `metin_sayisi`: 0–5 · `kalip`: HIC / KISMEN / RAHAT |
| SPRECHEN | `kayit`: EVET / HAYIR · `nasil`: COK_TAKILDIM / IDARE_EDER / AKICI |
| SINAV_PRATIGI | *(yok — `auto_score` deneme sonucundan gelir)* |

### 4.4 Kelime ve ölçme

**`user_word`** (SM-2)
`id` uuid PK · `user_id` + `word_id` UNIQUE · `ease_factor` numeric(4,2) default 2.50 ·
`interval_days` int default 0 · `repetition` int default 0 · `due_date` date ·
`last_reviewed_at` timestamptz · `lapses` int default 0 ·
`state` text (NEW, LEARNING, REVIEW, SUSPENDED) · **index (user_id, due_date)**

**`word_review_log`**
`id` bigserial · `user_word_id` FK · `reviewed_at` · `direction` text
(DE_TR, TR_DE, ARTICLE) · `quality` smallint 0–5 · `correct` bool · `response_ms` int

**`test_attempt`**
`id` uuid PK · `user_id` FK · `test_id` FK · `plan_day_id` FK · `started_at` ·
`finished_at` · `score`, `max_score` numeric ·
`status` text (IN_PROGRESS, COMPLETED, ABANDONED)

**`attempt_answer`**
`id` uuid PK · `attempt_id` FK · `question_id` FK · `selected_option_id` ·
`answer_text` · `is_correct` bool · `response_ms` int

**`user_category_stat`** ← adaptif yapının motoru
`user_id` + `mistake_category_id` PK · `attempts` int · `correct` int ·
`last_wrong_at` · `weight` numeric

> Ayrı tablo, çünkü "en zayıf 3 konu", "Dativ hata oranın %40" ve "zayıf konulara
> ek ağırlık ver" — üçü de tek yerden okunmalı. Her seferinde `attempt_answer`
> taramak sürdürülebilir değil.

**`skill_estimate`** · `user_id` · `skill` · `level_estimate` text · `score` ·
`computed_at` → zaman serisi, grafik için

**`user_activity`** · `user_id` + `activity_date` PK · `tasks_done` int ·
`minutes` int → seri (streak) hesabı

---

## 5. Program Üretici Algoritması

**Girdi:** hedef seviye, sınav türü, gün sayısı **D**, günlük dakika **M**,
yerleştirme sonucu.

### Adım 1 — Havuzu seç
Hedef seviyeye kadar tüm birimler, `sequence_no` sırasıyla.
(A2 hedefi → A1 + A2 birimleri.)

### Adım 2 — Bildiklerini at
Her birimin kategorileri yerleştirme testinde nasıl gitmiş:

| Doğruluk | Sonuç |
|---|---|
| ≥ %80 (en az 3 soruyla) | Birim **atlanır** |
| %50 – %80 | Süre **yarıya** iner (hafif tekrar) |
| < %50 | Tam süre |

Deneme/test birimleri (`is_new_content = false`) **asla atlanmaz**.

### Adım 3 — İş yükü ve fizibilite
W = Σ(efektif dakika) · Kapasite C = D × M · oran = W / C

| Oran | `feasibility` | Davranış |
|---|---|---|
| ≤ 0.90 | **OK** | Artan zaman ek tekrar/deneme gününe dağıtılır |
| 0.90 – 1.15 | **TIGHT** | "Program sıkı, boş gün yok" uyarısıyla devam |
| > 1.15 | **UNREALISTIC** | Plan yine üretilir, ama iki somut alternatif sunulur |

UNREALISTIC durumunda hesaplanan öneriler:
`gerekli_gün = ⌈W / M⌉` ve `gerekli_günlük_süre = ⌈W / D⌉`
Örnek mesaj: *"Bu hedef için süreyi 41 güne çıkar veya günlük süreni 8,5 saate
yükselt."* Üçüncü seçenek her zaman "yine de bu planla devam et".

### Adım 4 — Sondan geriye rezervasyon
Sınav günü sabit, geriye doğru pinlenir:

- Son gün → **SINAV PROVASI** (tam deneme)
- Son − 1 → **SON TEKRAR**
- Son %15'lik dilim → **DENEME KAMPI** (30 günde 3 tam deneme)
- Faz geçişleri → **CHECKPOINT** testi (A1 bitince, A2 gramer bitince)
- Her 7. gün → **LIGHT** (yeni konu yok, tekrar + kelime)

Kalan günler öğretim günüdür.

### Adım 5 — Dağıtım
Birimler öğretim günlerine sırayla atanır.

- Birim sayısı < gün sayısı → fazla günler LIGHT'a döner
- Birim sayısı > gün sayısı → günde birden fazla birim birleştirilir; günlük M
  aşılırsa birim bölünüp ertesi güne taşınır

### Adım 6 — Blok üretimi
Referans şablon 390 dk üzerinden ölçeklenir: `k = M / 390`.
Her blok süresi = `round(reference_minutes × k)`, 5'e yuvarlanır.

| Blok | Referans | k = 1.0 (6,5 sa) | k = 0.31 (2 sa) |
|---|---|---|---|
| Kelime | 60 | 60 | 20 |
| Gramer | 90 | 90 | 30 |
| Hören | 60 | 60 | 20 |
| Lesen | 60 | 60 | 20 |
| Schreiben | 45 | 45 | — |
| Sprechen | 45 | 45 | — |
| Sınav pratiği | 30 | 30 | — |

**Minimum eşik 15 dk:** altına düşen bloklar o gün düşürülür ve gün-aşırı
rotasyona alınır. Günde 2 saat ayıran kullanıcı 7 cılız blok değil, 3–4 anlamlı
blok görür; Schreiben/Sprechen gün aşırı gelir.

Gün tipi override'ları:
- **LIGHT** → sadece KELIME + zayıf kategori GRAMER tekrarı
- **TEST / MOCK_EXAM** → SINAV_PRATIGI bloğu (`ref_id` → test) + KELIME

### Adım 7 — Kelime kotası (günlük, otomatik kısılır)

```
bekleyen_tekrar × 8 sn + yeni_kelime × 25 sn ≤ kelime_bloğu_saniye

yeni_kota = max(0, ⌊(blok_sn − bekleyen × 8) / 25⌋)
```

Üst sınır: birimin kelime teması kotası ve seviye tavanı.
Tekrar yükü bloğu zaten dolduruyorsa **o gün yeni kelime verilmez**:
*"Bugün yeni kelime yok, 63 tekrarın var."*

### Adım 8 — Adaptasyon (plan üretildikten sonra sürekli çalışır)

- MISSED / PARTIAL görevler → sonraki LIGHT güne `is_makeup` görev olarak eklenir
- `user_category_stat.weight` yüksek kategoriler → sonraki Gramer bloğuna ek soru,
  Kelime bloğuna o temadan ek tekrar
- Beklenen ilerlemenin %70'inin altına düşülürse → panelde uyarı +
  "programı yeniden hesapla" önerisi
- Yeniden hesaplama: kalan gün ve kalan birimle **Adım 2'den** yeniden çalışır,
  geçmiş korunur, `plan.version++`
- Denemeler aynı sırayla tekrarlanmaz; yanlış yapılan kategorilerden soru ağırlığı
  artırılarak yeni kombinasyon kurulur

### SM-2 (kelime tekrarı)

Kartta dört düğme:
*Bilmiyordum* (q=0) · *Zor hatırladım* (q=3) · *Hatırladım* (q=4) · *Çok kolay* (q=5)

```
EF' = EF + (0.1 − (5−q) × (0.08 + (5−q) × 0.02)),  alt sınır 1.3

q < 3  → repetition = 0, interval = 1, lapses++
q ≥ 3  → 1. tekrar: 1 gün
         2. tekrar: 6 gün
         sonrası:   round(interval × EF)

due_date = bugün + interval
```

Soru yönü rastgele değil, kademeli:
yeni kelime **DE → TR**, oturmuşsa **TR → DE**, isimlerde ayrıca **artikel** sorusu.
Artikel renk kodu: **der** mavi · **die** kırmızı · **das** yeşil.

---

## 6. Site Şeması

**Herkese açık**
- Ana sayfa — projenin ne olduğu ve ne olmadığı, nasıl çalıştığı, kimin için
  olduğu, örnek bir gün akışı, kayıt/giriş çağrısı
- Nasıl çalışır
- Giriş / Kayıt

**Hamburger menü:** Ana sayfa · Nasıl çalışır · Giriş/Kayıt →
giriş sonrası panel bağlantıları + Çıkış

**Kullanıcı paneli (korumalı)**

| Sayfa | İçerik |
|---|---|
| Bugün | Günün konusu, görev listesi, işaretleme, ilerleme çubuğu |
| Program | Gün gün takvim, tamamlanan/kalan günler |
| Kelimeler | Günlük tekrar oturumu, öğrenilen kelimeler listesi |
| Testler | Yerleştirme, ara testler, denemeler, geçmiş sonuçlar |
| Gelişim | Grafikler, seviye tahmini, zayıf konular, içgörüler |
| Ayarlar | Hedef/süre güncelleme, programı yeniden hesaplatma, hesap |

---

## 7. MVP Kapsamı ve Bilinçli Kısıtlar

### Kapsam dışı bırakılanlar (bilinçli)

| Konu | Karar | Gerekçe |
|---|---|---|
| **Hören otomatik skor** | MVP'de **yok** | Ses üretimi (TTS/kayıt) ayrı bir altyapı işi. Hören bloğu programda durur, "Ne kadar anladın?" ile takip edilir — Schreiben/Sprechen gibi. |
| Schreiben / Sprechen puanlama | Yok | Spec'te de yok; sadece takip |
| B1 hedefi | Yok | Şema destekler, içerik 2. aşamada |
| Bildirim/hatırlatma | Yok | İleri aşama |

**Sonuç:** MVP'de test edilen alanlar **Kelime, Gramer, Lesen**.
(Spec madde 7'deki dört alandan Hören 2. aşamaya kaydı.)

### MVP adımları

| # | Adım | Çıktı | Doğrulama |
|---|---|---|---|
| 0 | İskelet | Gradle + JDK 21, Spring Boot 4.1, Supabase bağlantısı, Flyway, `/api/health` | `./gradlew bootRun` → health 200 |
| 1 | Auth | Supabase JWT doğrulama, `app_user` senkronu, `/api/me`, frontend kayıt/giriş | Token ile `/api/me` çalışır, tokensız 401 |
| 2 | Ana sayfa + menü | `index.html`, hamburger menü, korumalı rota yönlendirmesi | Girişsiz `/app/bugun` → `giris.html` |
| 3 | İçerik havuzu | Şema + Excel'den seed (birimler, bloklar, kategoriler, A1–A2 kelimeler) | Seeder 2 kez çalışır, kayıt çiftlenmez |
| 4 | Hedef + yerleştirme | Hedef formu, yerleştirme testi, `placement_result` | Test çöz → atlanacak kategoriler doğru |
| 5 | **Program üretici** | Generator + `/program` takvim + fizibilite uyarısı | Birim testleri: 30g/6,5sa · 60g/2sa · 10g/1sa (UNREALISTIC) |
| 6 | Bugün ekranı | Görev listesi, tipe özel tek-tık işaretleme, ilerleme çubuğu | İşaretle → yenilemede durum korunur |
| 7 | Kelime sistemi | Tekrar oturumu, SM-2, artikel renk kodu, günlük kota | Kart çöz → `due_date` beklenen aralıkta |
| 8 | Gramer quiz | Günün konusuna mini quiz, otomatik skor, kategori istatistiği | Quiz çöz → `user_category_stat` güncellenir |
| 9 | Gelişim paneli | Uyum oranı, seri, kelime istatistiği, test skoru grafiği | 3 günlük sahte veri → sayılar elle doğrulanır |
| 10 | Yayın | Render + Vercel, secret yönetimi | Canlı URL'den kayıt → program → görev uçtan uca |

**Adım 5 en riskli parça.** Generator saf sınıflardan oluştuğu için testleri koddan
önce yazılacak.

### Sonraki aşamalar

**2. aşama:** Ara testler ve denemeler · Hata kategorisi analizi ve adaptif ayarlama ·
Otomatik içgörüler ve beceri bazlı seviye tahmini · Hören ses içeriği ve otomatik
skor · B1 desteği

**İleri aşama:** Yapay zekâ destekli yazma geri bildirimi · Konuşma/telaffuz
değerlendirmesi · Bildirim ve hatırlatmalar

---

## 8. İçerik Kaynakları ve Lisans

- Kelime listeleri, sorular ve örnek cümleler proje için **özgün** hazırlanır veya
  açık lisanslı kaynaklardan (ör. Wiktionary) lisans koşullarına uyularak alınır.
- Goethe'nin resmi kelime listeleri ve sınav materyalleri **yalnızca seviye kapsamı
  için referans**tır; birebir kopyalanmaz.
- Her `word` ve `reading_passage` satırı `source` + `license` alanlarını taşır.
- Excel dosyası (`Goethe_A2_30_Gun_6_7_Saatlik_Program.xlsx`) sabit bir program
  değil, sistemin parçalayıp yeniden dağıtacağı **içerik havuzu** olarak ele alınır:
  her satır bir `content_unit`, her sütun bir `content_unit_block`.

---

## 9. Güvenlik

- Şifreler Supabase Auth'ta yönetilir; uygulama veritabanında şifre tutulmaz.
- Her API isteği JWT ile doğrulanır; `user_id` **token'dan** alınır, istekten değil.
- Tüm kullanıcı verisi sorgularında `user_id` filtresi zorunludur.
- Tüm tablolarda RLS açık ve "deny all" — anon key ile doğrudan erişim kapalı.
- Gizli bilgiler (Supabase URL/key, DB şifresi) `.env` üzerinden gelir, repoya
  girmez; `.gitignore` bunu zorlar.
