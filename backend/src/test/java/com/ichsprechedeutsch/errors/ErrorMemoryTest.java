package com.ichsprechedeutsch.errors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.ichsprechedeutsch.config.TestConfig;
import com.ichsprechedeutsch.errors.ErrorMemory.State;
import com.ichsprechedeutsch.errors.ErrorMemory.Status;
import com.ichsprechedeutsch.grammar.GrammarCheck;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

/** SPEC 8.4 — hata hafizasi; SPEC 8.2 — gramer kontrol tekrarlari. */
class ErrorMemoryTest {

    private static final LocalDate GUN = LocalDate.of(2026, 4, 1);
    private final ErrorMemory memory = new ErrorMemory(TestConfig.coach());

    private State yanlis(State s, int gunFarki, boolean yeniOturum) {
        return memory.onAnswer(s, false, yeniOturum, GUN.plusDays(gunFarki));
    }

    private State dogru(State s, int gunFarki) {
        return memory.onAnswer(s, true, false, GUN.plusDays(gunFarki));
    }

    @Test
    void ilkHataEtiketiAktifYapar() {
        State s = yanlis(State.first("DATIV", GUN), 0, true);
        assertEquals(Status.ACTIVE, s.status());
        assertEquals(1, s.occurrences());
        assertEquals(1, s.distinctSessions());
        assertEquals(0, s.correctStreak());
        assertNull(s.resolvedOn());
    }

    @Test
    void ayniOturumdakiIkinciHataFarkliOturumSaymaz() {
        State s = yanlis(State.first("DATIV", GUN), 0, true);
        s = yanlis(s, 0, false);
        assertEquals(2, s.occurrences());
        assertEquals(1, s.distinctSessions(), "ayni oturum");
    }

    @Test
    void dogruGelmeyeBaslayincaDuzeliyor() {
        State s = yanlis(State.first("DATIV", GUN), 0, true);
        s = dogru(s, 1);
        assertEquals(Status.IMPROVING, s.status());
        assertEquals(1, s.correctStreak());
    }

    @Test
    void ustUsteUcDogruCozer() {
        State s = yanlis(State.first("DATIV", GUN), 0, true);
        s = dogru(s, 1);
        s = dogru(s, 2);
        assertEquals(Status.IMPROVING, s.status(), "iki dogru yetmez");
        s = dogru(s, 3);
        assertEquals(Status.RESOLVED, s.status());
        assertEquals(GUN.plusDays(3), s.resolvedOn());
    }

    @Test
    void arayaGirenHataSayaciSifirlar() {
        State s = yanlis(State.first("DATIV", GUN), 0, true);
        s = dogru(s, 1);
        s = dogru(s, 2);
        s = yanlis(s, 3, true);
        assertEquals(Status.ACTIVE, s.status());
        assertEquals(0, s.correctStreak());
        s = dogru(s, 4);
        s = dogru(s, 5);
        assertEquals(Status.IMPROVING, s.status(), "sayac bastan basladi");
    }

    @Test
    void cozulenEtiketYenidenHataliOlursaAktifeDoner() {
        State s = yanlis(State.first("DATIV", GUN), 0, true);
        s = dogru(s, 1);
        s = dogru(s, 2);
        s = dogru(s, 3);
        assertEquals(Status.RESOLVED, s.status());

        s = yanlis(s, 10, true);
        assertEquals(Status.ACTIVE, s.status());
        assertNull(s.resolvedOn(), "cozulme tarihi silinir");
        assertEquals(2, s.distinctSessions());
    }

    @Test
    void tekrarlayanHataUcFarkliOturumdaOlusur() {
        State s = yanlis(State.first("DATIV", GUN), 0, true);
        assertFalse(memory.recurring(s));
        s = yanlis(s, 1, true);
        assertFalse(memory.recurring(s), "iki oturum yetmez");
        s = yanlis(s, 2, true);
        assertTrue(memory.recurring(s));
    }

    @Test
    void cozulenEtiketTekrarlayanSayilmaz() {
        State s = yanlis(State.first("DATIV", GUN), 0, true);
        s = yanlis(s, 1, true);
        s = yanlis(s, 2, true);
        s = dogru(s, 3);
        s = dogru(s, 4);
        s = dogru(s, 5);
        assertEquals(Status.RESOLVED, s.status());
        assertFalse(memory.recurring(s), "cozulen hata koc onerisine girmez");
    }

    // ---------- gramer kontrol tekrarlari ----------

    @Test
    void kontrolTekrarlariConfigAraliklariylaPlanlanir() {
        List<Integer> araliklar = TestConfig.coach().grammarCheckDays();
        assertEquals(List.of(7, 30), araliklar);

        assertEquals(Optional.of(GUN.plusDays(7)), GrammarCheck.next(GUN, 0, araliklar));
        assertEquals(Optional.of(GUN.plusDays(30)), GrammarCheck.next(GUN, 1, araliklar));
        assertTrue(GrammarCheck.next(GUN, 2, araliklar).isEmpty(), "sira bitti");
        assertTrue(GrammarCheck.next(null, 0, araliklar).isEmpty(), "tamamlanmamis konu");
    }

    @Test
    void kontrolGunuGelinceTekrarBekler() {
        assertFalse(GrammarCheck.due(GUN.plusDays(7), GUN));
        assertTrue(GrammarCheck.due(GUN.plusDays(7), GUN.plusDays(7)));
        assertTrue(GrammarCheck.due(GUN.plusDays(7), GUN.plusDays(9)), "gecikmis kontrol");
        assertFalse(GrammarCheck.due(null, GUN));
    }
}
