package com.ichsprechedeutsch.placement;

import com.ichsprechedeutsch.activity.ActivityRules;
import com.ichsprechedeutsch.activity.ActivityService;
import com.ichsprechedeutsch.activity.ActivityService.SiteActivity;
import com.ichsprechedeutsch.activity.ActivityStore;
import com.ichsprechedeutsch.activity.ActivityType;
import com.ichsprechedeutsch.common.error.NotFoundException;
import com.ichsprechedeutsch.common.error.ValidationException;
import com.ichsprechedeutsch.common.model.Level;
import com.ichsprechedeutsch.config.ActivityProperties;
import com.ichsprechedeutsch.config.PlacementProperties;
import com.ichsprechedeutsch.content.ContentCatalog;
import com.ichsprechedeutsch.content.Question;
import com.ichsprechedeutsch.level.LevelStore;
import com.ichsprechedeutsch.onboarding.OnboardingStore;
import com.ichsprechedeutsch.placement.PlacementEngine.Block;
import com.ichsprechedeutsch.placement.PlacementEngine.Step;
import com.ichsprechedeutsch.placement.PlacementStore.BlockState;
import com.ichsprechedeutsch.placement.PlacementStore.Session;
import com.ichsprechedeutsch.placement.PlacementStore.State;
import java.security.SecureRandom;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.ObjectMapper;

/**
 * Yerlestirme testi (SPEC 4.1). Sonuc yalniz KABA baslangic noktasidir:
 * dusuk guvenle kaydedilir, beceri profilini doldurmaz (kanit uretmez).
 * Tamamlanan test aktivite gecmisine otomatik yazilir (6.1).
 */
@Service
public class PlacementService {

    /** Istemciye giden soru: dogru cevap YOK. */
    public record QuestionView(String id, String prompt, List<String> options, String kind) {
    }

    public record BlockView(UUID sessionId, int blockNumber, List<QuestionView> questions,
                            int askedSoFar, int maxQuestions) {
    }

    public record ReviewItem(String prompt, String yourAnswer, String correctAnswer, boolean correct,
                             String explanationTr) {
    }

    /**
     * @param result      A0 = "A1'in altinda"
     * @param goalReached sonuc aktif hedefe esit ya da ustundeyse o hedef; degilse null.
     *                    Hedef otomatik degismez; istemci kullaniciya sorar (K6).
     */
    public record ResultView(Level result, String reason, int correct, int total, List<Block> blocks,
                             List<ReviewItem> review, Level goalReached) {
    }

    /** Cevap sonrasi: ya yeni blok ya sonuc. */
    public record AnswerOutcome(BlockView next, ResultView result) {
    }

    private final PlacementStore store;
    private final ContentCatalog catalog;
    private final PlacementEngine engine;
    private final PlacementProperties config;
    private final ActivityStore activityStore;
    private final ActivityService activities;
    private final ActivityProperties activityConfig;
    private final LevelStore levels;
    private final OnboardingStore onboarding;
    private final ObjectMapper mapper;
    private final Random random = new SecureRandom();

    public PlacementService(PlacementStore store, ContentCatalog catalog, PlacementProperties config,
                            ActivityStore activityStore, ActivityService activities,
                            ActivityProperties activityConfig, LevelStore levels, OnboardingStore onboarding,
                            ObjectMapper mapper) {
        this.store = store;
        this.catalog = catalog;
        this.engine = new PlacementEngine(config);
        this.config = config;
        this.activityStore = activityStore;
        this.activities = activities;
        this.activityConfig = activityConfig;
        this.levels = levels;
        this.onboarding = onboarding;
        this.mapper = mapper;
    }

    @Transactional
    public BlockView start(UUID userId) {
        store.abandonOpen(userId);
        UUID studySession = activityStore.startSession(userId, "PLACEMENT");
        Step first = engine.decide(List.of());
        List<Question> questions = pick(userId, first.nextLevel(), List.of());
        State state = new State(List.of(newBlock(first.nextLevel(), questions)));
        UUID id = store.create(userId, studySession, state);
        return view(id, 1, questions, 0);
    }

