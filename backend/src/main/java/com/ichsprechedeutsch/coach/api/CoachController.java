package com.ichsprechedeutsch.coach.api;

import com.ichsprechedeutsch.coach.CoachService;
import com.ichsprechedeutsch.coach.CoachService.View;
import com.ichsprechedeutsch.common.security.CurrentUser;
import com.ichsprechedeutsch.common.time.Today;
import com.ichsprechedeutsch.user.AppUser;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Koc: bugunun onerileri (en fazla 3), haftalik ve gunluk plan, aliskanlik ozeti. */
@RestController
@RequestMapping("/api/coach")
public class CoachController {

    private final CoachService service;
    private final Today today;

    public CoachController(CoachService service, Today today) {
        this.service = service;
        this.today = today;
    }

    @GetMapping
    public View get(@CurrentUser AppUser user) {
        return service.view(user.getId(), today.of(user));
    }
}
