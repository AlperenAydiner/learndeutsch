-- =====================================================================
-- IchSpreche Deutsch — V1 sema
-- =====================================================================
-- Kimlik Supabase Auth (auth.users) tarafindan yonetilir.
-- Uygulama verisi public semasinda durur.
--
-- NOT: auth.users'a yabanci anahtar TANIMLANMAZ. Flyway'i calistiran
-- rolun auth semasinda REFERENCES yetkisi olmayabilir; bagi uygulama
-- katmani kurar (app_user.auth_user_id = JWT'deki sub).
-- =====================================================================

-- ---------------------------------------------------------------------
-- 1. KULLANICI VE HEDEF
-- ---------------------------------------------------------------------

CREATE TABLE app_user (
    id            uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    auth_user_id  uuid        NOT NULL UNIQUE,
    email         text        NOT NULL,
    display_name  text,
    timezone      text        NOT NULL DEFAULT 'Europe/Istanbul',
    created_at    timestamptz NOT NULL DEFAULT now(),
    updated_at    timestamptz NOT NULL DEFAULT now()
);

CREATE TABLE learning_goal (
    id            uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id       uuid        NOT NULL REFERENCES app_user (id) ON DELETE CASCADE,
    target_level  text        NOT NULL CHECK (target_level IN ('A1', 'A2', 'B1')),
    exam_type     text        NOT NULL CHECK (exam_type IN ('GOETHE_A2', 'NONE')),
    total_days    integer     NOT NULL CHECK (total_days > 0),
    daily_minutes integer     NOT NULL CHECK (daily_minutes > 0),
    start_date    date        NOT NULL,
    status        text        NOT NULL DEFAULT 'ACTIVE'
                              CHECK (status IN ('ACTIVE', 'ARCHIVED')),
    created_at    timestamptz NOT NULL DEFAULT now()
);

CREATE INDEX idx_learning_goal_user_status ON learning_goal (user_id, status);

-- ---------------------------------------------------------------------
-- 2. ICERIK HAVUZU  (seed verisi, kullanicidan bagimsiz)
-- ---------------------------------------------------------------------

CREATE TABLE mistake_category (
    id       serial PRIMARY KEY,
    code     text NOT NULL UNIQUE,
    name_tr  text NOT NULL,
    skill    text NOT NULL CHECK (skill IN ('GRAMMAR', 'VOCAB', 'READING', 'LISTENING'))
);

CREATE TABLE content_unit (
    id                uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    code              text    NOT NULL UNIQUE,
    level             text    NOT NULL CHECK (level IN ('A1', 'A2', 'B1')),
    phase             text    NOT NULL CHECK (phase IN (
                          'TEMEL', 'GRAMER', 'SINAV_ODAK', 'KELIME_KAMPI',
                          'DENEME', 'SON_TEKRAR', 'PROVA')),
    sequence_no       integer NOT NULL,
    title_tr          text    NOT NULL,
    grammar_summary   text,
    vocab_theme       text,
    estimated_minutes integer NOT NULL CHECK (estimated_minutes > 0),
    is_new_content    boolean NOT NULL DEFAULT true,
    created_at        timestamptz NOT NULL DEFAULT now()
);

CREATE INDEX idx_content_unit_level_seq ON content_unit (level, sequence_no);

-- Birim <-> hata kategorisi. Yerlestirmede atlama karari ve
-- adaptif agirlik bu tablodan hesaplanir.
CREATE TABLE content_unit_category (
    content_unit_id     uuid     NOT NULL REFERENCES content_unit (id) ON DELETE CASCADE,
    mistake_category_id integer  NOT NULL REFERENCES mistake_category (id),
    weight              smallint NOT NULL DEFAULT 1 CHECK (weight > 0),
    PRIMARY KEY (content_unit_id, mistake_category_id)
);

-- Birimin referans blok dagilimi (Excel'in sutunlari).
CREATE TABLE content_unit_block (
    id                uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    content_unit_id   uuid    NOT NULL REFERENCES content_unit (id) ON DELETE CASCADE,
    task_type         text    NOT NULL CHECK (task_type IN (
                          'KELIME', 'GRAMER', 'HOEREN', 'LESEN',
                          'SCHREIBEN', 'SPRECHEN', 'SINAV_PRATIGI')),
    reference_minutes integer NOT NULL CHECK (reference_minutes > 0),
    instruction_tr    text,
    UNIQUE (content_unit_id, task_type)
);

CREATE TABLE word (
    id          uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    lemma       text NOT NULL,
    word_type   text NOT NULL CHECK (word_type IN (
                    'NOUN', 'VERB', 'ADJ', 'ADV', 'PREP', 'PHRASE')),
    article     text CHECK (article IN ('DER', 'DIE', 'DAS')),
    plural_form text,
    meaning_tr  text NOT NULL,
    example_de  text,
    example_tr  text,
    level       text NOT NULL CHECK (level IN ('A1', 'A2', 'B1')),
    theme       text,
    source      text,
    license     text,
    created_at  timestamptz NOT NULL DEFAULT now(),
    UNIQUE (lemma, meaning_tr),
    -- Isimlerin artikeli olmali, isim olmayanin olmamali.
    CONSTRAINT chk_article_only_for_nouns
        CHECK ((word_type = 'NOUN' AND article IS NOT NULL)
            OR (word_type <> 'NOUN' AND article IS NULL))
);

CREATE INDEX idx_word_level_theme ON word (level, theme);

CREATE TABLE content_unit_word (
    content_unit_id uuid NOT NULL REFERENCES content_unit (id) ON DELETE CASCADE,
    word_id         uuid NOT NULL REFERENCES word (id) ON DELETE CASCADE,
    PRIMARY KEY (content_unit_id, word_id)
);

CREATE TABLE reading_passage (
    id         uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    level      text    NOT NULL CHECK (level IN ('A1', 'A2', 'B1')),
    title_de   text    NOT NULL,
    body_de    text    NOT NULL,
    word_count integer,
    source     text,
    license    text,
    created_at timestamptz NOT NULL DEFAULT now()
);

CREATE TABLE question (
    id                  uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    question_type       text     NOT NULL CHECK (question_type IN (
                            'MULTIPLE_CHOICE', 'GAP_FILL', 'ARTICLE', 'MATCHING')),
    -- MVP'de HOEREN yok: dinleme skorsuz, sadece takip ediliyor.
    skill               text     NOT NULL CHECK (skill IN ('GRAMMAR', 'VOCAB', 'LESEN')),
    level               text     NOT NULL CHECK (level IN ('A1', 'A2', 'B1')),
    mistake_category_id integer  NOT NULL REFERENCES mistake_category (id),
    content_unit_id     uuid     REFERENCES content_unit (id) ON DELETE SET NULL,
    passage_id          uuid     REFERENCES reading_passage (id) ON DELETE CASCADE,
    prompt_de           text     NOT NULL,
    explanation_tr      text,
    difficulty          smallint NOT NULL DEFAULT 3 CHECK (difficulty BETWEEN 1 AND 5),
    created_at          timestamptz NOT NULL DEFAULT now()
);

CREATE INDEX idx_question_skill_level ON question (skill, level);
CREATE INDEX idx_question_category     ON question (mistake_category_id);

CREATE TABLE question_option (
    id          uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    question_id uuid    NOT NULL REFERENCES question (id) ON DELETE CASCADE,
    option_text text    NOT NULL,
    is_correct  boolean NOT NULL DEFAULT false,
    order_no    smallint NOT NULL,
    UNIQUE (question_id, order_no)
);

CREATE INDEX idx_question_option_question ON question_option (question_id);

CREATE TABLE test (
    id                  uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    test_type           text    NOT NULL CHECK (test_type IN (
                            'PLACEMENT', 'CHECKPOINT', 'MINI_MOCK', 'FULL_MOCK')),
    level               text    NOT NULL CHECK (level IN ('A1', 'A2', 'B1')),
    title_tr            text    NOT NULL,
    question_count      integer NOT NULL CHECK (question_count > 0),
    time_limit_minutes  integer CHECK (time_limit_minutes > 0),
    created_at          timestamptz NOT NULL DEFAULT now()
);

CREATE TABLE test_question (
    test_id     uuid     NOT NULL REFERENCES test (id) ON DELETE CASCADE,
    question_id uuid     NOT NULL REFERENCES question (id) ON DELETE CASCADE,
    order_no    smallint NOT NULL,
    PRIMARY KEY (test_id, question_id),
    UNIQUE (test_id, order_no)
);

-- ---------------------------------------------------------------------
-- 3. OLCME  (plan tablolari buna referans verdigi icin once gelir)
-- ---------------------------------------------------------------------

CREATE TABLE test_attempt (
    id          uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id     uuid        NOT NULL REFERENCES app_user (id) ON DELETE CASCADE,
    test_id     uuid        NOT NULL REFERENCES test (id),
    plan_day_id uuid,  -- FK asagida, plan_day olustuktan sonra eklenir
    started_at  timestamptz NOT NULL DEFAULT now(),
    finished_at timestamptz,
    score       numeric(6, 2),
    max_score   numeric(6, 2),
    status      text        NOT NULL DEFAULT 'IN_PROGRESS'
                            CHECK (status IN ('IN_PROGRESS', 'COMPLETED', 'ABANDONED'))
);

CREATE INDEX idx_test_attempt_user ON test_attempt (user_id, started_at DESC);

CREATE TABLE attempt_answer (
    id                 uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    attempt_id         uuid    NOT NULL REFERENCES test_attempt (id) ON DELETE CASCADE,
    question_id        uuid    NOT NULL REFERENCES question (id),
    selected_option_id uuid    REFERENCES question_option (id),
    answer_text        text,
    is_correct         boolean NOT NULL,
    response_ms        integer,
    answered_at        timestamptz NOT NULL DEFAULT now(),
    UNIQUE (attempt_id, question_id)
);

CREATE INDEX idx_attempt_answer_attempt ON attempt_answer (attempt_id);

CREATE TABLE placement_result (
    id                      uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id                 uuid   NOT NULL REFERENCES app_user (id) ON DELETE CASCADE,
    test_attempt_id         uuid   NOT NULL REFERENCES test_attempt (id) ON DELETE CASCADE,
    estimated_level         text   NOT NULL CHECK (estimated_level IN ('A1', 'A2', 'B1')),
    skill_scores            jsonb  NOT NULL DEFAULT '{}'::jsonb,
    mastered_category_codes text[] NOT NULL DEFAULT '{}',
    created_at              timestamptz NOT NULL DEFAULT now()
);

CREATE INDEX idx_placement_result_user ON placement_result (user_id, created_at DESC);

-- ---------------------------------------------------------------------
-- 4. KULLANICININ PROGRAMI
-- ---------------------------------------------------------------------

CREATE TABLE plan (
    id               uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id          uuid    NOT NULL REFERENCES app_user (id) ON DELETE CASCADE,
    learning_goal_id uuid    NOT NULL REFERENCES learning_goal (id) ON DELETE CASCADE,
    start_date       date    NOT NULL,
    end_date         date    NOT NULL,
    total_days       integer NOT NULL CHECK (total_days > 0),
    daily_minutes    integer NOT NULL CHECK (daily_minutes > 0),
    status           text    NOT NULL DEFAULT 'ACTIVE'
                             CHECK (status IN ('ACTIVE', 'COMPLETED', 'RECALCULATED')),
    feasibility      text    NOT NULL CHECK (feasibility IN ('OK', 'TIGHT', 'UNREALISTIC')),
    version          integer NOT NULL DEFAULT 1,
    generated_at     timestamptz NOT NULL DEFAULT now(),
    CONSTRAINT chk_plan_date_order CHECK (end_date >= start_date)
);

-- Kullanici basina yalnizca bir ACTIVE plan.
CREATE UNIQUE INDEX uq_plan_one_active_per_user
    ON plan (user_id) WHERE status = 'ACTIVE';

CREATE TABLE plan_day (
    id              uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    plan_id         uuid    NOT NULL REFERENCES plan (id) ON DELETE CASCADE,
    day_number      integer NOT NULL CHECK (day_number > 0),
    date            date    NOT NULL,
    content_unit_id uuid    REFERENCES content_unit (id) ON DELETE SET NULL,
    day_type        text    NOT NULL CHECK (day_type IN (
                        'NORMAL', 'LIGHT', 'TEST', 'MOCK_EXAM')),
    planned_minutes integer NOT NULL CHECK (planned_minutes >= 0),
    status          text    NOT NULL DEFAULT 'PENDING'
                            CHECK (status IN ('PENDING', 'IN_PROGRESS', 'DONE', 'MISSED')),
    UNIQUE (plan_id, day_number),
    UNIQUE (plan_id, date)
);

CREATE INDEX idx_plan_day_plan_date ON plan_day (plan_id, date);

ALTER TABLE test_attempt
    ADD CONSTRAINT fk_test_attempt_plan_day
    FOREIGN KEY (plan_day_id) REFERENCES plan_day (id) ON DELETE SET NULL;

CREATE TABLE task (
    id              uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    plan_day_id     uuid     NOT NULL REFERENCES plan_day (id) ON DELETE CASCADE,
    task_type       text     NOT NULL CHECK (task_type IN (
                        'KELIME', 'GRAMER', 'HOEREN', 'LESEN',
                        'SCHREIBEN', 'SPRECHEN', 'SINAV_PRATIGI')),
    planned_minutes integer  NOT NULL CHECK (planned_minutes > 0),
    content_unit_id uuid     REFERENCES content_unit (id) ON DELETE SET NULL,
    order_no        smallint NOT NULL,
    -- Gorevi sitedeki bir kaynaga baglar: TEST -> test.id, VOCAB_SESSION vb.
    ref_type        text     CHECK (ref_type IN ('TEST', 'VOCAB_SESSION', 'QUIZ')),
    ref_id          uuid,
    is_makeup       boolean  NOT NULL DEFAULT false,
    source_task_id  uuid     REFERENCES task (id) ON DELETE SET NULL,
    UNIQUE (plan_day_id, order_no),
    CONSTRAINT chk_task_ref_pairing
        CHECK ((ref_type IS NULL AND ref_id IS NULL)
            OR (ref_type IS NOT NULL AND ref_id IS NOT NULL))
);

CREATE INDEX idx_task_plan_day ON task (plan_day_id);

CREATE TABLE task_completion (
    id           uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    task_id      uuid        NOT NULL UNIQUE REFERENCES task (id) ON DELETE CASCADE,
    user_id      uuid        NOT NULL REFERENCES app_user (id) ON DELETE CASCADE,
    status       text        NOT NULL CHECK (status IN ('DONE', 'PARTIAL', 'MISSED')),
    -- Goreve ozel hazir secenekler. Serbest metin YOK;
    -- gecerli anahtar/degerler backend'de enum olarak dogrulanir.
    self_report  jsonb       NOT NULL DEFAULT '{}'::jsonb,
    auto_score   numeric(5, 2),
    completed_at timestamptz NOT NULL DEFAULT now()
);

CREATE INDEX idx_task_completion_user ON task_completion (user_id, completed_at DESC);

-- ---------------------------------------------------------------------
-- 5. KELIME VE ARALIKLI TEKRAR (SM-2)
-- ---------------------------------------------------------------------

CREATE TABLE user_word (
    id               uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id          uuid    NOT NULL REFERENCES app_user (id) ON DELETE CASCADE,
    word_id          uuid    NOT NULL REFERENCES word (id) ON DELETE CASCADE,
    ease_factor      numeric(4, 2) NOT NULL DEFAULT 2.50 CHECK (ease_factor >= 1.30),
    interval_days    integer NOT NULL DEFAULT 0 CHECK (interval_days >= 0),
    repetition       integer NOT NULL DEFAULT 0 CHECK (repetition >= 0),
    due_date         date,
    last_reviewed_at timestamptz,
    lapses           integer NOT NULL DEFAULT 0 CHECK (lapses >= 0),
    state            text    NOT NULL DEFAULT 'NEW'
                             CHECK (state IN ('NEW', 'LEARNING', 'REVIEW', 'SUSPENDED')),
    created_at       timestamptz NOT NULL DEFAULT now(),
    UNIQUE (user_id, word_id)
);

-- Gunluk tekrar kuyrugunun ana sorgusu: WHERE user_id = ? AND due_date <= today
CREATE INDEX idx_user_word_due ON user_word (user_id, due_date);

CREATE TABLE word_review_log (
    id           bigserial PRIMARY KEY,
    user_word_id uuid     NOT NULL REFERENCES user_word (id) ON DELETE CASCADE,
    direction    text     NOT NULL CHECK (direction IN ('DE_TR', 'TR_DE', 'ARTICLE')),
    quality      smallint NOT NULL CHECK (quality BETWEEN 0 AND 5),
    correct      boolean  NOT NULL,
    response_ms  integer,
    reviewed_at  timestamptz NOT NULL DEFAULT now()
);

CREATE INDEX idx_word_review_log_uw ON word_review_log (user_word_id, reviewed_at DESC);

-- ---------------------------------------------------------------------
-- 6. GELISIM VE ADAPTIF YAPI
-- ---------------------------------------------------------------------

-- Adaptif yapinin motoru: "en zayif 3 konu", "Dativ hata orani",
-- "zayif konulara ek agirlik ver" -- ucu de buradan okunur.
CREATE TABLE user_category_stat (
    user_id             uuid    NOT NULL REFERENCES app_user (id) ON DELETE CASCADE,
    mistake_category_id integer NOT NULL REFERENCES mistake_category (id),
    attempts            integer NOT NULL DEFAULT 0 CHECK (attempts >= 0),
    correct             integer NOT NULL DEFAULT 0 CHECK (correct >= 0),
    last_wrong_at       timestamptz,
    weight              numeric(4, 2) NOT NULL DEFAULT 1.00,
    updated_at          timestamptz NOT NULL DEFAULT now(),
    PRIMARY KEY (user_id, mistake_category_id),
    CONSTRAINT chk_correct_le_attempts CHECK (correct <= attempts)
);

CREATE TABLE skill_estimate (
    id              uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id         uuid NOT NULL REFERENCES app_user (id) ON DELETE CASCADE,
    skill           text NOT NULL CHECK (skill IN ('VOCAB', 'GRAMMAR', 'LESEN', 'HOEREN')),
    level_estimate  text NOT NULL,
    score           numeric(5, 2),
    computed_at     timestamptz NOT NULL DEFAULT now()
);

CREATE INDEX idx_skill_estimate_user ON skill_estimate (user_id, skill, computed_at DESC);

-- Seri (streak) hesabi icin gunluk ozet.
CREATE TABLE user_activity (
    user_id       uuid    NOT NULL REFERENCES app_user (id) ON DELETE CASCADE,
    activity_date date    NOT NULL,
    tasks_done    integer NOT NULL DEFAULT 0 CHECK (tasks_done >= 0),
    minutes       integer NOT NULL DEFAULT 0 CHECK (minutes >= 0),
    PRIMARY KEY (user_id, activity_date)
);

-- ---------------------------------------------------------------------
-- 7. SATIR SEVIYESI GUVENLIK (RLS)
-- ---------------------------------------------------------------------
-- Tum tablolarda RLS acilir ve HICBIR policy tanimlanmaz.
-- Sonuc: anon ve authenticated rolleri hicbir satiri goremez.
-- Backend service_role ile baglanir ve RLS'i bypass eder.
-- Boylece tek giris kapisi API olur, is mantigi tek yerde toplanir.

ALTER TABLE app_user              ENABLE ROW LEVEL SECURITY;
ALTER TABLE learning_goal         ENABLE ROW LEVEL SECURITY;
ALTER TABLE mistake_category      ENABLE ROW LEVEL SECURITY;
ALTER TABLE content_unit          ENABLE ROW LEVEL SECURITY;
ALTER TABLE content_unit_category ENABLE ROW LEVEL SECURITY;
ALTER TABLE content_unit_block    ENABLE ROW LEVEL SECURITY;
ALTER TABLE word                  ENABLE ROW LEVEL SECURITY;
ALTER TABLE content_unit_word     ENABLE ROW LEVEL SECURITY;
ALTER TABLE reading_passage       ENABLE ROW LEVEL SECURITY;
ALTER TABLE question              ENABLE ROW LEVEL SECURITY;
ALTER TABLE question_option       ENABLE ROW LEVEL SECURITY;
ALTER TABLE test                  ENABLE ROW LEVEL SECURITY;
ALTER TABLE test_question         ENABLE ROW LEVEL SECURITY;
ALTER TABLE test_attempt          ENABLE ROW LEVEL SECURITY;
ALTER TABLE attempt_answer        ENABLE ROW LEVEL SECURITY;
ALTER TABLE placement_result      ENABLE ROW LEVEL SECURITY;
ALTER TABLE plan                  ENABLE ROW LEVEL SECURITY;
ALTER TABLE plan_day              ENABLE ROW LEVEL SECURITY;
ALTER TABLE task                  ENABLE ROW LEVEL SECURITY;
ALTER TABLE task_completion       ENABLE ROW LEVEL SECURITY;
ALTER TABLE user_word             ENABLE ROW LEVEL SECURITY;
ALTER TABLE word_review_log       ENABLE ROW LEVEL SECURITY;
ALTER TABLE user_category_stat    ENABLE ROW LEVEL SECURITY;
ALTER TABLE skill_estimate        ENABLE ROW LEVEL SECURITY;
ALTER TABLE user_activity         ENABLE ROW LEVEL SECURITY;
