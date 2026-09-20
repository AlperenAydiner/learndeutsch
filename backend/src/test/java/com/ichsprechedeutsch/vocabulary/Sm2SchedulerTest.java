package com.ichsprechedeutsch.vocabulary;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class Sm2SchedulerTest {

    private final Sm2Scheduler scheduler = new Sm2Scheduler();

    @Test
    @DisplayName("Ilk dogru cevap: bir gun sonra")
    void firstCorrectAnswer() {
        Sm2Scheduler.State state = scheduler.review(Sm2Scheduler.State.fresh(), 4);

        assertEquals(1, state.intervalDays());
        assertEquals(1, state.repetition());
        assertEquals("LEARNING", state.state());
    }

    @Test
    @DisplayName("Ikinci dogru cevap: alti gun sonra")
    void secondCorrectAnswer() {
        Sm2Scheduler.State state = Sm2Scheduler.State.fresh();
        state = scheduler.review(state, 4);
        state = scheduler.review(state, 4);

        assertEquals(6, state.intervalDays());
        assertEquals(2, state.repetition());
    }

    @Test
    @DisplayName("Sonraki tekrarlar katsayiyla carpilarak uzar")
    void intervalsGrow() {
        Sm2Scheduler.State state = Sm2Scheduler.State.fresh();
        int previous = 0;

        for (int i = 0; i < 6; i++) {
            state = scheduler.review(state, 4);
            assertTrue(state.intervalDays() >= previous,
                    "aralik kisalmamali: " + previous + " -> " + state.intervalDays());
            previous = state.intervalDays();
        }

        assertTrue(previous > 30, "alti dogru cevaptan sonra aralik aylara cikmali, oldu: "
                + previous);
        assertEquals("REVIEW", state.state());
    }

    @Test
    @DisplayName("Bilinemeyen kelime bastan baslar ve hata sayaci artar")
    void failureResetsInterval() {
        Sm2Scheduler.State state = Sm2Scheduler.State.fresh();
        state = scheduler.review(state, 5);
        state = scheduler.review(state, 5);
        assertEquals(6, state.intervalDays());

        state = scheduler.review(state, 0);

        assertEquals(1, state.intervalDays(), "aralik sifirlanmali");
        assertEquals(0, state.repetition());
        assertEquals(1, state.lapses());
        assertEquals("LEARNING", state.state());
    }

    @Test
    @DisplayName("Kolaylik katsayisi 1,3'un altina inmez")
    void easeFactorHasFloor() {
        Sm2Scheduler.State state = Sm2Scheduler.State.fresh();

        for (int i = 0; i < 20; i++) {
            state = scheduler.review(state, 0);
        }

        assertEquals(Sm2Scheduler.MIN_EASE_FACTOR, state.easeFactor(), 0.001);
    }

    @Test
    @DisplayName("Cok kolay cevap katsayiyi buyutur, zor cevap kucultur")
    void easeFactorRespondsToQuality() {
        Sm2Scheduler.State fresh = Sm2Scheduler.State.fresh();

        double easy = scheduler.review(fresh, 5).easeFactor();
        double ok = scheduler.review(fresh, 4).easeFactor();
        double hard = scheduler.review(fresh, 3).easeFactor();

        assertTrue(easy > ok, "5 katsayiyi buyutmeli");
        assertEquals(Sm2Scheduler.DEFAULT_EASE_FACTOR, ok, 0.001, "4 katsayiyi degistirmemeli");
        assertTrue(hard < ok, "3 katsayiyi kucultmeli");
    }

    @Test
    @DisplayName("Zor hatirlanan kelime yine de ilerler")
    void hardButCorrectStillAdvances() {
        Sm2Scheduler.State state = scheduler.review(Sm2Scheduler.State.fresh(), 3);

        assertEquals(1, state.repetition(), "3 gecer nottur");
        assertEquals(0, state.lapses());
    }

    @Test
    @DisplayName("Gecersiz cevap kalitesi reddedilir")
    void rejectsInvalidQuality() {
        assertThrows(IllegalArgumentException.class,
                () -> scheduler.review(Sm2Scheduler.State.fresh(), 6));
        assertThrows(IllegalArgumentException.class,
                () -> scheduler.review(Sm2Scheduler.State.fresh(), -1));
    }
}
