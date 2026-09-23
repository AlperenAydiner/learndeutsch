package com.ichsprechedeutsch.skills.api;

import com.ichsprechedeutsch.common.security.CurrentUser;
import com.ichsprechedeutsch.common.time.Today;
import com.ichsprechedeutsch.config.LevelProperties.CanDoAnswer;
import com.ichsprechedeutsch.skills.SkillPracticeService;
import com.ichsprechedeutsch.skills.SkillPracticeService.ListeningView;
import com.ichsprechedeutsch.skills.SkillPracticeService.ReadingView;
import com.ichsprechedeutsch.skills.SkillPracticeService.ResultView;
import com.ichsprechedeutsch.skills.SkillPracticeService.SpeakingView;
import com.ichsprechedeutsch.skills.SkillPracticeService.TaskView;
import com.ichsprechedeutsch.skills.SkillPracticeService.WritingView;
import com.ichsprechedeutsch.user.AppUser;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Dort becerinin site ici calismasi. Hangi calismanin kanit urettigi
 * SPEC 4.2'de belirlidir; yanitin {@code evidence} ve {@code reasonTr}
 * alanlari bunu kullaniciya da soyler (K3).
 */
@RestController
@RequestMapping("/api/skills")
public class SkillController {

    public record ReadingSubmit(UUID sessionId, Map<String, String> answers, List<Long> interactions) {
    }

    public record ListeningSubmit(UUID sessionId, String dictation, Map<String, String> answers,
                                  List<Long> interactions) {
    }

    public record WritingSubmit(UUID sessionId, String text, Map<String, Integer> rubric,
                                List<Long> interactions) {
    }

    public record SpeakingSubmit(UUID sessionId, Map<String, CanDoAnswer> canDo, List<Long> interactions) {
    }

    private final SkillPracticeService service;
    private final Today today;

    public SkillController(SkillPracticeService service, Today today) {
        this.service = service;
        this.today = today;
    }

    // --- Lesen -----------------------------------------------------------

    @GetMapping("/reading")
    public List<TaskView> readingList(@CurrentUser AppUser user) {
        return service.readingList(user.getId(), today.of(user));
    }

    @PostMapping("/reading/{id}/session")
    public ReadingView readingStart(@CurrentUser AppUser user, @PathVariable String id) {
        return service.startReading(user.getId(), id);
    }

    @PostMapping("/reading/{id}/submit")
    public ResultView readingSubmit(@CurrentUser AppUser user, @PathVariable String id,
                                    @RequestBody ReadingSubmit req) {
        return service.submitReading(user.getId(), id, req.sessionId(), req.answers(),
                req.interactions(), today.of(user));
    }

    // --- Hören -----------------------------------------------------------

    @GetMapping("/listening")
    public List<TaskView> listeningList(@CurrentUser AppUser user) {
        return service.listeningList(user.getId(), today.of(user));
    }

    @PostMapping("/listening/{id}/session")
    public ListeningView listeningStart(@CurrentUser AppUser user, @PathVariable String id) {
        return service.startListening(user.getId(), id);
    }

    @PostMapping("/listening/{id}/submit")
    public ResultView listeningSubmit(@CurrentUser AppUser user, @PathVariable String id,
                                      @RequestBody ListeningSubmit req) {
        return service.submitListening(user.getId(), id, req.sessionId(), req.dictation(), req.answers(),
                req.interactions(), today.of(user));
    }

    // --- Schreiben -------------------------------------------------------

    @GetMapping("/writing")
    public List<TaskView> writingList(@CurrentUser AppUser user) {
        return service.writingList(user.getId(), today.of(user));
    }

    @PostMapping("/writing/{id}/session")
    public WritingView writingStart(@CurrentUser AppUser user, @PathVariable String id) {
        return service.startWriting(user.getId(), id);
    }

    @PostMapping("/writing/{id}/submit")
    public ResultView writingSubmit(@CurrentUser AppUser user, @PathVariable String id,
                                    @RequestBody WritingSubmit req) {
        return service.submitWriting(user.getId(), id, req.sessionId(), req.text(), req.rubric(),
                req.interactions(), today.of(user));
    }

    // --- Sprechen --------------------------------------------------------

    @GetMapping("/speaking")
    public List<TaskView> speakingList(@CurrentUser AppUser user) {
        return service.speakingList(user.getId(), today.of(user));
    }

    @PostMapping("/speaking/{id}/session")
    public SpeakingView speakingStart(@CurrentUser AppUser user, @PathVariable String id) {
        return service.startSpeaking(user.getId(), id);
    }

    @PostMapping("/speaking/{id}/submit")
    public ResultView speakingSubmit(@CurrentUser AppUser user, @PathVariable String id,
                                     @RequestBody SpeakingSubmit req) {
        return service.submitSpeaking(user.getId(), id, req.sessionId(), req.canDo(),
                req.interactions(), today.of(user));
    }
}
