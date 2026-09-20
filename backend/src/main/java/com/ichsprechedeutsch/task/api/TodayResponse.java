package com.ichsprechedeutsch.task.api;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/** Gunun ekrani: konu, gorevler, ilerleme ve seri. */
public record TodayResponse(
        UUID dayId,
        int dayNumber,
        int totalDays,
        LocalDate date,
        LocalDate endDate,
        String dayType,
        String titleTr,
        String grammarSummary,
        String vocabTheme,
        int plannedMinutes,
        String status,
        List<Task> tasks,
        Progress progress,
        int streak) {

    public record Task(
            UUID id,
            String taskType,
            int plannedMinutes,
            int orderNo,
            String instructionTr,
            /** Isaretlenmediyse null. */
            String status,
            /** Kaydedilmis oz bildirim, JSON metni. */
            String selfReport,
            /** Bu gorev tipinde sorulacak hazir secenekler. */
            List<ReportField> reportFields) {
    }

    /**
     * Arayuzun hangi secenekleri gosterecegini sunucu soyler.
     * Boylece gecerli degerler tek yerde tanimli kalir; istemci ile
     * sunucu birbirinden habersiz farkli listeler tasimaz.
     */
    public record ReportField(String name, List<String> allowed, boolean numeric) {
    }

    public record Progress(
            int done,
            int partial,
            int missed,
            int total,
            int doneMinutes,
            int totalMinutes) {
    }
}
