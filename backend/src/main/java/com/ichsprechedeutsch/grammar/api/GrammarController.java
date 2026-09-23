package com.ichsprechedeutsch.grammar.api;

import com.ichsprechedeutsch.common.security.CurrentUser;
import com.ichsprechedeutsch.common.time.Today;
import com.ichsprechedeutsch.grammar.GrammarService;
import com.ichsprechedeutsch.grammar.GrammarService.AnswerView;
import com.ichsprechedeutsch.grammar.GrammarService.ErrorView;
import com.ichsprechedeutsch.grammar.GrammarService.LessonView;
import com.ichsprechedeutsch.grammar.GrammarService.Stats;
import com.ichsprechedeutsch.grammar.GrammarService.TopicView;
import com.ichsprechedeutsch.user.AppUser;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** Gramer: konu agaci, konu dongusu, hata hafizasi (SPEC 8.2, 8.4). */
@RestController
@RequestMapping("/api/grammar")
public class GrammarController {

    public record AnswerRequest(UUID sessionId, String topicId, String itemId, String given) {
    }

    /** @param completed yoksa (null) konu tamamlanmis sayilmaz. */
    public record FinishRequest(List<Long> interactions, Boolean completed, String topicId) {
    }

    private final GrammarService service;
    private final Today today;

    public GrammarController(GrammarService service, Today today) {
        this.service = service;
        this.today = today;
    }

    @GetMapping("/topics")
    public List<TopicView> topics(@CurrentUser AppUser user) {
        return service.topics(user.getId(), today.of(user));
    }

    @PostMapping("/topics/{id}/session")
    public LessonView start(@CurrentUser AppUser user, @PathVariable String id) {
        return service.start(user.getId(), id, today.of(user));
    }

    @PostMapping("/answer")
    public AnswerView answer(@CurrentUser AppUser user, @RequestBody AnswerRequest req) {
        return service.answer(user.getId(), req.sessionId(), req.topicId(), req.itemId(), req.given(),
                today.of(user));
    }

    /** Oturumu bitirir; {@code completed} ise konu tamamlanmis sayilir. */
    @PostMapping("/session/{id}/finish")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void finish(@CurrentUser AppUser user, @PathVariable UUID id,
                       @RequestBody(required = false) FinishRequest req) {
        if (req != null && Boolean.TRUE.equals(req.completed()) && req.topicId() != null) {
            service.complete(user.getId(), req.topicId(), today.of(user));
        }
        service.finish(user.getId(), id, req == null ? List.of() : req.interactions(), today.of(user));
    }

    @GetMapping("/stats")
    public Stats stats(@CurrentUser AppUser user) {
        return service.stats(user.getId(), today.of(user));
    }

    @GetMapping("/errors")
    public List<ErrorView> errors(@CurrentUser AppUser user) {
        return service.errorList(user.getId());
    }
}
