package com.ichsprechedeutsch.onboarding;

import com.ichsprechedeutsch.common.error.ValidationException;
import com.ichsprechedeutsch.common.model.Level;
import com.ichsprechedeutsch.common.model.StartMode;
import com.ichsprechedeutsch.level.LevelService;
import com.ichsprechedeutsch.level.LevelStore;
import com.ichsprechedeutsch.level.WorkingLevel;
import com.ichsprechedeutsch.onboarding.OnboardingStore.Goal;
import com.ichsprechedeutsch.onboarding.OnboardingStore.Profile;
import com.ichsprechedeutsch.onboarding.OnboardingStore.Rhythm;
import java.time.LocalDate;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Onboarding (SPEC Bolum 3): baslangic, (istege bagli) yerlestirme testi,
 * hedef, amac, calisma ritmi. Cevaplarin hepsi sonradan degistirilebilir.
 */
@Service
public class OnboardingService {

    /** Arayuz secenekleri (SPEC Bolum 3, adim 5). K7 kapsami disinda: arayuz secenegi. */
    public static final List<Integer> DAILY_MINUTES = List.of(15, 30, 45, 60, 90);

    public record PlacementInfo(Level result, LocalDate takenOn) {
    }

    /**
     * @param targetOptions onboarding'de sunulacak hedefler (test sonucuna gore)
     * @param path          calisma seviyesinden hedefe kisisel yol (or. A1 -> A2 -> B1)
     */
    public record State(StartMode startMode, boolean completed, Goal goal, Rhythm rhythm,
                        PlacementInfo placement, List<Level> targetOptions, WorkingLevel working,
                        List<Level> path, List<Integer> dailyMinuteOptions) {
    }

    private final OnboardingStore store;
    private final LevelStore levels;
    private final LevelService levelService;

    public OnboardingService(OnboardingStore store, LevelStore levels, LevelService levelService) {
        this.store = store;
        this.levels = levels;
        this.levelService = levelService;
    }

    @Transactional(readOnly = true)
    public State state(UUID userId, LocalDate today) {
        Profile profile = store.profile(userId).orElse(null);
        Goal goal = store.activeGoal(userId).orElse(null);
        Rhythm rhythm = store.rhythm(userId).orElse(null);
        PlacementInfo placement = levels.latestAssessment(userId, "PLACEMENT")
                .map(a -> new PlacementInfo(a.result(), a.takenOn()))
                .orElse(null);
        WorkingLevel working = levelService.overview(userId, today).working();

        List<Level> options = TargetOptions.offered(placement == null ? null : placement.result());
        List<Level> path = goal == null ? List.of() : TargetOptions.path(working.level(), goal.target());

        return new State(profile == null ? null : profile.startMode(),
                profile != null && profile.completedAt() != null,
                goal, rhythm, placement, options, working, path, DAILY_MINUTES);
    }

    @Transactional
    public void saveStartMode(UUID userId, StartMode mode) {
        if (mode == null) {
            throw new ValidationException("Başlangıç seçimi gerekli");
        }
        store.saveStartMode(userId, mode);
    }

    /** Onboarding'in son adimi: hedef, amac, ritim birlikte kaydedilir. */
    @Transactional
    public void complete(UUID userId, Level target, Purpose purpose, Integer dailyMinutes,
                         Integer daysPerWeek) {
        if (store.profile(userId).isEmpty()) {
            throw new ValidationException("Önce başlangıç adımını tamamla");
        }
        Level placement = levels.latestAssessment(userId, "PLACEMENT").map(LevelStore.Assessment::result)
                .orElse(null);
        if (target == null || !TargetOptions.offered(placement).contains(target)) {
            throw new ValidationException("Bu hedef seviyesi seçilemez");
        }
        saveGoal(userId, target, purpose);
        saveRhythm(userId, dailyMinutes, daysPerWeek);
        store.markCompleted(userId);
    }

    /** Hedef degisikligi (sonradan Kocluk'tan). Eski hedef arsivlenir. */
    @Transactional
    public void saveGoal(UUID userId, Level target, Purpose purpose) {
        if (target == null || target == Level.A0) {
            throw new ValidationException("Hedef A1–C1 arasında olmalı");
        }
        if (purpose == null) {
            throw new ValidationException("Amacını seç");
        }
        store.replaceGoal(userId, target, purpose);
    }

    @Transactional
    public void saveRhythm(UUID userId, Integer dailyMinutes, Integer daysPerWeek) {
        if (dailyMinutes == null || !Set.copyOf(DAILY_MINUTES).contains(dailyMinutes)) {
            throw new ValidationException("Günlük süre 15, 30, 45, 60 ya da 90 dakika olmalı");
        }
        if (daysPerWeek == null || daysPerWeek < 1 || daysPerWeek > 7) {
            throw new ValidationException("Haftada 1 ile 7 gün arası seçilmeli");
        }
        store.saveRhythm(userId, dailyMinutes, daysPerWeek);
    }
}
