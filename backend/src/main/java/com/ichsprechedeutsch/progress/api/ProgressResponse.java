package com.ichsprechedeutsch.progress.api;

import java.time.LocalDate;
import java.util.List;

/** Gelisim paneli verisi. Hepsi olculmus, hicbiri tahmin degil. */
public record ProgressResponse(
        Plan plan,
        Adherence adherence,
        Vocabulary vocabulary,
        List<CategoryStat> categories,
        List<ScorePoint> scores,
        List<DayActivity> activity,
        List<SkillEstimate> skills,
        List<String> insights) {

    public record Plan(
            LocalDate startDate,
            LocalDate endDate,
            int totalDays,
            int daysRemaining,
            int elapsedDays,
            int completedDays,
            String feasibility) {
    }

    public record Adherence(
            int done,
            int partial,
            int missed,
            /** Gunu gecmis ama hic isaretlenmemis gorevler. */
            int skipped,
            int expectedTasks,
            /** 0..1; yarim yapilan gorev yarim sayilir. */
            double rate,
            int streak) {
    }

    public record Vocabulary(
            int totalWords,
            int startedWords,
            int masteredWords,
            int dueReviews,
            int totalReviews,
            double successRate) {
    }

    public record CategoryStat(
            String code,
            String nameTr,
            String skill,
            int correct,
            int attempts,
            double ratio) {
    }

    public record ScorePoint(
            LocalDate date,
            String titleTr,
            String testType,
            /** 0..1 */
            double ratio) {
    }

    public record DayActivity(LocalDate date, int tasksDone, int minutes) {
    }

    /** Yeterli veri yoksa level null olur; uydurma seviye yazilmaz. */
    public record SkillEstimate(String skill, String level, double ratio, int attempts) {
    }
}
