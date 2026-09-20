package com.ichsprechedeutsch.assessment.api;

import com.ichsprechedeutsch.assessment.QuizService;
import com.ichsprechedeutsch.common.security.CurrentUser;
import com.ichsprechedeutsch.user.AppUser;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/quiz")
public class QuizController {

    private final QuizService service;

    public QuizController(QuizService service) {
        this.service = service;
    }

    @GetMapping("/today")
    public QuizResponse today(@CurrentUser AppUser user) {
        return service.todaysQuiz(user.getId());
    }

    @PostMapping("/submit")
    public QuizResultResponse submit(@CurrentUser AppUser user,
                                     @Valid @RequestBody QuizSubmitRequest request) {
        return service.submit(user.getId(), request);
    }
}
