-- =====================================================================
-- V4 — Koc modeli, Faz 1 (SPEC Bolum 3, 4, 6, 9; kararlar K-002, K-004)
--
-- 1) Eski alan tablolari kalkar: sifirdan baslangic (K-002). Icerik
--    artik veritabaninda degil, JSON dosyalarinda (K-004).
-- 2) Yalniz kullanici durumu tablolari kurulur. Icerige kararli metin
--    id'siyle baglanilir; icerik tablosu yok.
--
-- Migrasyondan once veritabani otomatik yedeklenir (MigrationBackup).
-- Sonraki fazlarin tablolari (kelime/gramer ilerlemesi, hata kaydi,
-- haftalik plan) kendi fazlarinda eklenir.
-- =====================================================================

DROP TABLE IF EXISTS task_completion, task, plan_day, plan,
    word_review_log, user_word, user_category_stat, skill_estimate,
    user_activity, placement_result, attempt_answer, test_attempt,
    test_question, test, question_option, question, reading_passage,
    content_unit_word, word, content_unit_block, content_unit_category,
    content_unit, mistake_category, learning_goal CASCADE;

-- --- Onboarding cevaplari -------------------------------------------

CREATE TABLE user_profile (
    user_id                 uuid PRIMARY KEY REFERENCES app_user (id) ON DELETE CASCADE,
    start_mode              text NOT NULL CHECK (start_mode IN ('FROM_ZERO', 'SOME_KNOWLEDGE')),
    onboarding_completed_at timestamptz,
    created_at              timestamptz NOT NULL DEFAULT now(),
    updated_at              timestamptz NOT NULL DEFAULT now()
);

-- Hedef gecmisi tutulur; ayni anda tek aktif hedef.
CREATE TABLE goal (
    id           uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id      uuid NOT NULL REFERENCES app_user (id) ON DELETE CASCADE,
    target_level text NOT NULL CHECK (target_level IN ('A1', 'A2', 'B1', 'B2', 'C1')),
    purpose      text NOT NULL CHECK (purpose IN
                     ('EXAM', 'UNIVERSITY', 'WORK', 'DAILY_LIFE', 'ABROAD', 'PERSONAL', 'OTHER')),
    status       text NOT NULL DEFAULT 'ACTIVE' CHECK (status IN ('ACTIVE', 'ARCHIVED')),
    created_at   timestamptz NOT NULL DEFAULT now(),
    archived_at  timestamptz
);
CREATE UNIQUE INDEX ux_goal_one_active ON goal (user_id) WHERE status = 'ACTIVE';

CREATE TABLE study_rhythm (
    user_id       uuid PRIMARY KEY REFERENCES app_user (id) ON DELETE CASCADE,
    daily_minutes integer NOT NULL CHECK (daily_minutes IN (15, 30, 45, 60, 90)),
    days_per_week integer NOT NULL CHECK (days_per_week BETWEEN 1 AND 7),
    updated_at    timestamptz NOT NULL DEFAULT now()
);

-- --- Oturum: bir modulde baslatilip bitirilen tek calisma (SPEC 5.2) --

CREATE TABLE study_session (
    id             uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id        uuid NOT NULL REFERENCES app_user (id) ON DELETE CASCADE,
    module         text NOT NULL,
    started_at     timestamptz NOT NULL DEFAULT now(),
    finished_at    timestamptz,
    active_seconds integer CHECK (active_seconds >= 0)
);
CREATE INDEX idx_study_session_user ON study_session (user_id, started_at);

-- --- Yerlestirme testi ----------------------------------------------

-- state: sorulan bloklar, soru id'leri, verilen cevaplar (JSON).
-- Dogru cevaplar istemciye cevaplamadan once gonderilmez.
CREATE TABLE placement_session (
    id               uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id          uuid NOT NULL REFERENCES app_user (id) ON DELETE CASCADE,
    study_session_id uuid REFERENCES study_session (id) ON DELETE SET NULL,
    status           text NOT NULL DEFAULT 'IN_PROGRESS'
                         CHECK (status IN ('IN_PROGRESS', 'COMPLETED', 'ABANDONED')),
    state            jsonb NOT NULL,
    result_level     text CHECK (result_level IN ('A0', 'A1', 'A2', 'B1', 'B2', 'C1')),
    started_at       timestamptz NOT NULL DEFAULT now(),
    finished_at      timestamptz
);
CREATE INDEX idx_placement_session_user ON placement_session (user_id, started_at);

