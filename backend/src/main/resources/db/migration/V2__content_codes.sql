-- =====================================================================
-- V2 — soru ve testlere dogal anahtar
-- =====================================================================
-- V1'de question ve test tablolarinin yalnizca uuid birincil anahtari
-- vardi. Seeder'in idempotent calisabilmesi icin CSV'deki satiri
-- veritabanindaki satirla eslestirecek insan tarafindan yazilan bir
-- anahtar gerekiyor; content_unit ve mistake_category'de bunu 'code'
-- sutunu sagliyor. Ayni yaklasimi buraya da tasiyoruz.
--
-- Tablolar su an bos, o yuzden dogrudan NOT NULL eklenebilir.
-- =====================================================================

ALTER TABLE question ADD COLUMN code text NOT NULL;
ALTER TABLE question ADD CONSTRAINT uq_question_code UNIQUE (code);

ALTER TABLE test ADD COLUMN code text NOT NULL;
ALTER TABLE test ADD CONSTRAINT uq_test_code UNIQUE (code);

-- Secenek metni bir soru icinde tekrarlanamaz: ayni siki iki kez
-- yazmak hem anlamsiz hem de seeder'i belirsiz hale getirir.
ALTER TABLE question_option
    ADD CONSTRAINT uq_question_option_text UNIQUE (question_id, option_text);
