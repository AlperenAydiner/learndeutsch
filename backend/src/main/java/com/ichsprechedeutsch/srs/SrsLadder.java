package com.ichsprechedeutsch.srs;

import com.ichsprechedeutsch.config.SrsProperties;
import java.time.LocalDate;
import java.util.Map;

/**
 * Tekrar merdiveni (SPEC 8.1). Saf: bugun parametre, veritabani yok.
 *
 * <p>Basamak 1'den baslar ve config'deki aralik listesine karsilik gelir
 * (varsayilan 1, 3, 7, 14, 30, 60, 120 gun). Bildim bir ust basamaga
 * cikarir, Zorlandim ayni basamakta tutar, Bilemedim basa dondurur.
 * SM-2 gibi bir kolaylik katsayisi yoktur: merdiven config'dedir (K7).
 */
public final class SrsLadder {

    /** Bir kelimenin cevaptan sonraki durumu. */
    public record Next(int step, LocalDate due, int intervalDays) {
    }

    private final SrsProperties config;

    public SrsLadder(SrsProperties config) {
        if (config.intervalsDays() == null || config.intervalsDays().isEmpty()) {
            throw new IllegalArgumentException("srs.intervals-days bos olamaz");
        }
        this.config = config;
    }

    /** Ilk kez calisilan kelime merdivenin ilk araligiyla girer. */
    public Next first(LocalDate today) {
        return at(1, today);
    }

    public Next apply(int currentStep, Grade grade, LocalDate today) {
        int step = switch (grade) {
            case BILDIM -> Math.min(currentStep + 1, son());
            case ZORLANDIM -> Math.max(1, currentStep);
            case BILEMEDIM -> 1;
        };
        return at(step, today);
    }

    /** Guclu kelime: araligi esige ulasmis kelime (Ek A: 30 gun). */
    public boolean strong(int step) {
        return intervalDays(step) >= config.strongWordDays();
    }

    public int intervalDays(int step) {
        int i = Math.min(Math.max(step, 1), son()) - 1;
        return config.intervalsDays().get(i);
    }

    public int son() {
        return config.intervalsDays().size();
    }

    /**
     * Gunluk yeni kelime sayisi, gunluk calisma suresine gore (Ek A).
     * Ara degerlerde bir alttaki esik gecerlidir; en kucuk esigin altinda
     * kalan sureler en kucuk esigin degerini alir.
     */
    public int dailyNew(int dailyMinutes) {
        Map<Integer, Integer> tablo = config.dailyNewByMinutes();
        int enKucukAnahtar = Integer.MAX_VALUE;
        int secilen = 0;
        int enKucukDeger = 0;
        for (Map.Entry<Integer, Integer> e : tablo.entrySet()) {
            if (e.getKey() < enKucukAnahtar) {
                enKucukAnahtar = e.getKey();
                enKucukDeger = e.getValue();
            }
            if (e.getKey() <= dailyMinutes && e.getValue() > secilen) {
                secilen = e.getValue();
            }
        }
        return secilen > 0 ? secilen : enKucukDeger;
    }

    public int dailyReviewLimit() {
        return config.dailyReviewLimit();
    }

    private Next at(int step, LocalDate today) {
        int gun = intervalDays(step);
        return new Next(step, today.plusDays(gun), gun);
    }
}
