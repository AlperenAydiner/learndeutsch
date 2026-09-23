package com.ichsprechedeutsch.article.api;

import com.ichsprechedeutsch.article.ArticleService;
import com.ichsprechedeutsch.article.ArticleService.AnswerView;
import com.ichsprechedeutsch.article.ArticleService.SessionView;
import com.ichsprechedeutsch.article.ArticleService.Stats;
import com.ichsprechedeutsch.common.security.CurrentUser;
import com.ichsprechedeutsch.common.time.Today;
import com.ichsprechedeutsch.user.AppUser;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** Artikel pratigi: der/die/das (SPEC 7). */
@RestController
@RequestMapping("/api/article")
public class ArticleController {

    /** Bir oturumdaki varsayilan soru sayisi. */
    private static final int VARSAYILAN_ADET = 12;
    private static final int EN_FAZLA = 30;

    public record AnswerRequest(UUID sessionId, String wordId, String given) {
    }

    public record FinishRequest(List<Long> interactions) {
    }

    private final ArticleService service;
    private final Today today;

    public ArticleController(ArticleService service, Today today) {
        this.service = service;
        this.today = today;
    }

    @PostMapping("/session")
    public SessionView start(@CurrentUser AppUser user,
                             @RequestParam(required = false) Integer count) {
        int adet = count == null ? VARSAYILAN_ADET : Math.min(Math.max(count, 1), EN_FAZLA);
        return service.start(user.getId(), today.of(user), adet);
    }

    @PostMapping("/answer")
    public AnswerView answer(@CurrentUser AppUser user, @RequestBody AnswerRequest req) {
        return service.answer(user.getId(), req.sessionId(), req.wordId(), req.given(), today.of(user));
    }

    @PostMapping("/session/{id}/finish")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void finish(@CurrentUser AppUser user, @PathVariable UUID id,
                       @RequestBody(required = false) FinishRequest req) {
        service.finish(user.getId(), id, req == null ? List.of() : req.interactions(), today.of(user));
    }

    @GetMapping("/stats")
    public Stats stats(@CurrentUser AppUser user) {
        return service.stats(user.getId());
    }
}
