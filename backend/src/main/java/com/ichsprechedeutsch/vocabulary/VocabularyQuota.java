package com.ichsprechedeutsch.vocabulary;

/**
 * Gunluk yeni kelime kotasi.
 *
 * Kelime blogu planlanan sureyi asmamali. Bekleyen tekrar yuku artmissa
 * yeni kelime sayisi otomatik kisilir; tekrarlar zaten blogu dolduruyorsa
 * o gun hic yeni kelime verilmez.
 *
 * Bu, sistemin en kolay ihmal edilen ama en onemli davranislarindan biri:
 * her gun sabit sayida yeni kelime veren uygulamalarda tekrar borcu
 * birikir, kullanici bir sure sonra 300 karti gorunce birakip gider.
 */
public class VocabularyQuota {

    /** Bir tekrar karti ortalama bu kadar surer. */
    public static final int SECONDS_PER_REVIEW = 8;

    /** Yeni bir kelime, ilk ogrenme ve ayni gun tekrariyla bu kadar surer. */
    public static final int SECONDS_PER_NEW_WORD = 25;

    /** Gunluk ust sinir: tek gunde bundan fazla yeni kelime akilda kalmaz. */
    public static final int MAX_NEW_PER_DAY = 40;

    public record Quota(int newWords, int dueReviews, String reasonTr) {
    }

    /**
     * @param blockMinutes o gunku kelime blogunun suresi
     * @param dueCount     bekleyen tekrar sayisi
     * @param available    havuzda kalan ogrenilmemis kelime sayisi
     */
    public Quota calculate(int blockMinutes, int dueCount, int available) {
        int blockSeconds = Math.max(0, blockMinutes) * 60;
        int reviewSeconds = dueCount * SECONDS_PER_REVIEW;
        int remaining = blockSeconds - reviewSeconds;

        if (remaining <= 0) {
            return new Quota(0, dueCount,
                    "Bugun yeni kelime yok: " + dueCount + " tekrarin var, "
                            + "once onlari bitir.");
        }

        int fits = remaining / SECONDS_PER_NEW_WORD;
        int newWords = Math.min(Math.min(fits, MAX_NEW_PER_DAY), available);

        if (newWords == 0 && available == 0) {
            return new Quota(0, dueCount, "Bu seviyedeki kelimelerin hepsini gordun.");
        }
        if (newWords == 0) {
            return new Quota(0, dueCount,
                    "Bugun yeni kelime icin yer kalmadi; tekrarlara odaklan.");
        }

        return new Quota(newWords, dueCount,
                newWords + " yeni kelime ve " + dueCount + " tekrar.");
    }
}
