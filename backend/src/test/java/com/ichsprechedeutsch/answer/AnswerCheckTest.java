package com.ichsprechedeutsch.answer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.ichsprechedeutsch.answer.AnswerCheck.Result;
import com.ichsprechedeutsch.answer.AnswerCheck.Warning;
import com.ichsprechedeutsch.config.AnswerProperties;
import com.ichsprechedeutsch.config.AnswerProperties.Mode;
import com.ichsprechedeutsch.config.TestConfig;
import java.util.List;
import java.util.Locale;
import org.junit.jupiter.api.Test;

/** SPEC 8.3 — cevap kontrolu. */
class AnswerCheckTest {

    private static final AnswerProperties VARSAYILAN = TestConfig.answer();
    private static final AnswerProperties KATI = new AnswerProperties(Mode.STRICT, Mode.STRICT);

    private static Result kontrol(String given, String expected) {
        return AnswerCheck.check(given, expected, null, VARSAYILAN);
    }

    @Test
    void tamDogruCevapUyariUretmez() {
        Result r = kontrol("der Bahnhof", "der Bahnhof");
        assertTrue(r.flawless());
        assertEquals("der Bahnhof", r.matched());
    }

    @Test
    void fazlaBoslukVeNoktalamaYokSayilir() {
        assertTrue(kontrol("  der   Bahnhof .", "der Bahnhof").flawless());
        assertTrue(kontrol("Wie geht's?", "Wie geht's").flawless());
    }

    @Test
    void buyukKucukHarfKabulEdilirAmaUyarir() {
        Result r = kontrol("bahnhof", "Bahnhof");
        assertTrue(r.correct());
        assertEquals(List.of(Warning.BUYUK_KUCUK_HARF), r.warnings());
        assertFalse(r.flawless());
    }

    @Test
    void umlautYazimiKabulEdilirAmaUyarir() {
        Result r = kontrol("Strasse", "Straße");
        assertTrue(r.correct());
        assertEquals(List.of(Warning.UMLAUT_YAZIMI), r.warnings());

        assertEquals(List.of(Warning.UMLAUT_YAZIMI), kontrol("Tuer", "Tür").warnings());
        assertEquals(List.of(Warning.UMLAUT_YAZIMI), kontrol("schoen", "schön").warnings());
    }

    @Test
    void ikiKusurBirlikteIkiUyariVerir() {
        Result r = kontrol("strasse", "Straße");
        assertTrue(r.correct());
        assertEquals(List.of(Warning.BUYUK_KUCUK_HARF, Warning.UMLAUT_YAZIMI), r.warnings());
    }

    @Test
    void katiModdaKusurluYazimKabulEdilmez() {
        assertFalse(AnswerCheck.check("bahnhof", "Bahnhof", null, KATI).correct());
        assertFalse(AnswerCheck.check("Strasse", "Straße", null, KATI).correct());
        assertTrue(AnswerCheck.check("Straße", "Straße", null, KATI).flawless());
    }

    @Test
    void kabulEdilenAlternatifCevaplarDaDogrudur() {
        Result r = AnswerCheck.check("Auto", "Wagen", List.of("Auto", "PKW"), VARSAYILAN);
        assertTrue(r.flawless());
        assertEquals("Auto", r.matched());
    }

    @Test
    void kusursuzEslesmeUyarililariYener() {
        // "tür" hem "Tür"un kucuk harflisi hem de baska bir kabul edilen cevap.
        Result r = AnswerCheck.check("tür", "Tür", List.of("tür"), VARSAYILAN);
        assertTrue(r.flawless(), "tam eslesen alternatif varsa uyari verilmez");
    }

    @Test
    void yanlisCevapYanlistir() {
        assertFalse(kontrol("Zug", "Bahnhof").correct());
        assertFalse(kontrol("", "Bahnhof").correct());
        assertFalse(AnswerCheck.check(null, "Bahnhof", null, VARSAYILAN).correct());
    }

    @Test
    void turkceYereldeBuyukIHarfiBozulmaz() {
        Locale onceki = Locale.getDefault();
        try {
            Locale.setDefault(Locale.of("tr", "TR"));
            // "IHRE" -> Turkce yerelde "ıhre" olurdu; Locale.ROOT ile "ihre".
            assertTrue(kontrol("IHRE", "ihre").correct());
        } finally {
            Locale.setDefault(onceki);
        }
    }
}
