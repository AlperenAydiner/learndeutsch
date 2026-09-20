package com.ichsprechedeutsch.goal;

import com.ichsprechedeutsch.common.error.NotFoundException;
import com.ichsprechedeutsch.common.error.ValidationException;
import com.ichsprechedeutsch.goal.api.GoalRequest;
import java.time.LocalDate;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class LearningGoalService {

    /**
     * MVP'de icerik havuzu A1 ve A2'yi kapsiyor. B1 semada destekleniyor
     * ama icerigi olmadigi icin burada kabul edilmiyor: kullaniciya
     * uretemeyecegimiz bir program vaat etmeyelim.
     */
    private static final Set<String> LEVELS = Set.of("A1", "A2");

    private static final Set<String> EXAMS = Set.of("GOETHE_A2", "NONE");

    private static final String ACTIVE = "ACTIVE";

    private final LearningGoalRepository repository;

    public LearningGoalService(LearningGoalRepository repository) {
        this.repository = repository;
    }

    @Transactional(readOnly = true)
    public LearningGoal activeGoal(UUID userId) {
        return repository.findByUserIdAndStatus(userId, ACTIVE)
                .orElseThrow(() -> new NotFoundException("Henuz bir hedefin yok"));
    }

    @Transactional(readOnly = true)
    public List<LearningGoal> history(UUID userId) {
        return repository.findByUserIdOrderByCreatedAtDesc(userId);
    }

    /**
     * Yeni hedef acar. Kullanicinin onceki aktif hedefi arsivlenir:
     * ayni anda yalnizca bir hedef gecerlidir.
     */
    @Transactional
    public LearningGoal create(UUID userId, GoalRequest request) {
        validate(request);

        repository.findByUserIdAndStatus(userId, ACTIVE)
                .ifPresent(LearningGoal::archive);

        LocalDate start = request.startDate() != null ? request.startDate() : LocalDate.now();
        if (start.isBefore(LocalDate.now().minusDays(1))) {
            throw new ValidationException("Baslangic tarihi gecmiste olamaz");
        }

        LearningGoal goal = new LearningGoal(
                userId, request.targetLevel(), request.examType(),
                request.totalDays(), request.dailyMinutes(), start);

        return repository.saveAndFlush(goal);
    }

    private void validate(GoalRequest request) {
        if (!LEVELS.contains(request.targetLevel())) {
            throw new ValidationException(
                    "Hedef seviye A1 veya A2 olmali (B1 icerigi henuz hazir degil)");
        }
        if (!EXAMS.contains(request.examType())) {
            throw new ValidationException("Gecersiz sinav secimi");
        }
        // Goethe A2 sinavi A1 hedefiyle anlamsiz olur.
        if ("GOETHE_A2".equals(request.examType()) && !"A2".equals(request.targetLevel())) {
            throw new ValidationException(
                    "Goethe A2 sinavi icin hedef seviyen de A2 olmali");
        }
    }
}
