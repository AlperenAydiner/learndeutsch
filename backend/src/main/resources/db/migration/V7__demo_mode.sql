-- =====================================================================
-- V7 — Demo modu, Faz 4 (SPEC 12.2)
--
-- Ornek veri ayri ve acikca isaretli bir kullanicida uretilir; gercek
-- kullanici verisine asla yazilmaz. Demo kullanicisinda zaman ileri
-- sarilabilir (X-Demo-Date); baska kullanicilarda bu baslik yok sayilir.
-- =====================================================================

ALTER TABLE app_user ADD COLUMN is_demo boolean NOT NULL DEFAULT false;

-- Demo hesabi elle isaretlenir; uygulama kendiliginde demo hesabi acmaz.
COMMENT ON COLUMN app_user.is_demo IS
    'true ise bu hesap demo verisi tutar: arayuzde DEMO etiketi gorunur ve X-Demo-Date kabul edilir';
