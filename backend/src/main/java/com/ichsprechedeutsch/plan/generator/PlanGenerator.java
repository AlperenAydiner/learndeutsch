package com.ichsprechedeutsch.plan.generator;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Hedeften geriye planlama.
 *
 * Iki sey bu ureticiyi sabit bir tablodan ayirir:
 *
 * 1. BIR BIRIM GEREKTIGI KADAR GUNE YAYILIR. Gunde 6,5 saati olan
 *    kullanicinin bir gunde bitirdigi konuyu, gunde 2 saati olan uc
 *    gunde bitirir. Boylece "gunde 2 saat" diyen kullaniciya 6,5 saatlik
 *    icerik tek gune sikistirilmis gibi gosterilmez.
 *
 * 2. SINAV SONDAN YERLESIR. Prova, son tekrar ve denemeler takvimin
 *    sonundan geriye dogru yerini alir; geri kalan konular onlerine
 *    sigdirilir. Icerik sigmazsa sinav hazirligi feda edilmez, kullanici
 *    acikca uyarilir.
 *
 * Saf bir sinif: veritabanina, saate veya rastgeleye dokunmaz. Ayni
 * girdi her zaman ayni plani verir.
 */
public class PlanGenerator {

    /** Takvimin sonunda yer alan asamalar; sondan geriye yerlesirler. */
    private static final Set<String> TAIL_PHASES =
            Set.of("DENEME", "SON_TEKRAR", "PROVA");

    /**
     * Bolunemeyen asamalar.
     *
     * Deneme sinavi tek oturumda yapilir. Uc gune bolunmus bir deneme
     * hicbir sey olcmez: sinav kosullarini taklit etmesi gereken sey,
     * kesintisiz olmasidir. Gunluk suren kisaysa deneme kisalir, bolunmez.
     */
    private static final Set<String> ATOMIC_PHASES = Set.of("DENEME", "PROVA");

    /** Hafif gunun referans bloklari: yeni konu yok, tekrar var. */
    private static final List<BlockSpec> LIGHT_DAY_BLOCKS = List.of(
            new BlockSpec("KELIME", 240, "Bekleyen kelime tekrarlarini bitir."),
            new BlockSpec("GRAMER", 150, "Zayif konularini tekrar et."));

    private final UnitSelector unitSelector = new UnitSelector();
    private final FeasibilityChecker feasibilityChecker = new FeasibilityChecker();
    private final DayReserver dayReserver = new DayReserver();
    private final BlockScheduler blockScheduler = new BlockScheduler();

    /** Bir birimin kac gun sureceği ve hangi gunlere dustugu. */
    private record Placement(UnitSelector.Selection selection, int partIndex, int partCount) {
    }

    public GeneratedPlan generate(PlanRequest request) {
        List<UnitSelector.Selection> selections = unitSelector.select(request);
        List<UnitSelector.Selection> toPlace = selections.stream()
                .filter(s -> !s.isSkipped())
                .toList();

        int workload = toPlace.stream()
                .mapToInt(UnitSelector.Selection::effectiveMinutes)
                .sum();

        FeasibilityChecker.Verdict verdict = feasibilityChecker.check(
                workload, request.totalDays(), request.dailyMinutes());

        String[] dayTypes = dayReserver.reserve(request.totalDays());
        Placement[] placements = distribute(request, dayTypes, toPlace);

        List<PlannedDay> days = buildDays(request, dayTypes, placements);

        int placedUnits = (int) java.util.Arrays.stream(placements)
                .filter(p -> p != null && p.partIndex() == 1)
                .count();

        return buildResult(verdict, days, selections, toPlace.size() - placedUnits);
    }

    // ------------------------------------------------------------------
    // Dagitim
    // ------------------------------------------------------------------

    /**
     * Birimleri gunlere yayar.
     *
     * Once kuyruk (deneme, son tekrar, prova) sondan geriye yerlesir,
     * sonra govde bastan ileriye. Ortada bosluk kalirsa hafif gun olur;
     * yer kalmazsa govdenin sonundaki birimler disarida kalir ve
     * kullaniciya bildirilir.
     */
    private Placement[] distribute(PlanRequest request, String[] dayTypes,
                                   List<UnitSelector.Selection> toPlace) {

        Placement[] placements = new Placement[dayTypes.length];
        List<Integer> teaching = dayReserver.teachingDays(dayTypes);

        List<UnitSelector.Selection> tail = toPlace.stream()
                .filter(s -> TAIL_PHASES.contains(s.unit().phase()))
                .toList();
        List<UnitSelector.Selection> body = toPlace.stream()
                .filter(s -> !TAIL_PHASES.contains(s.unit().phase()))
                .toList();

        int cursor = teaching.size() - 1;
        // Kuyruk sondan geriye: son birim en son gune gelsin diye ters sirada.
        for (int i = tail.size() - 1; i >= 0 && cursor >= 0; i--) {
            UnitSelector.Selection selection = tail.get(i);
            int parts = daysNeeded(selection, request.dailyMinutes());

            for (int part = parts; part >= 1 && cursor >= 0; part--) {
                placements[teaching.get(cursor)] = new Placement(selection, part, parts);
                cursor--;
            }
        }

        int tailStart = cursor + 1;   // kuyrugun basladigi ogretim gunu indeksi

        int index = 0;
        for (UnitSelector.Selection selection : body) {
            int parts = daysNeeded(selection, request.dailyMinutes());
            if (index + parts > tailStart) {
                break;                // kalanlar sigmadi
            }
            for (int part = 1; part <= parts; part++) {
                placements[teaching.get(index)] = new Placement(selection, part, parts);
                index++;
            }
        }

        // Govde ile kuyruk arasinda kalan ogretim gunleri hafif gun olur.
        for (int i = index; i < tailStart; i++) {
            dayTypes[teaching.get(i)] = DayReserver.LIGHT;
        }

        return placements;
    }

