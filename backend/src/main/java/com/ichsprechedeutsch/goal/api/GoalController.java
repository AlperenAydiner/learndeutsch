package com.ichsprechedeutsch.goal.api;

import com.ichsprechedeutsch.common.security.CurrentUser;
import com.ichsprechedeutsch.goal.LearningGoalService;
import com.ichsprechedeutsch.user.AppUser;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/goals")
public class GoalController {

    private final LearningGoalService service;

    public GoalController(LearningGoalService service) {
        this.service = service;
    }

    /** Aktif hedef. Hedef yoksa 404 doner; frontend bunu "once hedef sec" olarak okur. */
    @GetMapping("/active")
    public GoalResponse active(@CurrentUser AppUser user) {
        return GoalResponse.from(service.activeGoal(user.getId()));
    }

    @GetMapping
    public List<GoalResponse> history(@CurrentUser AppUser user) {
        return service.history(user.getId()).stream().map(GoalResponse::from).toList();
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public GoalResponse create(@CurrentUser AppUser user,
                               @Valid @RequestBody GoalRequest request) {
        return GoalResponse.from(service.create(user.getId(), request));
    }
}
