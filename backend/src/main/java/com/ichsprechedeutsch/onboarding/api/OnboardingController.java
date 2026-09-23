package com.ichsprechedeutsch.onboarding.api;

import com.ichsprechedeutsch.common.model.Level;
import com.ichsprechedeutsch.common.model.StartMode;
import com.ichsprechedeutsch.common.security.CurrentUser;
import com.ichsprechedeutsch.common.time.Today;
import com.ichsprechedeutsch.onboarding.OnboardingService;
import com.ichsprechedeutsch.onboarding.OnboardingService.State;
import com.ichsprechedeutsch.onboarding.Purpose;
import com.ichsprechedeutsch.user.AppUser;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Onboarding + hedef ve ritim degisikligi. Her yazma guncel durumu dondurur. */
@RestController
@RequestMapping("/api/onboarding")
public class OnboardingController {

    public record StartRequest(StartMode startMode) {
    }

    public record CompleteRequest(Level target, Purpose purpose, Integer dailyMinutes, Integer daysPerWeek) {
    }

    public record GoalRequest(Level target, Purpose purpose) {
    }

    public record RhythmRequest(Integer dailyMinutes, Integer daysPerWeek) {
    }

    private final OnboardingService service;
    private final Today today;

    public OnboardingController(OnboardingService service, Today today) {
        this.service = service;
        this.today = today;
    }

    @GetMapping
    public State state(@CurrentUser AppUser user) {
        return service.state(user.getId(), today.of(user));
    }

    @PutMapping("/start")
    public State start(@CurrentUser AppUser user, @RequestBody StartRequest request) {
        service.saveStartMode(user.getId(), request.startMode());
        return state(user);
    }

    @PostMapping("/complete")
    public State complete(@CurrentUser AppUser user, @RequestBody CompleteRequest r) {
        service.complete(user.getId(), r.target(), r.purpose(), r.dailyMinutes(), r.daysPerWeek());
        return state(user);
    }

    @PutMapping("/goal")
    public State goal(@CurrentUser AppUser user, @RequestBody GoalRequest r) {
        service.saveGoal(user.getId(), r.target(), r.purpose());
        return state(user);
    }

    @PutMapping("/rhythm")
    public State rhythm(@CurrentUser AppUser user, @RequestBody RhythmRequest r) {
        service.saveRhythm(user.getId(), r.dailyMinutes(), r.daysPerWeek());
        return state(user);
    }
}
