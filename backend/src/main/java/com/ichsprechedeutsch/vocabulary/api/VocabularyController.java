package com.ichsprechedeutsch.vocabulary.api;

import com.ichsprechedeutsch.common.security.CurrentUser;
import com.ichsprechedeutsch.user.AppUser;
import com.ichsprechedeutsch.vocabulary.VocabularyService;
import jakarta.validation.Valid;
import java.util.UUID;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/words")
public class VocabularyController {

    private final VocabularyService service;

    public VocabularyController(VocabularyService service) {
        this.service = service;
    }

    /** Bugunun tekrar oturumu: bekleyen tekrarlar ve kota kadar yeni kelime. */
    @GetMapping("/session")
    public SessionResponse session(@CurrentUser AppUser user) {
        return service.session(user.getId());
    }

    @PostMapping("/{userWordId}/review")
    public ReviewResultResponse review(@CurrentUser AppUser user,
                                       @PathVariable UUID userWordId,
                                       @Valid @RequestBody ReviewAnswerRequest request) {
        return service.review(user.getId(), userWordId, request);
    }
}
