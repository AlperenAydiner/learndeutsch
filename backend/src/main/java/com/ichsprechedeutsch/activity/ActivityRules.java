package com.ichsprechedeutsch.activity;

import com.ichsprechedeutsch.common.model.Level;
import com.ichsprechedeutsch.common.model.ResultSource;
import com.ichsprechedeutsch.common.model.Skill;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

/** Aktivite kaydinin saf kurallari (SPEC 6.1, 6.3). */
public final class ActivityRules {

    private ActivityRules() {
    }

    /** Bir kaydin kanit uretebilmesi icin gereken alanlar. */
    public record Candidate(ActivityType type, Origin origin, Level level, Double score, Double maxScore,
                            ResultSource resultSource, LocalDate date, Boolean contentVerified) {
    }

    /** Kaniti olusturacak degerler; kademe okunurken config'den hesaplanir. */
    public record EvidenceDraft(Skill skill, Level level, ResultSource source, double score,
                                double maxScore, LocalDate date, Boolean contentVerified) {
    }

    public enum Origin { SITE, EXTERNAL }

    /**
     * Kayit ne zaman kanit olur (SPEC 6.3):
     * <ul>
     *   <li>Yalniz dort beceride (gramer/kelime sonucu kanit olmaz, K2),</li>
     *   <li>seviye dolu ve sonuc (puan / azami puan) girilmisse,</li>
     *   <li>dis kayitta sonucun nereden geldigi belirtilmisse (kademe buradan).</li>
     * </ul>
     * Site ici Hören (Web Speech sentetik ses) ogrenme aktivitesidir, kanit degildir (4.2).
     */
    public static Optional<EvidenceDraft> evidenceFrom(Candidate c) {
        Optional<Skill> skill = c.type().skill();
        if (skill.isEmpty() || c.level() == null || c.level() == Level.A0
                || c.score() == null || c.maxScore() == null || c.maxScore() <= 0) {
            return Optional.empty();
        }
        if (c.origin() == Origin.SITE && skill.get() == Skill.HOEREN) {
            return Optional.empty();
        }
        ResultSource source = c.origin() == Origin.SITE ? ResultSource.SITE_TEST : c.resultSource();
        if (source == null) {
            return Optional.empty();
        }
        Boolean verified = c.origin() == Origin.SITE ? c.contentVerified() : null;
        return Optional.of(new EvidenceDraft(skill.get(), c.level(), source, c.score(), c.maxScore(),
                c.date(), verified));
    }

    /**
     * Olculen aktif sure (SPEC 6.1): ardisik etkilesimler arasindaki bosluk
     * esikten uzunsa o aralik sayilmaz.
     *
     * @param interactions zaman sirasina gore etkilesim anlari
     */
    public static long activeSeconds(List<Instant> interactions, int idleGapMinutes) {
        Duration limit = Duration.ofMinutes(idleGapMinutes);
        long total = 0;
        for (int i = 1; i < interactions.size(); i++) {
            Duration gap = Duration.between(interactions.get(i - 1), interactions.get(i));
            if (!gap.isNegative() && gap.compareTo(limit) <= 0) {
                total += gap.getSeconds();
            }
        }
        return total;
    }
}
