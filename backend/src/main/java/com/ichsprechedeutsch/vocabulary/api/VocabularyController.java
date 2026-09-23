package com.ichsprechedeutsch.vocabulary.api;

import com.ichsprechedeutsch.common.security.CurrentUser;
import com.ichsprechedeutsch.common.time.Today;
import com.ichsprechedeutsch.srs.Grade;
import com.ichsprechedeutsch.user.AppUser;
import com.ichsprechedeutsch.vocabulary.VocabularyService;
import com.ichsprechedeutsch.vocabulary.VocabularyService.AnswerView;
import com.ichsprechedeutsch.vocabulary.VocabularyService.SessionView;
import com.ichsprechedeutsch.vocabulary.VocabularyService.Stats;
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

/** Kelime oturumu: bugunun tekrarlari + gunluk yeni kelimeler (SPEC 8.1). */
@RestController
@RequestMapping("/api/words")
public class VocabularyController {

    /**
     * @param articleGiven isimlerde secilen artikel (der/die/das), yoksa null
     * @param sessionId    oturum; tek kart calisiliyorsa null olabilir
     */
    public record AnswerRequest(UUID sessionId, String wordId, Grade grade, String articleGiven) {
    }

    /** @param interactions istemcinin kaydettigi etkilesim anlari (epoch ms) */
    public record FinishRequest(List<Long> interactions) {
    }

    private final VocabularyService service;
    private final Today today;

    public VocabularyController(VocabularyService service, Today today) {
        this.service = service;
        this.today = today;
    }

    @PostMapping("/session")
    public SessionView start(@CurrentUser AppUser user) {
        return service.start(user.getId(), today.of(user));
    }

    @PostMapping("/answer")
    public AnswerView answer(@CurrentUser AppUser user, @RequestBody AnswerRequest req) {
        return service.answer(user.getId(), req.sessionId(), req.wordId(), req.grade(),
                req.articleGiven(), today.of(user));
    }

    @PostMapping("/session/{id}/finish")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void finish(@CurrentUser AppUser user, @PathVariable UUID id,
                       @RequestBody(required = false) FinishRequest req) {
        service.finish(user.getId(), id, req == null ? List.of() : req.interactions(), today.of(user));
    }

    @GetMapping("/stats")
    public Stats stats(@CurrentUser AppUser user) {
        return service.stats(user.getId(), today.of(user));
    }
}
