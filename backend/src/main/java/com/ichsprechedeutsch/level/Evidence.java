package com.ichsprechedeutsch.level;

import com.ichsprechedeutsch.common.model.Level;
import com.ichsprechedeutsch.common.model.ResultSource;
import com.ichsprechedeutsch.common.model.Skill;
import java.time.LocalDate;
import java.util.UUID;

/**
 * Bir beceri kaniti (SPEC 4.2). Kademe saklanmaz; okunurken config'e gore
 * hesaplanir (TierPolicy), boylece config degisince eski kanitlar da yeni
 * kurala uyar.
 *
 * @param contentVerified site ici icerikten geldiyse o icerigin verified
 *                        durumu; dis sonuc icin null
 */
public record Evidence(
        UUID id,
        Skill skill,
        Level level,
        ResultSource source,
        double score,
        double maxScore,
        LocalDate date,
        Boolean contentVerified) {

    public Evidence {
        if (maxScore <= 0 || score < 0 || score > maxScore) {
            throw new IllegalArgumentException("Puan 0 ile azami puan arasinda olmali");
        }
        if (level == Level.A0) {
            throw new IllegalArgumentException("Kanit seviyesi A1-C1 arasinda olmali");
        }
    }

    public double ratio() {
        return score / maxScore;
    }
}
