package com.ichsprechedeutsch.goal;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface LearningGoalRepository extends JpaRepository<LearningGoal, UUID> {

    Optional<LearningGoal> findByUserIdAndStatus(UUID userId, String status);

    List<LearningGoal> findByUserIdOrderByCreatedAtDesc(UUID userId);
}
