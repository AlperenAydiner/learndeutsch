package com.ichsprechedeutsch.plan.generator;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class PlanGeneratorTest {

    private static final LocalDate START = LocalDate.of(2026, 1, 5);

    /** Referans sablonun toplami; bir ogretim birimi bu kadar surer. */
    private static final int UNIT_MINUTES = 390;

    private final PlanGenerator generator = new PlanGenerator();

    // ------------------------------------------------------------------
    // Fizibilite
    // ------------------------------------------------------------------

    @Test
    @DisplayName("22 birim, 30 gun, gunde 6,5 saat: rahat siğar")
    void comfortablePlan() {
        GeneratedPlan plan = generate(22, 30, 390, true);

        assertEquals(Feasibility.OK, plan.verdict().feasibility());
        assertEquals(30, plan.days().size());
        assertEquals(0, plan.unplacedUnits());
    }

    @Test
    @DisplayName("Ayni is, gunde 1 saat: sigmaz ve somut alternatif uretilir")
    void unrealisticPlan() {
        GeneratedPlan plan = generate(22, 10, 60, true);

        assertEquals(Feasibility.UNREALISTIC, plan.verdict().feasibility());

        int workload = plan.verdict().workloadMinutes();
        assertEquals((int) Math.ceil(workload / 60.0), plan.verdict().requiredDays());
        assertEquals((int) Math.ceil(workload / 10.0), plan.verdict().requiredDailyMinutes());
        assertTrue(plan.verdict().messageTr().contains("gune cikar"));
    }

    @Test
    @DisplayName("Sigmasa bile plan uretilir ve sigmayanlar bildirilir")
    void unrealisticStillProducesPlan() {
        GeneratedPlan plan = generate(22, 10, 60, true);

        assertEquals(10, plan.days().size());
        assertTrue(plan.unplacedUnits() > 0);
        assertTrue(plan.notes().stream().anyMatch(n -> n.contains("sigmadi")));
    }

    // ------------------------------------------------------------------
    // Gunluk sureye gore bolunme - tasarimin kalbi
    // ------------------------------------------------------------------

    @Test
    @DisplayName("Gunde 2 saati olan kullanicida birim uc gune bolunur")
    void unitSpansMultipleDaysWhenDayIsShort() {
        GeneratedPlan plan = generate(3, 30, 130, false);   // 390 / 130 = 3 gun

        List<PlannedDay> firstUnitDays = plan.days().stream()
                .filter(d -> "U-0".equals(d.unitCode()))
                .toList();

        assertEquals(3, firstUnitDays.size(), "birim uc gune yayilmali");
        assertTrue(firstUnitDays.get(0).titleTr().endsWith("(1/3)"));
        assertTrue(firstUnitDays.get(2).titleTr().endsWith("(3/3)"));
    }

    @Test
    @DisplayName("Gunluk sure birime yetiyorsa bolunme olmaz")
    void unitFitsOneDayWhenTimeIsEnough() {
        GeneratedPlan plan = generate(3, 30, 390, false);

        List<PlannedDay> firstUnitDays = plan.days().stream()
                .filter(d -> "U-0".equals(d.unitCode()))
                .toList();

        assertEquals(1, firstUnitDays.size());
        assertFalse(firstUnitDays.get(0).titleTr().contains("/"));
    }

    @Test
    @DisplayName("Fizibilite ile yerlestirme tutarlidir")
    void feasibilityMatchesPlacement() {
        // Sigan planda disarida birim kalmamali.
        GeneratedPlan fits = generate(10, 40, 390, true);
        assertEquals(Feasibility.OK, fits.verdict().feasibility());
        assertEquals(0, fits.unplacedUnits());

        // Sigmayan planda mutlaka disarida birim kalmali.
        GeneratedPlan doesNot = generate(30, 15, 60, true);
        assertEquals(Feasibility.UNREALISTIC, doesNot.verdict().feasibility());
        assertTrue(doesNot.unplacedUnits() > 0);
    }

    // ------------------------------------------------------------------
    // Yerlestirme testinin etkisi
    // ------------------------------------------------------------------

    @Test
    @DisplayName("Bilinen konu programdan cikar")
    void masteredCategoriesAreSkipped() {
        GeneratedPlan before = generate(10, 30, 390, false, Set.of(), Set.of());
        GeneratedPlan after = generate(10, 30, 390, false,
                Set.of("KAT-0", "KAT-1", "KAT-2"), Set.of());

        assertEquals(0, before.skippedUnits());
        assertEquals(3, after.skippedUnits());
        assertTrue(after.verdict().workloadMinutes() < before.verdict().workloadMinutes());
    }

    @Test
    @DisplayName("Kismi bilgi birim atlatmaz, sadece kisaltir")
    void partialKnowledgeShortensOnly() {
        GeneratedPlan plan = generate(10, 30, 390, false,
                Set.of(), Set.of("KAT-0", "KAT-1"));

        assertEquals(0, plan.skippedUnits());
        assertEquals(2, plan.shortenedUnits());
    }

    // ------------------------------------------------------------------
    // Takvim
    // ------------------------------------------------------------------

    @Test
    @DisplayName("Haftada bir hafif gun gelir")
    void weeklyLightDays() {
        GeneratedPlan plan = generate(10, 28, 390, false);

        assertEquals("LIGHT", plan.days().get(6).dayType(), "7. gun");
        assertEquals("LIGHT", plan.days().get(13).dayType(), "14. gun");
    }

    @Test
    @DisplayName("Sinav varsa son gun provadir")
    void lastDayIsMockWhenExam() {
        for (int days : new int[]{14, 21, 28, 30, 60}) {
            GeneratedPlan plan = generate(10, days, 390, true);
            PlannedDay last = plan.days().get(days - 1);

            assertEquals("MOCK_EXAM", last.dayType(), days + " gunluk plan");
            assertFalse(last.tasks().isEmpty());
        }
    }

    @Test
    @DisplayName("Deneme sinavi bolunmez, tek gune sigar")
    void mockExamIsAtomic() {
        // Gunde 1 saat: normal birim 7 gune bolunurdu ama deneme bolunmemeli.
        GeneratedPlan plan = generate(3, 30, 60, true);

        List<PlannedDay> mockDays = plan.days().stream()
                .filter(d -> "MOCK_EXAM".equals(d.dayType()))
                .toList();

        assertFalse(mockDays.isEmpty(), "deneme gunu olmali");
        for (PlannedDay day : mockDays) {
            assertFalse(day.titleTr().contains("/"),
                    "deneme bolunmemeli: " + day.titleTr());
        }
    }

    @Test
    @DisplayName("Sinav yoksa deneme gunu konmaz")
    void noMockDaysWithoutExam() {
        GeneratedPlan plan = generate(10, 30, 390, false);

        assertTrue(plan.days().stream().noneMatch(d -> "MOCK_EXAM".equals(d.dayType())));
    }

    @Test
    @DisplayName("Gun numaralari ve tarihler kesintisiz ilerler")
    void daysAreContiguous() {
        GeneratedPlan plan = generate(10, 21, 120, true);

        for (int i = 0; i < plan.days().size(); i++) {
            assertEquals(i + 1, plan.days().get(i).dayNumber());
            assertEquals(START.plusDays(i), plan.days().get(i).date());
        }
    }

    @Test
    @DisplayName("Hafif gun kullanicinin zayif konusunu adiyla anar")
    void lightDaysNameWeakTopics() {
        GeneratedPlan plan = generateWithWeakTopics(3, 21, 390,
                List.of("Dativ", "Perfekt", "Artikel"));

        PlannedDay light = plan.days().stream()
                .filter(d -> "LIGHT".equals(d.dayType()))
                .findFirst()
                .orElseThrow(() -> new AssertionError("hafif gun bulunamadi"));

        assertEquals("Tekrar: Dativ, Perfekt", light.titleTr());
    }

    // ------------------------------------------------------------------
    // Blok olcekleme
    // ------------------------------------------------------------------

    @Test
    @DisplayName("Gunun toplami sozu verilen sureye esittir")
    void dayTotalMatchesPromisedMinutes() {
        for (int daily : new int[]{60, 90, 120, 180, 240, 390}) {
            GeneratedPlan plan = generate(20, 30, daily, true);

            for (PlannedDay day : plan.days()) {
                assertEquals(daily, day.plannedMinutes(),
                        "gun " + day.dayNumber() + " (gunluk " + daily + " dk)");
            }
        }
    }

    @Test
    @DisplayName("Hicbir blok 15 dakikanin altina dusmez")
    void noBlockBelowMinimum() {
        for (int daily : new int[]{60, 90, 120, 390}) {
            GeneratedPlan plan = generate(20, 30, daily, true);

            for (PlannedDay day : plan.days()) {
                for (PlannedTask task : day.tasks()) {
                    assertTrue(task.plannedMinutes() >= BlockScheduler.MIN_BLOCK_MINUTES,
                            day.dayNumber() + ". gun " + task.taskType()
                                    + " = " + task.plannedMinutes() + " dk");
                }
            }
        }
    }

    @Test
    @DisplayName("Kisa gunde blok sayisi azalir")
    void shortDaysGetFewerBlocks() {
        PlannedDay full = firstTeachingDay(generate(20, 30, 390, true));
        PlannedDay short_ = firstTeachingDay(generate(20, 30, 120, true));

        assertEquals(7, full.tasks().size(), "6,5 saatte yedi blok");
        assertTrue(short_.tasks().size() < 7,
                "2 saatte daha az blok olmali, oldu: " + short_.tasks().size());
        assertFalse(short_.tasks().isEmpty());
    }

    @Test
    @DisplayName("Gun asiri gelen blok tamamen kaybolmaz")
    void rotatedBlocksStillAppear() {
        GeneratedPlan plan = generate(20, 30, 120, true);

        long sprechenDays = plan.days().stream()
                .filter(d -> d.tasks().stream()
                        .anyMatch(t -> "SPRECHEN".equals(t.taskType())))
                .count();

        assertTrue(sprechenDays > 0, "Sprechen tamamen kaybolmamali");
        assertTrue(sprechenDays < plan.days().size(), "Sprechen her gun gelmemeli");
    }

    // ------------------------------------------------------------------
    // Gorev baglari
    // ------------------------------------------------------------------

    @Test
    @DisplayName("Ogretim gunundeki gorevler birime baglidir")
    void teachingTasksLinkToUnit() {
        PlannedDay day = firstTeachingDay(generate(20, 30, 390, true));

        assertNotNull(day.contentUnitId());
        assertTrue(day.tasks().stream()
                .allMatch(t -> day.contentUnitId().equals(t.contentUnitId())));
    }

    @Test
    @DisplayName("Hafif gunun birimi yoktur ama gorevi vardir")
    void lightDaysHaveTasksWithoutUnit() {
        GeneratedPlan plan = generate(5, 21, 120, false);

        PlannedDay light = plan.days().stream()
                .filter(d -> "LIGHT".equals(d.dayType()))
                .findFirst()
                .orElseThrow(() -> new AssertionError("hafif gun bulunamadi"));

        assertNull(light.contentUnitId());
        assertFalse(light.tasks().isEmpty());
    }

    // ------------------------------------------------------------------
    // Yardimcilar
    // ------------------------------------------------------------------

    private PlannedDay firstTeachingDay(GeneratedPlan plan) {
        return plan.days().stream()
                .filter(d -> d.contentUnitId() != null)
                .findFirst()
                .orElseThrow(() -> new AssertionError("ogretim gunu bulunamadi"));
    }

    private GeneratedPlan generate(int bodyUnits, int totalDays, int dailyMinutes,
                                   boolean exam) {
        return generate(bodyUnits, totalDays, dailyMinutes, exam, Set.of(), Set.of());
    }

    private GeneratedPlan generate(int bodyUnits, int totalDays, int dailyMinutes,
                                   boolean exam, Set<String> mastered,
                                   Set<String> partial) {
        return generator.generate(request(bodyUnits, totalDays, dailyMinutes, exam,
                mastered, partial, List.of()));
    }

    private GeneratedPlan generateWithWeakTopics(int bodyUnits, int totalDays,
                                                 int dailyMinutes, List<String> weak) {
        return generator.generate(request(bodyUnits, totalDays, dailyMinutes, false,
                Set.of(), Set.of(), weak));
    }

    /**
     * Gercek icerik havuzuna benzeyen sentetik bir istek.
     * Sinav secildiginde sona bir deneme ve bir prova birimi eklenir -
     * tipki gercek icerikte oldugu gibi.
     */
    private PlanRequest request(int bodyUnits, int totalDays, int dailyMinutes,
                                boolean exam, Set<String> mastered,
                                Set<String> partial, List<String> weakTopics) {

        List<UnitSpec> units = new ArrayList<>();
        Map<String, List<BlockSpec>> blocks = new HashMap<>();
        int sequence = 10;

        for (int i = 0; i < bodyUnits; i++) {
            sequence += 10;
            units.add(unit("U-" + i, i < bodyUnits / 2 ? "A1" : "A2", "TEMEL",
                    sequence, "Birim " + i, List.of("KAT-" + i)));
            blocks.put("U-" + i, referenceBlocks());
        }

        if (exam) {
            sequence += 10;
            units.add(unit("DENEME-1", "A2", "DENEME", sequence, "Tam deneme", List.of()));
            blocks.put("DENEME-1", referenceBlocks());

            sequence += 10;
            units.add(unit("PROVA", "A2", "PROVA", sequence, "Sinav provasi", List.of()));
            blocks.put("PROVA", referenceBlocks());
        }

        return new PlanRequest("A2", exam ? "GOETHE_A2" : "NONE", totalDays,
                dailyMinutes, START, units, blocks, mastered, partial, weakTopics);
    }

    private UnitSpec unit(String code, String level, String phase, int sequence,
                          String title, List<String> categories) {
        return new UnitSpec(UUID.randomUUID(), code, level, phase, sequence,
                title, "ozet", UNIT_MINUTES, !"PROVA".equals(phase), categories);
    }

    /** Projenin referans blok sablonu: 390 dakika. */
    private List<BlockSpec> referenceBlocks() {
        return List.of(
                new BlockSpec("KELIME", 60, "Kelime"),
                new BlockSpec("GRAMER", 90, "Gramer"),
                new BlockSpec("HOEREN", 60, "Dinleme"),
                new BlockSpec("LESEN", 60, "Okuma"),
                new BlockSpec("SCHREIBEN", 45, "Yazma"),
                new BlockSpec("SPRECHEN", 45, "Konusma"),
                new BlockSpec("SINAV_PRATIGI", 30, "Alistirma"));
    }
}
