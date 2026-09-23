package com.ichsprechedeutsch.level;

import com.ichsprechedeutsch.common.model.ResultSource;
import com.ichsprechedeutsch.common.model.Skill;
import com.ichsprechedeutsch.common.model.Tier;
import com.ichsprechedeutsch.config.LevelProperties;

/**
 * Kanit kaynagindan guvenilirlik kademesi (SPEC 4.4).
 *
 * <ul>
 *   <li>Temel eslesme config'de (level.source-tiers).</li>
 *   <li>Schreiben/Sprechen modelltesti kullanicinin kendi puanladigi test
 *       oldugu icin ayri kademe alir (varsayilan dusuk).</li>
 *   <li>verified:false icerikten gelen kanit bir kademe dusurulur
 *       (config'den kapatilabilir; K-008).</li>
 * </ul>
 */
public final class TierPolicy {

    private final LevelProperties config;

    public TierPolicy(LevelProperties config) {
        this.config = config;
    }

    public Tier tierOf(Evidence e) {
        Tier tier;
        boolean productive = e.skill() == Skill.SCHREIBEN || e.skill() == Skill.SPRECHEN;
        if (productive && e.source() == ResultSource.MODELLTEST) {
            tier = config.selfScoredProductiveModelltestTier();
        } else {
            tier = config.sourceTiers().get(e.source());
        }
        if (config.unverifiedDowngrade() && Boolean.FALSE.equals(e.contentVerified())) {
            tier = tier.downgrade();
        }
        return tier;
    }

    public double weightOf(Evidence e) {
        return config.weight(tierOf(e));
    }

    public boolean isPositive(Evidence e) {
        return e.ratio() >= config.passThreshold();
    }
}
