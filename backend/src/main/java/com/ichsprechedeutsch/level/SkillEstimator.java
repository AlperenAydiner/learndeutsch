package com.ichsprechedeutsch.level;

import com.ichsprechedeutsch.common.model.Level;
import com.ichsprechedeutsch.common.model.Skill;
import com.ichsprechedeutsch.common.model.Tier;
import com.ichsprechedeutsch.config.LevelProperties;
import com.ichsprechedeutsch.level.SkillEstimate.Confidence;
import com.ichsprechedeutsch.level.SkillEstimate.Kind;
import java.time.LocalDate;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;

/**
 * Kanittan beceri tahmini (SPEC 4.3) ve guven durumu (4.4). Saf: veritabani
 * ve sistem saati okumaz; bugunun tarihi parametredir.
 *
 * <p>Yontem, aciklanabilir olsun diye agirlikli oylamadir:
 * <ul>
 *   <li>Gecme esigini gecen kanit kendi seviyesini ve alttakileri destekler.</li>
 *   <li>Gecemeyen kanit kendi seviyesine ve usttekilere karsi sayilir.</li>
 *   <li>Tahmin: destek agirligi karsi agirliktan buyuk olan en yuksek seviye.</li>
 * </ul>
 */
public final class SkillEstimator {

    private final LevelProperties config;
    private final TierPolicy tiers;

    public SkillEstimator(LevelProperties config) {
        this.config = config;
        this.tiers = new TierPolicy(config);
    }

    public SkillEstimate estimate(Skill skill, Collection<Evidence> all, LocalDate today) {
        LocalDate oldest = today.minusMonths(config.maxEvidenceAgeMonths());
        List<Evidence> window = all.stream()
                .filter(e -> e.skill() == skill)
                .filter(e -> !e.date().isBefore(oldest) && !e.date().isAfter(today))
                .sorted(Comparator.comparing(Evidence::date).reversed())
                .toList();

        if (window.isEmpty()) {
            return SkillEstimate.none(skill);
        }

        Level best = null;
        for (Level l : Level.assessable()) {
            if (support(window, l) > against(window, l)) {
                best = l;
            }
        }

        if (best == null) {
            // Hicbir seviye desteklenmiyor: basarisiz olunan en dusuk seviyenin altinda.
            Level lowestFailed = window.stream()
                    .filter(e -> !tiers.isPositive(e))
                    .map(Evidence::level)
                    .min(Comparator.naturalOrder())
                    .orElseThrow();
            return new SkillEstimate(skill, Kind.BELOW, lowestFailed, Confidence.LOW,
                    window.size(), window);
        }

        Level estimate = best;
        boolean triedAbove = estimate == Level.C1
                || window.stream().anyMatch(e -> e.level().compareTo(estimate) > 0);
        Kind kind = triedAbove ? Kind.AT : Kind.AT_LEAST;

        return new SkillEstimate(skill, kind, estimate, confidence(window, estimate, today),
                window.size(), window);
    }

    private double support(List<Evidence> window, Level level) {
        return window.stream()
                .filter(tiers::isPositive)
                .filter(e -> e.level().isAtLeast(level))
                .mapToDouble(tiers::weightOf)
                .sum();
    }

    private double against(List<Evidence> window, Level level) {
        return window.stream()
                .filter(e -> !tiers.isPositive(e))
                .filter(e -> level.isAtLeast(e.level()))
                .mapToDouble(tiers::weightOf)
                .sum();
    }

    /** "Guvenilir": yeterli pozitif kanit, en az biri guncel, en az biri orta/yuksek kademe. */
    private Confidence confidence(List<Evidence> window, Level estimate, LocalDate today) {
        LevelProperties.Reliable r = config.reliable();
        List<Evidence> supporting = window.stream()
                .filter(tiers::isPositive)
                .filter(e -> e.level().isAtLeast(estimate))
                .toList();
        LocalDate recent = today.minusDays(r.recentDays());

        boolean enough = supporting.size() >= r.minPositive();
        boolean current = supporting.stream().anyMatch(e -> !e.date().isBefore(recent));
        boolean strong = !r.requireMediumOrHigh()
                || supporting.stream().anyMatch(e -> tiers.tierOf(e) != Tier.LOW);

        return enough && current && strong ? Confidence.RELIABLE : Confidence.LOW;
    }

    public TierPolicy tiers() {
        return tiers;
    }
}
