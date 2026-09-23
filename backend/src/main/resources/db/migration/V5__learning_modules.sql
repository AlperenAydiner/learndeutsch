-- =====================================================================
-- V5 — Ogrenme modulleri, Faz 3a (SPEC 8.1, 8.3, 8.5)
--
-- Kelime tekrari (SRS) ve artikel pratigi kullanici durumudur; icerik
-- (kelimenin kendisi) yine JSON dosyalarindadir (K-004), buraya yalniz
-- kararli kelime id'si yazilir.
--
-- Ogrenme testleri seviye kaniti DEGILDIR (K2, SPEC 8.5): bu tablolarin
-- hicbiri skill_evidence'a baglanmaz.
-- =====================================================================

-- --- Kelime SRS durumu -----------------------------------------------

CREATE TABLE vocabulary_progress (
    user_id         uuid NOT NULL REFERENCES app_user (id) ON DELETE CASCADE,
    word_id         text NOT NULL,
    step            integer NOT NULL CHECK (step >= 1),
    due_date        date NOT NULL,
    last_grade      text CHECK (last_grade IN ('BILEMEDIM', 'ZORLANDIM', 'BILDIM')),
    reviews         integer NOT NULL DEFAULT 0 CHECK (reviews >= 0),
    lapses          integer NOT NULL DEFAULT 0 CHECK (lapses >= 0),
    first_seen      date NOT NULL,
    last_seen       date NOT NULL,
    created_at      timestamptz NOT NULL DEFAULT now(),
    updated_at      timestamptz NOT NULL DEFAULT now(),
    PRIMARY KEY (user_id, word_id)
);
CREATE INDEX idx_vocabulary_due ON vocabulary_progress (user_id, due_date);

-- Artikel istatistigi burada tutulmaz: artikel pratigi SRS durumunu
-- degistirmez (SPEC 8.1), sonuclari learning_answer'dan turetilir.

-- --- Ogrenme cevaplari: son N yanit pencereleri (SPEC 5.2) -----------
-- kind: WORD (anlam), ARTICLE (der/die/das), GRAMMAR (Faz 3b).
-- tag: artikel icin der/die/das, gramer icin konu id'si.

CREATE TABLE learning_answer (
    id          uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id     uuid NOT NULL REFERENCES app_user (id) ON DELETE CASCADE,
    session_id  uuid REFERENCES study_session (id) ON DELETE SET NULL,
    kind        text NOT NULL CHECK (kind IN ('WORD', 'ARTICLE', 'GRAMMAR')),
    item_id     text NOT NULL,
    tag         text,
    correct     boolean NOT NULL,
    given       text,
    answer_date date NOT NULL,
    created_at  timestamptz NOT NULL DEFAULT now()
);
CREATE INDEX idx_learning_answer_window ON learning_answer (user_id, kind, created_at DESC);

ALTER TABLE vocabulary_progress ENABLE ROW LEVEL SECURITY;
ALTER TABLE learning_answer     ENABLE ROW LEVEL SECURITY;
