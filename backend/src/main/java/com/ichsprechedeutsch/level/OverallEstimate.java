package com.ichsprechedeutsch.level;

import com.ichsprechedeutsch.common.model.Level;
import com.ichsprechedeutsch.common.model.Skill;
import com.ichsprechedeutsch.config.LevelProperties;
import com.ichsprechedeutsch.config.LevelProperties.OverallMethod;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

/**
 * Genel seviye tahmini (SPEC 4.5). "A2 civari (beceriler A1-B1 arasi; en
 * zayif: Sprechen)". Yeterli beceri verisi yoksa sufficient=false: "Henuz
 * yeterli veri yok". Eksik beceriler acikca listelenir.
 *
 * @param level         A0 = "A1'in altinda"
 */
public record OverallEstimate(
        boolean sufficient,
        Level level,
        Level rangeMin,
        Level rangeMax,
        Skill weakest,
        List<Skill> missing,
        OverallMethod method) {

    public static OverallEstimate from(Map<Skill, SkillEstimate> skills, LevelProperties config) {
        List<SkillEstimate> withData = new ArrayList<>();
        List<Skill> missing = new ArrayList<>();
        for (Skill s : Skill.values()) {
            SkillEstimate e = skills.get(s);
            if (e != null && e.hasData()) {
                withData.add(e);
            } else {
                missing.add(s);
            }
        }

        OverallMethod method = config.overall().method();
        if (withData.size() < config.overall().minSkills()) {
            return new OverallEstimate(false, null, null, null, null, List.copyOf(missing), method);
        }

        List<Level> ranks = withData.stream().map(SkillEstimate::rank).sorted().toList();
        Level min = ranks.getFirst();
        Level max = ranks.getLast();
        Level level = method == OverallMethod.WEAKEST
                ? min
                // Medyan, asagi yuvarlanir: cift sayida beceride alttaki orta deger.
                : ranks.get((ranks.size() - 1) / 2);

        Skill weakest = withData.stream()
                .min(Comparator.comparing(SkillEstimate::rank))
                .map(SkillEstimate::skill)
                .orElseThrow();

        return new OverallEstimate(true, level, min, max, weakest, List.copyOf(missing), method);
    }
}
