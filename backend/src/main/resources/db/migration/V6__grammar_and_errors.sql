-- =====================================================================
-- V6 — Gramer ilerlemesi ve hata hafizasi, Faz 3b (SPEC 8.2, 8.4)
--
-- Gramer icerigi (aciklama, ornek, alistirma) JSON dosyalarindadir
-- (K-004); burada yalniz kullanicinin durumu tutulur.
--
-- Tek tek yanitlar zaten learning_answer'da (V5). Burada tutulan sey
-- ETIKET BAZLI ozet: bir hata etiketi kac farkli oturumda tekrarlandi,
-- durumu ne (aktif -> duzeliyor -> cozuldu).
-- =====================================================================

CREATE TABLE grammar_progress (
    user_id      uuid NOT NULL REFERENCES app_user (id) ON DELETE CASCADE,
    topic_id     text NOT NULL,
    answers      integer NOT NULL DEFAULT 0 CHECK (answers >= 0),
    correct      integer NOT NULL DEFAULT 0 CHECK (correct >= 0),
    last_studied date,
    -- Konu dongusu tamamlandiginda kontrol tekrarlari baslar (Ek A: 7, 30 gun).
    completed_on date,
    check_index  integer NOT NULL DEFAULT 0 CHECK (check_index >= 0),
    next_check   date,
    created_at   timestamptz NOT NULL DEFAULT now(),
    updated_at   timestamptz NOT NULL DEFAULT now(),
    PRIMARY KEY (user_id, topic_id),
    CHECK (correct <= answers)
);
CREATE INDEX idx_grammar_check ON grammar_progress (user_id, next_check);

CREATE TABLE error_record (
    user_id           uuid NOT NULL REFERENCES app_user (id) ON DELETE CASCADE,
    tag               text NOT NULL,
    status            text NOT NULL DEFAULT 'ACTIVE'
                          CHECK (status IN ('ACTIVE', 'IMPROVING', 'RESOLVED')),
    occurrences       integer NOT NULL DEFAULT 0 CHECK (occurrences >= 0),
    distinct_sessions integer NOT NULL DEFAULT 0 CHECK (distinct_sessions >= 0),
    correct_streak    integer NOT NULL DEFAULT 0 CHECK (correct_streak >= 0),
    last_session_id   uuid REFERENCES study_session (id) ON DELETE SET NULL,
    first_seen        date NOT NULL,
    last_seen         date NOT NULL,
    resolved_on       date,
    updated_at        timestamptz NOT NULL DEFAULT now(),
    PRIMARY KEY (user_id, tag)
);
CREATE INDEX idx_error_status ON error_record (user_id, status);

ALTER TABLE grammar_progress ENABLE ROW LEVEL SECURITY;
ALTER TABLE error_record     ENABLE ROW LEVEL SECURITY;
