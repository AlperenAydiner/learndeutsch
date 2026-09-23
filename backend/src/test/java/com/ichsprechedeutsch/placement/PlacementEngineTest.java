package com.ichsprechedeutsch.placement;

import static com.ichsprechedeutsch.common.model.Level.A0;
import static com.ichsprechedeutsch.common.model.Level.A1;
import static com.ichsprechedeutsch.common.model.Level.A2;
import static com.ichsprechedeutsch.common.model.Level.B1;
import static com.ichsprechedeutsch.common.model.Level.B2;
import static com.ichsprechedeutsch.common.model.Level.C1;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.ichsprechedeutsch.common.model.Level;
import com.ichsprechedeutsch.config.PlacementProperties;
import com.ichsprechedeutsch.config.TestConfig;
import com.ichsprechedeutsch.content.Question;
import com.ichsprechedeutsch.placement.PlacementEngine.Block;
import com.ichsprechedeutsch.placement.PlacementEngine.Step;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;
import org.junit.jupiter.api.Test;

/** Blok 6: gecme 4/6 (0.67 >= 0.60), yukari 5/6 (0.83 >= 0.80). K-003. */
class PlacementEngineTest {

    private final PlacementEngine engine = new PlacementEngine(TestConfig.placement());

    private static Block b(Level level, int correct) {
        return new Block(level, correct, 6);
    }

    @Test
    void startsAtA2() {
        Step s = engine.decide(List.of());
        assertFalse(s.finished());
        assertEquals(A2, s.nextLevel());
    }

    @Test
    void passingWithoutUpThresholdStopsAtThatLevel() {
        Step s = engine.decide(List.of(b(A2, 4)));
        assertTrue(s.finished());
        assertEquals(A2, s.result());
    }

    @Test
    void upThresholdClimbsAndFailingReturnsPreviousLevel() {
        assertEquals(B1, engine.decide(List.of(b(A2, 5))).nextLevel());
        Step s = engine.decide(List.of(b(A2, 5), b(B1, 2)));
        assertTrue(s.finished());
        assertEquals(A2, s.result());
    }

    @Test
    void climbingThenPassingStopsThere() {
        Step s = engine.decide(List.of(b(A2, 6), b(B1, 5), b(B2, 4)));
        assertEquals(B2, s.result());
    }

    @Test
    void topsOutAtC1() {
        Step s = engine.decide(List.of(b(A2, 6), b(B1, 6), b(B2, 5), b(C1, 5)));
        assertTrue(s.finished());
        assertEquals(C1, s.result());
    }

    @Test
    void worstUpwardPathFitsInQuestionLimit() {
        // A2 -> B1 -> B2 -> C1 = 4 blok x 6 = 24 soru (en fazla 24).
        assertEquals(C1, engine.decide(List.of(b(A2, 6), b(B1, 6), b(B2, 6))).nextLevel());
    }

    @Test
    void failingStartGoesDownAndFirstPassWins() {
        assertEquals(A1, engine.decide(List.of(b(A2, 2))).nextLevel());
        Step s = engine.decide(List.of(b(A2, 2), b(A1, 4)));
        assertEquals(A1, s.result());
    }

    @Test
    void failingA1IsBelowA1() {
        Step s = engine.decide(List.of(b(A2, 1), b(A1, 3)));
        assertTrue(s.finished());
        assertEquals(A0, s.result());
    }

    @Test
    void downPhaseNeverClimbsEvenWithPerfectScore() {
        assertEquals(A1, engine.decide(List.of(b(A2, 3), b(A1, 6))).result());
    }

    @Test
    void threeOfSixIsNotAPass() {
        // Ek A'nin 5'lik blogunda 3/5 = %60 gecerdi; 6'lik blokta 3/6 = %50 gecmez (K-003).
        assertEquals(A1, engine.decide(List.of(b(A2, 3))).nextLevel());
    }

    @Test
    void rejectsBlocksThatDoNotFollowTheAlgorithm() {
        assertThrows(IllegalStateException.class, () -> engine.decide(List.of(b(B1, 5))));
    }

    @Test
    void questionLimitStopsWithHighestPassedLevel() {
        PlacementEngine tight = new PlacementEngine(new PlacementProperties(A2, 6, 0.60, 0.80, 12));
        Step s = tight.decide(List.of(b(A2, 6), b(B1, 6)));
        assertTrue(s.finished());
        assertEquals(B1, s.result());
    }

    @Test
    void pickerPrefersUnseenAndNeverRepeatsInSession() {
        List<Question> pool = new ArrayList<>();
        for (int i = 1; i <= 12; i++) {
            pool.add(new Question("q" + i, A2, "MULTIPLE_CHOICE", "p", List.of("a", "b"), "a",
                    List.of(), List.of("X"), null, 1, false, "GRAMMAR"));
        }
        Set<String> seenBefore = Set.of("q1", "q2", "q3", "q4", "q5", "q6");
        List<Question> first = QuestionPicker.pick(pool, 6, seenBefore, List.of(), new Random(1));
        assertEquals(6, first.size());
        assertTrue(first.stream().noneMatch(q -> seenBefore.contains(q.id())), "once gorulmemisler");

        Set<String> asked = new HashSet<>();
        first.forEach(q -> asked.add(q.id()));
        List<Question> second = QuestionPicker.pick(pool, 6, Set.of(), asked, new Random(2));
        assertTrue(second.stream().noneMatch(q -> asked.contains(q.id())), "oturumda tekrar yok");

        List<Question> small = QuestionPicker.pick(pool.subList(0, 3), 6, Set.of(), List.of(), new Random(3));
        assertEquals(3, small.size(), "havuz yetmezse olani verir");
    }
}
