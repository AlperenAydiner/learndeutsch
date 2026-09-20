package com.ichsprechedeutsch.plan.generator;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * Hedefe giden yolda hangi birimlerin calisilacagini ve her birinin ne
 * kadar sureceğini belirler.
 *
 * Yerlestirme testi burada devreye girer: bildigin konu programdan cikar,
 * yarim bildigin konunun suresi yariya iner. Bu, "hedeften geriye
 * planlama"nin ilk ve en belirleyici adimidir - 30 gunluk bir hedefi
 * gercekci kilan sey genelde budur.
 */
public class UnitSelector {

    /** Yari bilinen konu icin sure carpani. */
    private static final double PARTIAL_FACTOR = 0.5;

    /** Bir birimin secim sonucu. */
    public record Selection(UnitSpec unit, int effectiveMinutes, String decision) {

        public boolean isSkipped() {
            return effectiveMinutes == 0;
        }
    }

    /**
     * Hedef seviyeye kadar olan birimleri sirali dondurur.
     *
     * Sinavi olmayan kullaniciya sinav odagi, deneme ve prova asamalari
     * verilmez: o gunler onun icin bos yere gecmis olurdu.
     */
    public List<Selection> select(PlanRequest request) {
        List<Selection> out = new ArrayList<>();

        for (UnitSpec unit : sortedRelevantUnits(request)) {
            int effective = effectiveMinutes(unit, request);
            String decision = effective == 0 ? "SKIPPED"
                    : effective < unit.estimatedMinutes() ? "SHORTENED" : "FULL";
            out.add(new Selection(unit, effective, decision));
        }
        return out;
    }

    private List<UnitSpec> sortedRelevantUnits(PlanRequest request) {
        int targetRank = levelRank(request.targetLevel());

        return request.units().stream()
                .filter(u -> levelRank(u.level()) <= targetRank)
                .filter(u -> request.hasExam() || !isExamOnly(u.phase()))
                .sorted((a, b) -> Integer.compare(a.sequenceNo(), b.sequenceNo()))
                .toList();
    }

    /**
     * Bir birim ancak TUM kategorileri biliniyorsa atlanir.
     *
     * Kismi bilgi birim atlatmaz: "Artikel"i bilmen, "Artikel ve cogul"
     * birimini atlamana yetmez, cogul ve olumsuzlugu da bilmen gerekir.
     */
    private int effectiveMinutes(UnitSpec unit, PlanRequest request) {
        if (!unit.isNewContent() || unit.categoryCodes().isEmpty()) {
            return unit.estimatedMinutes();   // tekrar ve deneme gunleri atlanmaz
        }

        Set<String> mastered = request.masteredCategories();
        Set<String> partial = request.partialCategories();

        if (mastered.containsAll(unit.categoryCodes())) {
            return 0;
        }

        boolean mostlyKnown = unit.categoryCodes().stream()
                .allMatch(c -> mastered.contains(c) || partial.contains(c));

        return mostlyKnown
                ? (int) Math.round(unit.estimatedMinutes() * PARTIAL_FACTOR)
                : unit.estimatedMinutes();
    }

    private boolean isExamOnly(String phase) {
        return "SINAV_ODAK".equals(phase) || "DENEME".equals(phase) || "PROVA".equals(phase);
    }

    private int levelRank(String level) {
        return switch (level) {
            case "A1" -> 1;
            case "A2" -> 2;
            case "B1" -> 3;
            default -> 99;
        };
    }
}