    /**
     * Mevcut blogun cevaplari. Cevaplanmayan soru yanlis sayilir.
     *
     * @param interactions istemcinin kaydettigi etkilesim anlari (epoch ms); aktif sure icin
     */
    @Transactional
    public AnswerOutcome answer(UUID userId, UUID sessionId, Map<String, String> answers,
                                List<Long> interactions, LocalDate today) {
        Session session = store.find(userId, sessionId)
                .orElseThrow(() -> new NotFoundException("Test oturumu bulunamadı"));
        if (!"IN_PROGRESS".equals(session.status())) {
            throw new ValidationException("Bu test tamamlanmış ya da yarıda bırakılmış; yeniden başla");
        }

        List<BlockState> blocks = new ArrayList<>(session.state().blocks());
        BlockState current = blocks.getLast();
        if (current.answered()) {
            throw new IllegalStateException("Acik blok yok");
        }
        Set<String> expected = new HashSet<>(current.questionIds());
        for (String qid : answers.keySet()) {
            if (!expected.contains(qid)) {
                throw new ValidationException("Bu bloğa ait olmayan soru gönderildi");
            }
        }

        int correct = 0;
        Map<String, String> given = new LinkedHashMap<>();
        for (String qid : current.questionIds()) {
            String a = answers.get(qid);
            given.put(qid, a);
            if (a != null && isCorrect(question(qid), a)) {
                correct++;
            }
        }
        List<Instant> events = interactions == null ? List.of()
                : interactions.stream().sorted().map(Instant::ofEpochMilli).toList();
        long active = ActivityRules.activeSeconds(events, activityConfig.idleGapMinutes());
        blocks.set(blocks.size() - 1, new BlockState(current.level(), current.questionIds(), given, correct, active));

        List<Block> done = blocks.stream()
                .map(b -> new Block(b.level(), b.correct(), b.questionIds().size()))
                .toList();
        Step step = engine.decide(done);

        if (!step.finished()) {
            List<String> asked = blocks.stream().flatMap(b -> b.questionIds().stream()).toList();
            List<Question> next = pick(userId, step.nextLevel(), asked);
            if (next.isEmpty()) {
                throw new IllegalStateException(step.nextLevel() + " seviyesinde yerlestirme sorusu yok");
            }
            blocks.add(newBlock(step.nextLevel(), next));
            State state = new State(blocks);
            store.saveState(sessionId, state);
            return new AnswerOutcome(view(sessionId, blocks.size(), next, asked.size()), null);
        }

        State state = new State(blocks);
        store.complete(sessionId, state, step.result());
        return new AnswerOutcome(null, finish(userId, session, state, step, done, today));
    }

    // ------------------------------------------------------------------

    private ResultView finish(UUID userId, Session session, State state, Step step, List<Block> done,
                              LocalDate today) {
        int correct = done.stream().mapToInt(Block::correct).sum();
        int total = done.stream().mapToInt(Block::total).sum();
        long active = state.blocks().stream().mapToLong(BlockState::activeSeconds).sum();

        Map<String, Object> details = new LinkedHashMap<>();
        details.put("blocks", done);
        details.put("reason", step.reason());
        levels.insertAssessment(userId, "PLACEMENT", step.result(), correct, total,
                mapper.writeValueAsString(details), today, session.id());

        if (session.studySessionId() != null) {
            activityStore.finishSession(userId, session.studySessionId(), active);
        }
        // Yerlestirme testi kanit uretmez (4.1); yalniz calisma gecmisine yazilir.
        activities.recordSite(userId, new SiteActivity(ActivityType.SEVIYE_TESTI, today, active,
                null, null, null, null, null, session.studySessionId()));

        List<ReviewItem> review = new ArrayList<>();
        for (BlockState b : state.blocks()) {
            for (String qid : b.questionIds()) {
                Question q = question(qid);
                String yours = b.answers() == null ? null : b.answers().get(qid);
                review.add(new ReviewItem(q.prompt(), yours, q.answer(),
                        yours != null && isCorrect(q, yours), q.explanationTr()));
            }
        }

        Level goalReached = onboarding.activeGoal(userId)
                .map(OnboardingStore.Goal::target)
                .filter(target -> step.result().isAtLeast(target))
                .orElse(null);

        return new ResultView(step.result(), step.reason(), correct, total, done, review, goalReached);
    }

    private List<Question> pick(UUID userId, Level level, List<String> askedThisSession) {
        return QuestionPicker.pick(catalog.placementPool(level), config.blockSize(),
                store.seenQuestionIds(userId), askedThisSession, random);
    }

    private static BlockState newBlock(Level level, List<Question> questions) {
        return new BlockState(level, questions.stream().map(Question::id).toList(), null, null, 0);
    }

    private BlockView view(UUID sessionId, int number, List<Question> questions, int askedSoFar) {
        List<QuestionView> qs = questions.stream()
                .map(q -> new QuestionView(q.id(), q.prompt(), q.options(), q.placementKind()))
                .toList();
        return new BlockView(sessionId, number, qs, askedSoFar, config.maxQuestions());
    }

    private Question question(String id) {
        return catalog.question(id)
                .orElseThrow(() -> new IllegalStateException("Icerikte olmayan soru: " + id));
    }

    private static boolean isCorrect(Question q, String answer) {
        return q.answer().equals(answer)
                || (q.acceptedAnswers() != null && q.acceptedAnswers().contains(answer));
    }
}
