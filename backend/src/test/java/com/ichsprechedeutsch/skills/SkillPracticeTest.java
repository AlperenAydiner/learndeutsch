package com.ichsprechedeutsch.skills;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.ichsprechedeutsch.common.error.ValidationException;
import com.ichsprechedeutsch.config.LevelProperties;
import com.ichsprechedeutsch.config.LevelProperties.CanDoAnswer;
import com.ichsprechedeutsch.config.TestConfig;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/** SPEC 8.3 dikte, 4.2 rubrik ve Kann-Beschreibung puanlamasi. */
class SkillPracticeTest {

    private static final LevelProperties CONFIG = TestConfig.level();
    private static final String METIN = "Der Zug nach München fährt um acht Uhr ab.";

    // ---------- dikte ----------

    @Test
    void tamDogruDikteTamPuan() {
        DictationCheck.Result r = DictationCheck.check(METIN, METIN);
        assertEquals(9, r.total());
        assertEquals(9, r.correct());
        assertEquals(1.0, r.ratio());
        assertTrue(r.words().stream().allMatch(w -> "EXACT".equals(w.status())));
    }

    @Test
    void noktalamaVeFazlaBoslukSayilmaz() {
        DictationCheck.Result r = DictationCheck.check(METIN, "  Der Zug nach München fährt um acht Uhr ab  ");
        assertEquals(1.0, r.ratio());
    }

    @Test
    void umlautYaziminaYakinSayilirVePuanVerir() {
        DictationCheck.Result r = DictationCheck.check(METIN, "Der Zug nach Muenchen faehrt um acht Uhr ab.");
        assertEquals(9, r.correct());
        assertEquals(2, r.words().stream().filter(w -> "CLOSE".equals(w.status())).count());
    }

    @Test
    void yanlisVeEksikKelimelerPuaniDusurur() {
        DictationCheck.Result r = DictationCheck.check(METIN, "Der Bus nach München fährt um acht");
        assertEquals(9, r.total());
        assertEquals(6, r.correct(), "Bus yanlis; Uhr ve ab eksik");
        assertEquals(6.0 / 9, r.ratio());
        assertEquals("WRONG", r.words().get(1).status());
        assertEquals("WRONG", r.words().get(8).status(), "eksik kelime yanlis sayilir");
    }

    @Test
    void fazladanYazilanKelimeGosterilirAmaPuaniDusurmez() {
        DictationCheck.Result r = DictationCheck.check("Ich komme aus Izmir.", "Ich komme aus Izmir heute");
        assertEquals(1.0, r.ratio());
        assertEquals("EXTRA", r.words().getLast().status());
    }

    @Test
    void bosCevapSifirPuan() {
        DictationCheck.Result r = DictationCheck.check(METIN, "");
        assertEquals(0, r.correct());
        assertEquals(0.0, r.ratio());
    }

    // ---------- rubrik ----------

    private static Map<String, Integer> rubrik(int... puanlar) {
        List<String> olcutler = WritingRubric.criteria(CONFIG);
        Map<String, Integer> m = new LinkedHashMap<>();
        for (int i = 0; i < olcutler.size(); i++) {
            m.put(olcutler.get(i), puanlar[i]);
        }
        return m;
    }

    @Test
    void rubrikOlcutleriConfigdenGelir() {
        assertEquals(CONFIG.schreibenRubric().criteria(), WritingRubric.criteria(CONFIG).size());
        assertEquals(3, CONFIG.schreibenRubric().maxPerCriterion());
    }

    @Test
    void rubrikPuaniOranaCevrilir() {
        WritingRubric.Result r = WritingRubric.score(rubrik(3, 2, 2, 3), CONFIG);
        assertEquals(10, r.score());
        assertEquals(12, r.maxScore());
        assertEquals(10.0 / 12, r.ratio());
    }

    @Test
    void eksikYaGecersizPuanReddedilir() {
        assertThrows(ValidationException.class, () -> WritingRubric.score(Map.of("Tutarlılık", 2), CONFIG));
        assertThrows(ValidationException.class, () -> WritingRubric.score(rubrik(3, 2, 2, 9), CONFIG));
        assertThrows(ValidationException.class, () -> WritingRubric.score(rubrik(3, 2, 2, -1), CONFIG));
    }

    // ---------- Kann-Beschreibungen ----------

    @Test
    void canDoPuanlariConfigdenOrtalanir() {
        Map<String, CanDoAnswer> cevaplar = new LinkedHashMap<>();
        cevaplar.put("cd-1", CanDoAnswer.YES);
        cevaplar.put("cd-2", CanDoAnswer.PARTIAL);
        cevaplar.put("cd-3", CanDoAnswer.NO);
        cevaplar.put("cd-4", CanDoAnswer.YES);

        CanDoScore.Result r = CanDoScore.of(cevaplar, CONFIG);
        assertEquals(2.5, r.score(), "1 + 0.5 + 0 + 1");
        assertEquals(4, r.maxScore());
        assertEquals(0.625, r.ratio());
        assertEquals(4, r.answered());
    }

    @Test
    void hepsiEvetTamPuanHepsiHayirSifir() {
        Map<String, CanDoAnswer> evet = Map.of("a", CanDoAnswer.YES, "b", CanDoAnswer.YES);
        Map<String, CanDoAnswer> hayir = Map.of("a", CanDoAnswer.NO, "b", CanDoAnswer.NO);
        assertEquals(1.0, CanDoScore.of(evet, CONFIG).ratio());
        assertEquals(0.0, CanDoScore.of(hayir, CONFIG).ratio());
        assertEquals(0.0, CanDoScore.of(Map.of(), CONFIG).ratio(), "veri yoksa sonuc yok (K3)");
    }
}
