package com.ichsprechedeutsch.article;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Random;

/**
 * Artikel alistirmasinin soru secimi (SPEC 7: Artikel kelime verisini
 * kullanir, ayri veri tutulmaz). Saf: rastgelelik disaridan verilir.
 *
 * <p>Sira: once yanlis yapilan kelimeler (en cok yanlistan baslayarak),
 * sonra hic sorulmamislar, en sonda dogru bilinenler. Ayni gruptakiler
 * karistirilir ki her oturum ayni sirayla gelmesin.
 */
public final class ArticleDrill {

    /** Bir kelimenin gecmisi: kac kez soruldu, kaci dogru. */
    public record History(int answers, int correct) {

        public int wrong() {
            return answers - correct;
        }
    }

    private ArticleDrill() {
    }

    public static List<String> pick(List<String> candidates, Map<String, History> history, int count,
                                    Random random) {
        List<String> yanlislar = new ArrayList<>();
        List<String> yeniler = new ArrayList<>();
        List<String> bilinenler = new ArrayList<>();
        for (String id : candidates) {
            History h = history.get(id);
            if (h == null || h.answers() == 0) {
                yeniler.add(id);
            } else if (h.wrong() > 0) {
                yanlislar.add(id);
            } else {
                bilinenler.add(id);
            }
        }

        yanlislar.sort(Comparator.comparingInt((String id) -> history.get(id).wrong()).reversed()
                .thenComparing(Comparator.naturalOrder()));
        java.util.Collections.shuffle(yeniler, random);
        java.util.Collections.shuffle(bilinenler, random);

        List<String> out = new ArrayList<>(yanlislar);
        out.addAll(yeniler);
        out.addAll(bilinenler);
        return out.stream().limit(Math.max(0, count)).toList();
    }
}