-- Kaba baslangic noktasi ve oz degerlendirmeler; beceri profilinden ayri
-- tutulur (SPEC 4.1). result A0 = "A1'in altinda".
CREATE TABLE level_assessment (
    id         uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id    uuid NOT NULL REFERENCES app_user (id) ON DELETE CASCADE,
    kind       text NOT NULL CHECK (kind IN ('PLACEMENT', 'SELF_ASSESSMENT')),
    result     text NOT NULL CHECK (result IN ('A0', 'A1', 'A2', 'B1', 'B2', 'C1')),
    confidence text NOT NULL CHECK (confidence IN ('LOW', 'RELIABLE')),
    score      numeric,
    max_score  numeric,
    details    jsonb,
    taken_on   date NOT NULL,
    source_id  uuid,
    created_at timestamptz NOT NULL DEFAULT now()
);
CREATE INDEX idx_level_assessment_user ON level_assessment (user_id, taken_on);

-- --- Aktivite kaydi (SPEC Bolum 6) ----------------------------------

CREATE TABLE activity_log (
    id               uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id          uuid NOT NULL REFERENCES app_user (id) ON DELETE CASCADE,
    activity_date    date NOT NULL,
    type             text NOT NULL CHECK (type IN
                         ('HOEREN', 'LESEN', 'SCHREIBEN', 'SPRECHEN', 'GRAMER', 'KELIME',
                          'ARTIKEL', 'SEVIYE_TESTI')),
    duration_minutes integer NOT NULL CHECK (duration_minutes BETWEEN 0 AND 1440),
    origin           text NOT NULL CHECK (origin IN ('SITE', 'EXTERNAL')),
    source_kind      text CHECK (source_kind IN
                         ('YOUTUBE', 'BOOK', 'PODCAST', 'COURSE', 'TEACHER', 'AI', 'OTHER_APP', 'SELF')),
    level            text CHECK (level IN ('A0', 'A1', 'A2', 'B1', 'B2', 'C1')),
    score            numeric CHECK (score >= 0),
    max_score        numeric CHECK (max_score > 0),
    result_source    text CHECK (result_source IN
                         ('OFFICIAL_EXAM', 'TEACHER', 'MODELLTEST', 'APP_TEST', 'SELF_ASSESSMENT', 'SITE_TEST')),
    speaking_partner text CHECK (speaking_partner IN ('TEACHER', 'FRIEND', 'AI', 'ALONE')),
    speaking_topic   text CHECK (char_length(speaking_topic) <= 120),
    grammar_topic_id text,
    note             text CHECK (char_length(note) <= 200),
    content_id       text,
    content_verified boolean,
    study_session_id uuid REFERENCES study_session (id) ON DELETE SET NULL,
    created_at       timestamptz NOT NULL DEFAULT now(),
    updated_at       timestamptz NOT NULL DEFAULT now(),
    CHECK ((score IS NULL) = (max_score IS NULL)),
    CHECK (score IS NULL OR score <= max_score)
);
CREATE INDEX idx_activity_user_date ON activity_log (user_id, activity_date);

-- --- Beceri kanitlari (SPEC 4.2) ------------------------------------
-- Kademe saklanmaz; okunurken config'den hesaplanir (TierPolicy).
-- Bir aktiviteden turediyse aktivite silinince kanit da silinir.

CREATE TABLE skill_evidence (
    id               uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id          uuid NOT NULL REFERENCES app_user (id) ON DELETE CASCADE,
    skill            text NOT NULL CHECK (skill IN ('LESEN', 'HOEREN', 'SCHREIBEN', 'SPRECHEN')),
    level            text NOT NULL CHECK (level IN ('A1', 'A2', 'B1', 'B2', 'C1')),
    source           text NOT NULL CHECK (source IN
                         ('OFFICIAL_EXAM', 'TEACHER', 'MODELLTEST', 'APP_TEST', 'SELF_ASSESSMENT', 'SITE_TEST')),
    score            numeric NOT NULL CHECK (score >= 0),
    max_score        numeric NOT NULL CHECK (max_score > 0),
    evidence_date    date NOT NULL,
    content_id       text,
    content_verified boolean,
    activity_id      uuid REFERENCES activity_log (id) ON DELETE CASCADE,
    created_at       timestamptz NOT NULL DEFAULT now(),
    CHECK (score <= max_score)
);
CREATE INDEX idx_skill_evidence_user ON skill_evidence (user_id, skill, evidence_date);

-- --- Guvenlik: tum tablolar RLS acik, politika yok (yalniz backend) ---

ALTER TABLE user_profile      ENABLE ROW LEVEL SECURITY;
ALTER TABLE goal              ENABLE ROW LEVEL SECURITY;
ALTER TABLE study_rhythm      ENABLE ROW LEVEL SECURITY;
ALTER TABLE study_session     ENABLE ROW LEVEL SECURITY;
ALTER TABLE placement_session ENABLE ROW LEVEL SECURITY;
ALTER TABLE level_assessment  ENABLE ROW LEVEL SECURITY;
ALTER TABLE activity_log      ENABLE ROW LEVEL SECURITY;
ALTER TABLE skill_evidence    ENABLE ROW LEVEL SECURITY;
