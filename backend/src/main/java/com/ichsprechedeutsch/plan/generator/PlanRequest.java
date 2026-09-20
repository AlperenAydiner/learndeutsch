package com.ichsprechedeutsch.plan.generator;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Program ureticinin tum girdisi.
 *
 * Uretici baska hicbir yerden veri okumaz: ne veritabani, ne saat, ne
 * rastgelelik. Ayni girdi her zaman ayni plani uretir. Bu, ureticiyi
 * test edilebilir kilan sey.
 *
 * @param masteredCategories yerlestirmede biliniyor cikan kategoriler;
 *                           tum kategorileri burada olan birim atlanir
 * @param partialCategories  yari bilinen kategoriler; birimin suresi yariya iner
 */
public record PlanRequest(
        String targetLevel,
        String examType,
        int totalDays,
        int dailyMinutes,
        LocalDate startDate,
        List<UnitSpec> units,
        Map<String, List<BlockSpec>> blocksByUnitCode,
        Set<String> masteredCategories,
        Set<String> partialCategories,
        /** En zayif konular, kotuden iyiye sirali (Turkce adlar). */
        List<String> weakTopicsTr) {

    public boolean hasExam() {
        return examType != null && !"NONE".equals(examType);
    }
}
