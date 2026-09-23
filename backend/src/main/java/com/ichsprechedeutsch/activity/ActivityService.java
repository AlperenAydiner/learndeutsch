package com.ichsprechedeutsch.activity;

import com.ichsprechedeutsch.activity.ActivityRules.Candidate;
import com.ichsprechedeutsch.activity.ActivityRules.Origin;
import com.ichsprechedeutsch.activity.ActivityStore.Activity;
import com.ichsprechedeutsch.activity.api.ActivityRequest;
import com.ichsprechedeutsch.common.error.NotFoundException;
import com.ichsprechedeutsch.common.error.ValidationException;
import com.ichsprechedeutsch.common.model.Level;
import com.ichsprechedeutsch.config.ActivityProperties;
import com.ichsprechedeutsch.content.ContentCatalog;
import com.ichsprechedeutsch.content.GrammarTopic;
import com.ichsprechedeutsch.level.LevelStore;
import java.time.LocalDate;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Aktivite kaydi (SPEC Bolum 6). Dis calismalar "Bugun ne yaptin?"
 * formundan, site ici calismalar otomatik gelir. Kayit kanit sartlarini
 * saglarsa (6.3) beceri kaniti da uretir; kayit duzenlenince kanit yeniden
 * hesaplanir, silinince kanit da silinir.
 */
@Service
public class ActivityService {

    static final Set<String> SOURCE_KINDS =
            Set.of("YOUTUBE", "BOOK", "PODCAST", "COURSE", "TEACHER", "AI", "OTHER_APP", "SELF");
    static final Set<String> SPEAKING_PARTNERS = Set.of("TEACHER", "FRIEND", "AI", "ALONE");

    /** Site ici otomatik kayit icin girdi. */
    public record SiteActivity(ActivityType type, LocalDate date, long activeSeconds, Level level,
                               Double score, Double maxScore, String contentId, Boolean contentVerified,
                               UUID studySessionId) {
    }

    private final ActivityStore store;
    private final LevelStore levels;
    private final ContentCatalog catalog;
    private final ActivityProperties config;

    public ActivityService(ActivityStore store, LevelStore levels, ContentCatalog catalog,
                           ActivityProperties config) {
        this.store = store;
        this.levels = levels;
        this.catalog = catalog;
        this.config = config;
    }

    @Transactional(readOnly = true)
    public List<Activity> between(UUID userId, LocalDate from, LocalDate to) {
        if (to.isBefore(from)) {
            throw new ValidationException("Bitiş tarihi başlangıçtan önce olamaz");
        }
        return store.between(userId, from, to);
    }

    @Transactional
    public Activity createExternal(UUID userId, ActivityRequest req, LocalDate today) {
        Activity a = validate(req, today);
        UUID id = store.insert(userId, a);
        writeEvidence(userId, id, a);
        return store.find(userId, id).orElseThrow();
    }

    @Transactional
    public Activity update(UUID userId, UUID id, ActivityRequest req, LocalDate today) {
        Activity existing = store.find(userId, id)
                .orElseThrow(() -> new NotFoundException("Kayıt bulunamadı"));
        if (existing.origin() == Origin.SITE) {
            throw new ValidationException("Sitede otomatik kaydedilen çalışma düzenlenemez, yalnız silinebilir");
        }
        Activity a = validate(req, today);
        store.update(userId, id, a);
        levels.deleteEvidenceOfActivity(userId, id);
        writeEvidence(userId, id, a);
        return store.find(userId, id).orElseThrow();
    }

    @Transactional
    public void delete(UUID userId, UUID id) {
        if (!store.delete(userId, id)) {
            throw new NotFoundException("Kayıt bulunamadı");
        }
    }

    /** Site ici tamamlanan calismayi otomatik yazar (SPEC 6.1). */
    @Transactional
    public UUID recordSite(UUID userId, SiteActivity s) {
        int minutes = (int) Math.round(s.activeSeconds() / 60.0);
        Activity a = new Activity(null, s.date(), s.type(), minutes, Origin.SITE, null, s.level(),
                s.score(), s.maxScore(), null, null, null, null, null, s.contentId(), s.contentVerified(),
                s.studySessionId());
        UUID id = store.insert(userId, a);
        writeEvidence(userId, id, a);
        return id;
    }

    // ------------------------------------------------------------------

    private void writeEvidence(UUID userId, UUID activityId, Activity a) {
        ActivityRules.evidenceFrom(new Candidate(a.type(), a.origin(), a.level(), a.score(), a.maxScore(),
                        a.resultSource(), a.date(), a.contentVerified()))
                .ifPresent(e -> levels.insertEvidence(userId, activityId, e.skill(), e.level(), e.source(),
                        e.score(), e.maxScore(), e.date(), a.contentId(), e.contentVerified()));
    }

    private Activity validate(ActivityRequest r, LocalDate today) {
        if (r.date() == null || r.type() == null) {
            throw new ValidationException("Tarih ve çalışma türü gerekli");
        }
        if (r.date().isAfter(today)) {
            throw new ValidationException("İleri bir tarihe kayıt girilemez");
        }
        if (!r.type().userSelectable()) {
            throw new ValidationException("Bu tür yalnız site içinde otomatik kaydedilir");
        }
        if (r.durationMinutes() == null || r.durationMinutes() < 0 || r.durationMinutes() > 1440) {
            throw new ValidationException("Süre 0 ile 1440 dakika arasında olmalı");
        }
        if (r.sourceKind() == null || !SOURCE_KINDS.contains(r.sourceKind())) {
            throw new ValidationException("Kaynak türünü seç");
        }

        boolean hasScore = r.score() != null || r.maxScore() != null;
        if (hasScore) {
            if (r.score() == null || r.maxScore() == null || r.maxScore() <= 0
                    || r.score() < 0 || r.score() > r.maxScore()) {
                throw new ValidationException("Sonuç puan / azami puan olarak girilmeli (ör. 8 / 10)");
            }
            if (r.resultSource() == null) {
                throw new ValidationException("Sonucun nereden geldiğini seç");
            }
        } else if (r.resultSource() != null) {
            throw new ValidationException("Sonuç girilmeden kaynağı seçilemez");
        }

        String partner = null;
        String topic = null;
        if (r.type() == ActivityType.SPRECHEN) {
            if (r.speakingPartner() != null && !SPEAKING_PARTNERS.contains(r.speakingPartner())) {
                throw new ValidationException("Konuşma ortağı geçersiz");
            }
            partner = r.speakingPartner();
            topic = trimToNull(r.speakingTopic(), 120, "Konu");
        }

        String grammarTopic = null;
        if (r.type() == ActivityType.GRAMER && r.grammarTopicId() != null) {
            GrammarTopic t = catalog.topics().get(r.grammarTopicId());
            if (t == null || !"GRAMMAR".equals(t.kind())) {
                throw new ValidationException("Gramer konusu bulunamadı");
            }
            grammarTopic = t.id();
        }

        String note = trimToNull(r.note(), config.maxNoteLength(), "Not");

        return new Activity(null, r.date(), r.type(), r.durationMinutes(), Origin.EXTERNAL, r.sourceKind(),
                r.level(), hasScore ? r.score() : null, hasScore ? r.maxScore() : null,
                hasScore ? r.resultSource() : null, partner, topic, grammarTopic, note, null, null, null);
    }

    private static String trimToNull(String s, int max, String field) {
        if (s == null || s.isBlank()) {
            return null;
        }
        String t = s.trim();
        if (t.length() > max) {
            throw new ValidationException(field + " en fazla " + max + " karakter olabilir");
        }
        return t;
    }
}
