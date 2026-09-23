package com.ichsprechedeutsch.level.api;

import com.ichsprechedeutsch.common.model.Level;
import com.ichsprechedeutsch.common.model.Skill;
import com.ichsprechedeutsch.common.security.CurrentUser;
import com.ichsprechedeutsch.common.time.Today;
import com.ichsprechedeutsch.config.LevelProperties;
import com.ichsprechedeutsch.level.Evidence;
import com.ichsprechedeutsch.level.GoalCompletion;
import com.ichsprechedeutsch.level.LevelService;
import com.ichsprechedeutsch.level.LevelService.Overview;
import com.ichsprechedeutsch.level.OverallEstimate;
import com.ichsprechedeutsch.level.Reassessment;
import com.ichsprechedeutsch.level.SkillEstimate;
import com.ichsprechedeutsch.level.TierPolicy;
import com.ichsprechedeutsch.level.WorkingLevel;
import com.ichsprechedeutsch.user.AppUser;
import java.time.LocalDate;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Seviye durumu. Her tahmin dayandigi kanitlari listeler (K3). Metinler
 * istemcide "Platform ici degerlendirmeye gore ... civarinda" diliyle
 * gosterilir (K5).
 */
@RestController
@RequestMapping("/api/level")
public class LevelController {

    public record EvidenceView(LocalDate date, Level level, String source, String tier,
                               double score, double maxScore, boolean positive, Boolean contentVerified) {
    }

    public record SkillView(Skill skill, String label, String kind, Level level, Level rank,
                            String confidence, int evidenceCount, List<EvidenceView> basis) {
    }

    public record PlacementView(Level result, LocalDate takenOn, String confidence) {
    }

    public record Response(List<SkillView> skills, OverallEstimate overall, WorkingLevel working,
                           PlacementView placement, Reassessment reassessment, GoalCompletion goal,
                           Level goalTarget) {
    }

    private final LevelService service;
    private final Today today;
    private final TierPolicy tiers;

    public LevelController(LevelService service, Today today, LevelProperties config) {
        this.service = service;
        this.today = today;
        this.tiers = new TierPolicy(config);
    }

    @GetMapping
    public Response get(@CurrentUser AppUser user) {
        Overview o = service.overview(user.getId(), today.of(user));
        List<SkillView> skills = o.skills().values().stream().map(this::view).toList();
        PlacementView placement = o.placement() == null ? null
                : new PlacementView(o.placement().result(), o.placement().takenOn(), o.placement().confidence());
        return new Response(skills, o.overall(), o.working(), placement, o.reassessment(), o.goal(),
                o.goalTarget());
    }

    private SkillView view(SkillEstimate e) {
        List<EvidenceView> basis = e.basis().stream().map(this::view).toList();
        return new SkillView(e.skill(), e.skill().label(), e.kind().name(), e.level(), e.rank(),
                e.confidence().name(), e.evidenceCount(), basis);
    }

    private EvidenceView view(Evidence ev) {
        return new EvidenceView(ev.date(), ev.level(), ev.source().name(), tiers.tierOf(ev).name(),
                ev.score(), ev.maxScore(), tiers.isPositive(ev), ev.contentVerified());
    }
}
