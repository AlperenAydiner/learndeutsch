package com.ichsprechedeutsch.vocabulary;

/**
 * SM-2 araliklı tekrar algoritmasi.
 *
 * Fikir basit: dogru bildigin kelime giderek daha seyrek, zorlandigin
 * kelime daha sik karsina cikar. Her kelimenin bir "kolaylik katsayisi"
 * vardir; iyi cevap onu buyutur, kotu cevap kucultur ve araligi sifirlar.
 *
 * Saf sinif: veritabanina, saate veya rastgeleye dokunmaz. Bugunun
 * tarihini de disaridan alir, boylece test edilebilir.
 */
public class Sm2Scheduler {

    /** Katsayi bunun altina inmez; yoksa kelime sonsuza dek her gun gelir. */
    public static final double MIN_EASE_FACTOR = 1.3;

    /** Ilk kez gorulen kelimenin baslangic katsayisi. */
    public static final double DEFAULT_EASE_FACTOR = 2.5;

    /** Bu degerin altindaki cevap "bilemedim" sayilir ve araligi sifirlar. */
    public static final int PASS_THRESHOLD = 3;

    /** Ikinci dogru cevaptan sonraki aralik. */
    private static final int SECOND_INTERVAL = 6;

    public record State(
            double easeFactor,
            int intervalDays,
            int repetition,
            int lapses,
            /** NEW | LEARNING | REVIEW */
            String state) {

        /** Hic gorulmemis kelime. */
        public static State fresh() {
            return new State(DEFAULT_EASE_FACTOR, 0, 0, 0, "NEW");
        }
    }

    /**
     * Bir cevabi degerlendirip kelimenin yeni durumunu hesaplar.
     *
     * @param quality 0 bilmiyordum, 3 zor hatirladim, 4 hatirladim, 5 cok kolay
     */
    public State review(State current, int quality) {
        if (quality < 0 || quality > 5) {
            throw new IllegalArgumentException("Cevap kalitesi 0-5 arasinda olmali");
        }

        double ease = nextEaseFactor(current.easeFactor(), quality);

        if (quality < PASS_THRESHOLD) {
            // Bilemedi: bastan basla ama katsayiyi tamamen sifirlama.
            return new State(ease, 1, 0, current.lapses() + 1, "LEARNING");
        }

        int repetition = current.repetition() + 1;
        int interval = switch (repetition) {
            case 1 -> 1;
            case 2 -> SECOND_INTERVAL;
            default -> (int) Math.round(current.intervalDays() * ease);
        };

        return new State(ease, Math.max(1, interval), repetition, current.lapses(),
                repetition >= 3 ? "REVIEW" : "LEARNING");
    }

    /**
     * Kolaylik katsayisinin guncellenmesi - SM-2'nin ozu.
     *
     * q=5 katsayiyi biraz buyutur, q=4 aynen birakir, q=3 ve altı kucultur.
     */
    private double nextEaseFactor(double current, int quality) {
        double delta = 0.1 - (5 - quality) * (0.08 + (5 - quality) * 0.02);
        return Math.max(MIN_EASE_FACTOR, round2(current + delta));
    }

    private double round2(double value) {
        return Math.round(value * 100) / 100.0;
    }
}
