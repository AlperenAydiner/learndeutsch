package com.ichsprechedeutsch.plan.api;

import com.ichsprechedeutsch.common.security.CurrentUser;
import com.ichsprechedeutsch.plan.PlanService;
import com.ichsprechedeutsch.user.AppUser;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/plan")
public class PlanController {

    private final PlanService service;

    public PlanController(PlanService service) {
        this.service = service;
    }

    /**
     * Aktif hedefe gore program uretir.
     *
     * Var olan aktif plan RECALCULATED olur; gecmis planlar silinmez,
     * boylece kullanici neyi ne zaman degistirdigini izleyebilir.
     */
    @PostMapping("/generate")
    @ResponseStatus(HttpStatus.CREATED)
    public PlanResponse generate(@CurrentUser AppUser user) {
        service.generateForUser(user.getId());
        return service.loadActivePlan(user.getId());
    }

    @GetMapping
    public PlanResponse active(@CurrentUser AppUser user) {
        return service.loadActivePlan(user.getId());
    }
}
