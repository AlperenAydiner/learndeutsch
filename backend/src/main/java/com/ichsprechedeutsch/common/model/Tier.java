package com.ichsprechedeutsch.common.model;

/** Kanit guvenilirlik kademesi (SPEC 4.4). */
public enum Tier {
    LOW, MEDIUM, HIGH;

    /** Bir kademe asagi; LOW'da LOW kalir. */
    public Tier downgrade() {
        return this == LOW ? LOW : values()[ordinal() - 1];
    }
}
