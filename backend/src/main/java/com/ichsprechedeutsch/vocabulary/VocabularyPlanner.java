package com.ichsprechedeutsch.vocabulary;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Bugunun kelime oturumu (SPEC 8.1). Saf: bugun parametre.
 *
 * <p>Once tekrarlar gelir; birikmis tekrarlar en gecikmisten baslayarak
 * gunluk sinira kadar alinir, kalani sonraki gunlere yayilir. Tekrar
 * siniri dolduysa o gun yeni kelime verilmez: yeni kelime tekrarin onune
 * gecmez.
 */
public final class VocabularyPlanner {

    /** Tekrar bekleyen kelime. */
    public record DueCard(String wordId, int step, LocalDate due) {
    }

    /**
     * @param dueTotal  bugun ve oncesinde vadesi gelen toplam kelime
     * @param postponed gunluk sinir yuzunden sonraki gunlere kalan
     */
    public record Plan(List<DueCard> reviews, List<String> newWords, int dueTotal, int postponed) {

        public int size() {
            return reviews.size() + newWords.size();
        }
    }

    private VocabularyPlanner() {
    }

    public static Plan plan(List<DueCard> due, List<String> newCandidates, LocalDate today,
                            int newLimit, int reviewLimit) {
        List<DueCard> vadesiGelen = new ArrayList<>(due.stream()
                .filter(c -> !c.due().isAfter(today))
                .toList());
        vadesiGelen.sort(Comparator.comparing(DueCard::due).thenComparing(DueCard::wordId));

        List<DueCard> bugun = vadesiGelen.stream().limit(Math.max(0, reviewLimit)).toList();
        int ertelenen = vadesiGelen.size() - bugun.size();

        List<String> yeni = bugun.size() >= reviewLimit
                ? List.of()
                : newCandidates.stream().limit(Math.max(0, newLimit)).toList();

        return new Plan(bugun, yeni, vadesiGelen.size(), ertelenen);
    }
}
