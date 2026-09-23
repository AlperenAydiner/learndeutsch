package com.ichsprechedeutsch.exam.api;

import com.ichsprechedeutsch.common.security.CurrentUser;
import com.ichsprechedeutsch.common.time.Today;
import com.ichsprechedeutsch.exam.ExamService;
import com.ichsprechedeutsch.exam.ExamService.View;
import com.ichsprechedeutsch.user.AppUser;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Sinav bolumu: hedef seviyedeki pratikler ve resmi sinav yonlendirmeleri. */
@RestController
@RequestMapping("/api/exam")
public class ExamController {

    private final ExamService service;
    private final Today today;

    public ExamController(ExamService service, Today today) {
        this.service = service;
        this.today = today;
    }

    @GetMapping
    public View get(@CurrentUser AppUser user) {
        return service.view(user.getId(), today.of(user));
    }
}
