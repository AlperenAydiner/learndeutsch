package com.ichsprechedeutsch.plan.generator;

/**
 * Hedef gercekci mi?
 *
 * Bu sinif kullaniciya "olmaz" demez; ne kadar sigmadigini soyler ve iki
 * somut alternatif hesaplar. Karar kullanicinin.
 */
public class FeasibilityChecker {

    /** Bu oranin altinda program rahat. */
    private static final double COMFORTABLE = 0.90;

    /** Bu oranin ustunde is zamana sigmiyor. */
    private static final double OVERFLOW = 1.15;

    /**
     * @param workloadMinutes       secilen birimlerin toplam efektif suresi
     * @param ratio                 is yuku / kapasite
     * @param requiredDays          mevcut gunluk sureyle kac gun gerekir
     * @param requiredDailyMinutes  mevcut gun sayisiyla gunde kac dakika gerekir
     */
    public record Verdict(
            Feasibility feasibility,
            int workloadMinutes,
            int capacityMinutes,
            double ratio,
            int requiredDays,
            int requiredDailyMinutes,
            String messageTr) {
    }

    public Verdict check(int workloadMinutes, int totalDays, int dailyMinutes) {
        int capacity = totalDays * dailyMinutes;
        double ratio = capacity == 0 ? Double.MAX_VALUE : (double) workloadMinutes / capacity;

        int requiredDays = (int) Math.ceil((double) workloadMinutes / dailyMinutes);
        int requiredDaily = (int) Math.ceil((double) workloadMinutes / totalDays);

        Feasibility feasibility = ratio <= COMFORTABLE ? Feasibility.OK
                : ratio <= OVERFLOW ? Feasibility.TIGHT
                : Feasibility.UNREALISTIC;

        return new Verdict(feasibility, workloadMinutes, capacity, round2(ratio),
                requiredDays, requiredDaily,
                message(feasibility, requiredDays, requiredDaily, totalDays, dailyMinutes));
    }

    private String message(Feasibility feasibility, int requiredDays, int requiredDaily,
                           int totalDays, int dailyMinutes) {
        return switch (feasibility) {
            case OK -> "Hedefin ulasilabilir gorunuyor. Programda tekrar icin de yer var.";
            case TIGHT -> "Program sikisik: bos gun yok. Bir iki gun aksatirsan "
                    + "geriye dusersin, ama hedef ulasilabilir.";
            case UNREALISTIC -> String.format(
                    "Bu is %s gunde %s saate sigmiyor. Ya sureyi %d gune cikar, "
                            + "ya da gunluk sureni %s saate yukselt. "
                            + "Istersen bu planla yine de devam edebilirsin.",
                    totalDays, saat(dailyMinutes), requiredDays, saat(requiredDaily));
        };
    }

    private String saat(int minutes) {
        double hours = minutes / 60.0;
        return hours == Math.floor(hours)
                ? String.valueOf((int) hours)
                : String.format("%.1f", hours).replace('.', ',');
    }

    private double round2(double value) {
        return Math.round(value * 100) / 100.0;
    }
}
