package com.ichsprechedeutsch.progress.api;

import com.ichsprechedeutsch.common.security.CurrentUser;
import com.ichsprechedeutsch.progress.ProgressService;
import com.ichsprechedeutsch.user.AppUser;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/progress")
public class ProgressController {

    private final ProgressService service;

    public ProgressController(ProgressService service) {
        this.service = service;
    }

    @GetMapping
    public ProgressResponse progress(@CurrentUser AppUser user) {
        return service.load(user.getId());
    }
}
