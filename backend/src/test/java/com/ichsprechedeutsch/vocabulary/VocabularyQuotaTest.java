package com.ichsprechedeutsch.vocabulary;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class VocabularyQuotaTest {

    private final VocabularyQuota quota = new VocabularyQuota();

    @Test
    @DisplayName("Tekrar yoksa blok suresi kadar yeni kelime verilir")
    void noReviewsMeansFullQuota() {
        // 60 dk = 3600 sn; 3600 / 25 = 144, ust sinir 40.
        VocabularyQuota.Quota result = quota.calculate(60, 0, 500);

        assertEquals(VocabularyQuota.MAX_NEW_PER_DAY, result.newWords());
        assertEquals(0, result.dueReviews());
    }

    @Test
    @DisplayName("Tekrar yuku arttikca yeni kelime sayisi duser")
    void reviewsReduceNewWords() {
        VocabularyQuota.Quota few = quota.calculate(20, 10, 500);
        VocabularyQuota.Quota many = quota.calculate(20, 100, 500);

        assertTrue(many.newWords() < few.newWords(),
                "tekrar arttikca yeni kelime azalmali: " + few.newWords()
                        + " -> " + many.newWords());
    }

    @Test
    @DisplayName("Tekrarlar blogu doldurunca yeni kelime verilmez")
    void reviewsCanFillTheBlock() {
        // 20 dk = 1200 sn; 200 tekrar x 8 sn = 1600 sn > 1200.
        VocabularyQuota.Quota result = quota.calculate(20, 200, 500);

        assertEquals(0, result.newWords());
        assertEquals(200, result.dueReviews());
        assertTrue(result.reasonTr().contains("tekrarin var"), result.reasonTr());
    }

    @Test
    @DisplayName("Havuzda kelime kalmadiysa kota sifir ve sebebi soylenir")
    void emptyPool() {
        VocabularyQuota.Quota result = quota.calculate(60, 0, 0);

        assertEquals(0, result.newWords());
        assertTrue(result.reasonTr().contains("hepsini gordun"), result.reasonTr());
    }

    @Test
    @DisplayName("Havuzdaki kelime sayisi kotayi sinirlar")
    void poolLimitsQuota() {
        VocabularyQuota.Quota result = quota.calculate(60, 0, 7);

        assertEquals(7, result.newWords());
    }

    @Test
    @DisplayName("Blok yoksa yeni kelime de yok")
    void noBlockNoWords() {
        assertEquals(0, quota.calculate(0, 0, 500).newWords());
    }
}
