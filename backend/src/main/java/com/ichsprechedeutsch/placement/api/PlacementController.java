package com.ichsprechedeutsch.placement.api;

import com.ichsprechedeutsch.common.security.CurrentUser;
import com.ichsprechedeutsch.common.time.Today;
import com.ichsprechedeutsch.placement.PlacementService;
import com.ichsprechedeutsch.placement.PlacementService.AnswerOutcome;
import com.ichsprechedeutsch.placement.PlacementService.BlockView;
import com.ichsprechedeutsch.user.AppUser;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/placement")
public class PlacementController {

    /** @param answers soru id -> secilen sik metni */
    public record AnswerRequest(Map<String, String> answers, List<Long> interactions) {
    }

    private final PlacementService service;
    private final Today today;

    public PlacementController(PlacementService service, Today today) {
        this.service = service;
        this.today = today;
    }

    /** Yeni test baslatir (acik test varsa yarida birakilmis sayilir). */
    @PostMapping("/start")
    public BlockView start(@CurrentUser AppUser user) {
        return service.start(user.getId());
    }

    @PostMapping("/{sessionId}/answers")
    public AnswerOutcome answer(@CurrentUser AppUser user, @PathVariable UUID sessionId,
                                @RequestBody AnswerRequest request) {
        return service.answer(user.getId(), sessionId,
                request.answers() == null ? Map.of() : request.answers(),
                request.interactions(), today.of(user));
    }
}