    /**
     * Birim kac gun surer?
     *
     * Tasarimin kalbi burasi: gunluk sure kucukse birim boluner, gune
     * sikistirilmaz.
     */
    private int daysNeeded(UnitSelector.Selection selection, int dailyMinutes) {
        if (ATOMIC_PHASES.contains(selection.unit().phase())) {
            return 1;
        }
        return Math.max(1, (int) Math.ceil(
                (double) selection.effectiveMinutes() / dailyMinutes));
    }

    // ------------------------------------------------------------------
    // Gun kurulumu
    // ------------------------------------------------------------------

    private List<PlannedDay> buildDays(PlanRequest request, String[] dayTypes,
                                       Placement[] placements) {
        List<PlannedDay> days = new ArrayList<>();

        for (int i = 0; i < dayTypes.length; i++) {
            int dayNumber = i + 1;
            Placement placement = placements[i];

            String dayType = dayType(placement);
            List<BlockSpec> blocks = blocksFor(request, placement, dayType);
            UUID unitId = placement != null ? placement.selection().unit().id() : null;

            List<PlannedTask> tasks =
                    blockScheduler.schedule(blocks, request.dailyMinutes(), dayNumber, unitId);

            days.add(new PlannedDay(
                    dayNumber,
                    request.startDate().plusDays(i),
                    dayType,
                    unitId,
                    placement != null ? placement.selection().unit().code() : null,
                    title(placement, dayType, request),
                    tasks.stream().mapToInt(PlannedTask::plannedMinutes).sum(),
                    tasks));
        }
        return days;
    }

    private String dayType(Placement placement) {
        // Birim dusmeyen her gun hafif gundur: yeni konu yok, tekrar var.
        if (placement == null) {
            return DayReserver.LIGHT;
        }
        String phase = placement.selection().unit().phase();
        if ("DENEME".equals(phase) || "PROVA".equals(phase)) {
            return DayReserver.MOCK_EXAM;
        }
        return DayReserver.NORMAL;
    }

    private List<BlockSpec> blocksFor(PlanRequest request, Placement placement,
                                      String dayType) {
        if (placement != null) {
            List<BlockSpec> blocks =
                    request.blocksByUnitCode().get(placement.selection().unit().code());
            if (blocks != null && !blocks.isEmpty()) {
                return blocks;
            }
        }
        return LIGHT_DAY_BLOCKS;
    }

    /**
     * Cok gune yayilan birim kacinci gununde oldugunu soyler: "Perfekt (2/3)".
     * Hafif gun ise kullanicinin en zayif konularini adiyla anar - genel
     * "tekrar gunu" demek, neyi tekrar edecegini soylememek demektir.
     */
    private String title(Placement placement, String dayType, PlanRequest request) {
        if (placement == null) {
            List<String> weak = request.weakTopicsTr();
            if (weak != null && !weak.isEmpty()) {
                return "Tekrar: " + String.join(", ", weak.subList(0, Math.min(2, weak.size())));
            }
            return "Tekrar gunu";
        }

        String title = placement.selection().unit().titleTr();
        return placement.partCount() > 1
                ? title + " (" + placement.partIndex() + "/" + placement.partCount() + ")"
                : title;
    }

    // ------------------------------------------------------------------

    private GeneratedPlan buildResult(FeasibilityChecker.Verdict verdict,
                                      List<PlannedDay> days,
                                      List<UnitSelector.Selection> selections,
                                      int unplaced) {
        int skipped = (int) selections.stream()
                .filter(UnitSelector.Selection::isSkipped).count();
        int shortened = (int) selections.stream()
                .filter(s -> "SHORTENED".equals(s.decision())).count();

        List<String> notes = new ArrayList<>();
        notes.add(verdict.messageTr());

        if (skipped > 0) {
            notes.add(skipped + " konuyu zaten bildigin icin programdan cikardik.");
        }
        if (shortened > 0) {
            notes.add(shortened + " konunun suresini yarim bildigin icin kisalttik.");
        }
        if (unplaced > 0) {
            notes.add(unplaced + " konu bu sureye sigmadi. Sinav hazirligini feda "
                    + "etmemek icin onlari cikardik; sureyi uzatirsan plana girerler.");
        }

        return new GeneratedPlan(verdict, days, skipped, shortened, unplaced, notes);
    }
}
