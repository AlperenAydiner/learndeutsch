package com.ichsprechedeutsch.coach;

import com.ichsprechedeutsch.coach.CoachInput.ArticleWindow;
import com.ichsprechedeutsch.coach.CoachInput.DoneActivity;
import com.ichsprechedeutsch.coach.CoachInput.ErrorTag;
import com.ichsprechedeutsch.coach.CoachInput.GrammarWindow;
import com.ichsprechedeutsch.coach.Recommendation.Action;
import com.ichsprechedeutsch.coach.Recommendation.Category;
import com.ichsprechedeutsch.common.model.Level;
import com.ichsprechedeutsch.common.model.StartMode;
import com.ichsprechedeutsch.config.CoachProperties;
import com.ichsprechedeutsch.config.SrsProperties;
import com.ichsprechedeutsch.level.SkillEstimate;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Kural tabanli koc (SPEC 5.3). Saf: girdisi {@link CoachInput}, ciktisi
 * oneri listesi. Kurallar config sirasiyla (coach.rule-order) denenir;
 * ekranda en fazla N oneri, ayni kategoriden en fazla bir tane.
 *
 * <p>Her oneri kullaniciya gosterilen gerekceyi ({@code reason}) ve bu
 * gerekcenin dayandigi ham veriyi ({@code basis}) tasir (K3). Klise
 * motivasyon cumlesi yok; gerekce veriye dayanir (5.5).
 */
public final class RuleBasedCoach implements Coach {

    /** Sitedeki sayfalar; modul yoksa oneri "disarida yap" olur. */
    static final String PLACEMENT_PAGE = "/app/yerlestirme.html";

    private final CoachProperties config;
    private final SrsProperties srs;

    public RuleBasedCoach(CoachProperties config, SrsProperties srs) {
        this.config = config;
        this.srs = srs;
    }

    @Override
    public List<Recommendation> advise(CoachInput in) {
        List<Recommendation> candidates = new ArrayList<>();
        List<String> order = config.ruleOrder();
        for (int i = 0; i < order.size(); i++) {
            rule(order.get(i), i, in).ifPresent(candidates::add);
        }
        if (in.activities().isEmpty()) {
            candidates.addAll(startPlan(in, order.size()));
        }

        List<Recommendation> out = new ArrayList<>();
        Map<Category, Integer> perCategory = new LinkedHashMap<>();
        for (Recommendation r : candidates) {
            if (out.size() >= config.maxRecommendations()) {
                break;
            }
            int used = perCategory.getOrDefault(r.category(), 0);
            if (used >= config.maxPerCategory()) {
                continue;
            }
            perCategory.put(r.category(), used + 1);
            out.add(r);
        }
        return out;
    }

    private Optional<Recommendation> rule(String name, int priority, CoachInput in) {
        boolean a0 = in.working().level() == Level.A0;
        return switch (name) {
            case "ASSESSMENT" -> assessment(priority, in);
            case "COMEBACK" -> comeback(priority, in);
            case "DUE_REVIEWS" -> dueReviews(priority, in);
            case "RECURRING_ERROR" -> recurringError(priority, in);
            case "WEAK_GRAMMAR" -> weakGrammar(priority, in);
            // SPEC 5.3: calisma seviyesi A0 iken 5. ve 6. kurallar calismaz.
            case "MISSING_EVIDENCE" -> a0 ? Optional.empty() : missingEvidence(priority, in);
            case "IDLE_SKILL" -> a0 ? Optional.empty() : idleSkill(priority, in);
            case "WEAK_ARTICLE" -> weakArticle(priority, in);
            case "NEAR_GOAL" -> nearGoal(priority, in);
            default -> throw new IllegalStateException("Bilinmeyen koc kurali: " + name);
        };
    }

    // --- Kurallar --------------------------------------------------------

    /** SPEC 4.6 / 4.7: yerlestirme testi onerisi. */
    private Optional<Recommendation> assessment(int p, CoachInput in) {
        if (in.startMode() == StartMode.SOME_KNOWLEDGE && !in.placementDone()) {
            return Optional.of(new Recommendation("ASSESSMENT", Category.ASSESSMENT, null,
                    "Yerleştirme testini çöz", in.placementMinutes(), p,
                    "Bir miktar Almanca bildiğini söyledin ama henüz test yapmadın; öneriler şimdilik varsayılan "
                            + in.working().level() + " düzeyinden başlıyor.",
                    basis("placementDone", false, "workingLevel", in.working().level()),
                    Action.site(PLACEMENT_PAGE)));
        }
        if (in.reassessment() != null && in.reassessment().due()) {
            String why = "DAYS".equals(in.reassessment().reason())
                    ? "Son değerlendirmenden bu yana " + in.reassessment().daysSince() + " gün geçti."
                    : "Son değerlendirmenden bu yana " + in.reassessment().newEvidence() + " yeni sonuç girdin.";
            return Optional.of(new Recommendation("ASSESSMENT", Category.ASSESSMENT, null,
                    "Yeniden değerlendirme: yerleştirme testi", in.placementMinutes(), p, why,
                    basis("daysSince", in.reassessment().daysSince(), "newEvidence", in.reassessment().newEvidence()),
                    Action.site(PLACEMENT_PAGE)));
        }
        return Optional.empty();
    }

    /** Kural 1: bir suredir hic calismadiysa kisa, gecmise dayali geri donus. */
    private Optional<Recommendation> comeback(int p, CoachInput in) {
        Optional<LocalDate> last = lastActive(in);
        if (last.isEmpty()) {
            return Optional.empty();
        }
        long idle = ChronoUnit.DAYS.between(last.get(), in.today());
        if (idle < config.comebackDays()) {
            return Optional.empty();
        }
        int minutes = roundDown((int) Math.round(in.dailyMinutes() * config.comebackDayFraction()));
        minutes = Math.max(minutes, config.planMinItemMinutes());
        StudyType favourite = mostStudied(in).orElse(StudyType.KELIME);
        String reviews = in.dueReviews() > 0
                ? " Biriken " + in.dueReviews() + " tekrarı " + config.comebackSpreadDays()
                        + " güne yayıyoruz; bugün " + comebackReviewsToday(in) + " tanesi."
                : "";
        return Optional.of(new Recommendation("COMEBACK", Category.COMEBACK, favourite,
                "Kısa bir dönüş: " + minutes + " dk " + favourite.label(), minutes, p,
                "Son çalışman " + idle + " gün önceydi. En çok çalıştığın tür " + favourite.label()
                        + "; oradan hafif başla." + reviews,
                basis("daysIdle", idle, "lastActive", last.get(), "favourite", favourite),
                actionFor(favourite, in, false)));
    }

    /** Kural 2: zamani gelmis kelime tekrari veya gramer kontrolu. */
    private Optional<Recommendation> dueReviews(int p, CoachInput in) {
        int due = in.dueReviews();
        int checks = in.dueGrammarChecks();
        if (due <= 0 && checks <= 0) {
            return Optional.empty();
        }
        int today = comebackActive(in) ? comebackReviewsToday(in) : Math.min(due, srs.dailyReviewLimit());
        String title = due > 0
                ? "Tekrar zamanı: " + today + " kelime" + (checks > 0 ? " + " + checks + " gramer kontrolü" : "")
                : checks + " gramer kontrolü";
        String limitNote = due > today ? " (toplam " + due + "; günlük sınır " + srs.dailyReviewLimit() + ")" : "";
        int minutes = Math.max(config.planMinItemMinutes(), roundUp(in.dueReviewMinutes()));
        StudyType type = due > 0 ? StudyType.KELIME : StudyType.GRAMER;
        return Optional.of(new Recommendation("DUE_REVIEWS", Category.REVIEW, type, title, minutes, p,
                "Zamanı gelmiş " + (due > 0 ? due + " kelime tekrarın" : "") + (due > 0 && checks > 0 ? " ve " : "")
                        + (checks > 0 ? checks + " gramer kontrolün" : "") + " var" + limitNote + ".",
                basis("dueReviews", due, "dueGrammarChecks", checks, "today", today),
                actionFor(type, in, false)));
    }

    /** Kural 3: ayni hata etiketi birden fazla farkli oturumda. */
    private Optional<Recommendation> recurringError(int p, CoachInput in) {
        return in.recurringErrors().stream()
                .filter(e -> !"RESOLVED".equals(e.status()))
                .filter(e -> e.distinctSessions() >= config.recurringErrorSessions())
                .max(Comparator.comparingInt(ErrorTag::distinctSessions))
                .map(e -> new Recommendation("RECURRING_ERROR", Category.GRAMMAR, StudyType.GRAMER,
                        "Hedefli tekrar: " + e.titleTr(), shareMinutes(StudyType.GRAMER, in), p,
                        e.titleTr() + " hatası " + e.distinctSessions() + " farklı oturumda tekrarlandı.",
                        basis("tag", e.tag(), "sessions", e.distinctSessions(), "status", e.status()),
                        topicAction(e.tag(), in)));
    }

    /** Kural 4: bir gramer konusunda pencere ici basari esigin altinda. */
    private Optional<Recommendation> weakGrammar(int p, CoachInput in) {
        CoachProperties.Window w = config.grammar();
        return in.grammarWindows().stream()
                .filter(g -> g.answers() >= w.minAnswers())
                .filter(g -> g.ratio() < w.threshold())
                .min(Comparator.comparingDouble(GrammarWindow::ratio))
                .map(g -> new Recommendation("WEAK_GRAMMAR", Category.GRAMMAR, StudyType.GRAMER,
                        "Konu tekrarı: " + g.titleTr(), shareMinutes(StudyType.GRAMER, in), p,
                        "Son " + g.answers() + " " + g.titleTr() + " yanıtında %" + pct(g.ratio())
                                + " doğru (eşik %" + pct(w.threshold()) + ").",
                        basis("topic", g.topicId(), "answers", g.answers(), "correct", g.correct()),
                        topicAction(g.topicId(), in)));
    }

    /** Kural 5: bir becerinin hic kaniti yok. */
    private Optional<Recommendation> missingEvidence(int p, CoachInput in) {
        return bySharesDesc(in).stream()
                .filter(t -> t.skill().isPresent())
                .filter(t -> !in.skills().getOrDefault(t.skill().get(), SkillEstimate.none(t.skill().get())).hasData())
                .findFirst()
                .map(t -> new Recommendation("MISSING_EVIDENCE", Category.SKILL, t,
                        t.label() + " için ilk sonucunu üret", shareMinutes(t, in), p,
                        t.label() + " için hiç kanıt yok; bu beceride seviye tahmini yapılamıyor.",
                        basis("skill", t.skill().get(), "evidenceCount", 0),
                        actionFor(t, in, true)));
    }

    /** Kural 6: bir beceri bir suredir hic calisilmadi. */
    private Optional<Recommendation> idleSkill(int p, CoachInput in) {
        LocalDate since = in.today().minusDays(config.skillIdleDays());
        return bySharesDesc(in).stream()
                .filter(t -> t.skill().isPresent())
                .filter(t -> in.activities().stream()
                        .noneMatch(a -> a.type() == t && a.date().isAfter(since)))
                .findFirst()
                .map(t -> new Recommendation("IDLE_SKILL", Category.SKILL, t,
                        t.label() + " zamanı", shareMinutes(t, in), p,
                        "Son " + config.skillIdleDays() + " günde " + t.label() + " çalışmadın.",
                        basis("skill", t.skill().get(), "idleDays", config.skillIdleDays()),
                        actionFor(t, in, false)));
    }

    /** Kural 7: bir artikel turunde basari esigin altinda. */
    private Optional<Recommendation> weakArticle(int p, CoachInput in) {
        CoachProperties.Window w = config.article();
        return in.articleWindows().entrySet().stream()
                .filter(e -> e.getValue().answers() >= w.minAnswers())
                .filter(e -> e.getValue().ratio() < w.threshold())
                .min(Comparator.comparingDouble(e -> e.getValue().ratio()))
                .map(e -> {
                    ArticleWindow a = e.getValue();
                    return new Recommendation("WEAK_ARTICLE", Category.ARTICLE, StudyType.KELIME,
                            "Artikel pratiği: " + e.getKey(), shareMinutes(StudyType.KELIME, in), p, "'" + e.getKey() + "' artikelinde son " + a.answers() + " yanıtta %" + pct(a.ratio())
                                    + " doğru (eşik %" + pct(w.threshold()) + ").",
                            basis("article", e.getKey(), "answers", a.answers(), "correct", a.correct()),
                            articleAction(in));
                });
    }

    /** Kural 8: hedefe yakin -> sinav pratigini artir. */
    private Optional<Recommendation> nearGoal(int p, CoachInput in) {
        if (in.goal() == null || !in.goal().nearGoal() || in.goal().complete()) {
            return Optional.empty();
        }
        long reached = in.goal().skills().stream().filter(s -> s.estimateReached()).count();
        return Optional.of(new Recommendation("NEAR_GOAL", Category.EXAM, null,
                "Sınav pratiğini artır",
                Math.max(config.planMinItemMinutes(), roundDown((int) Math.round(in.dailyMinutes() * config.examPracticeFraction()))), p,
                reached + " becerinde tahmin hedef seviyede (" + in.goalTarget() + ").",
                basis("skillsAtTarget", reached, "target", in.goalTarget()),
                Action.external(in.goalTarget() + " seviyesinde bir modelltest bölümü çöz ve puanını gir.", true)));
    }

    /** Veri yoksa: calisma seviyesine gore baslangic plani (5.3). */
    private List<Recommendation> startPlan(CoachInput in, int priority) {
        List<Recommendation> out = new ArrayList<>();
        Level level = in.working().level();
        for (StudyType t : bySharesDesc(in)) {
            Category category = t == StudyType.KELIME ? Category.VOCABULARY
                    : t == StudyType.GRAMER ? Category.GRAMMAR : Category.SKILL;
            out.add(new Recommendation("START", category, t, t.label() + ": " + shareMinutes(t, in) + " dk",
                    shareMinutes(t, in), priority,
                    "Henüz kaydın yok. " + (level == Level.A0 ? "Sıfırdan başlangıç" : level + " başlangıç")
                            + " planında haftalık payı en yüksek türlerden biri.",
                    basis("workingLevel", level, "share", config.sharesFor(level).get(t.key())),
                    actionFor(t, in, false)));
        }
        return out;
    }

    // --- Yardimcilar -----------------------------------------------------

    /** Belirli bir gramer konusu sitede varsa dogrudan o konuya gotur. */
    private Action topicAction(String topicId, CoachInput in) {
        if (in.siteModules().contains(StudyType.GRAMER)) {
            return Action.site("/app/ogren.html#gramer:" + topicId);
        }
        return Action.external(hint(StudyType.GRAMER, in.working().level()), false);
    }

    /** Artikel alistirmasi sitede kelime modulune baglidir; yoksa disarida. */
    private Action articleAction(CoachInput in) {
        if (in.siteModules().contains(StudyType.KELIME)) {
            return Action.site("/app/ogren.html#artikel");
        }
        return Action.external(hint(StudyType.KELIME, in.working().level()), false);
    }

    private Action actionFor(StudyType type, CoachInput in, boolean logResult) {
        if (in.siteModules().contains(type)) {
            return Action.site("/app/ogren.html#" + type.key());
        }
        return Action.external(hint(type, in.working().level()), logResult || type.skill().isPresent());
    }

    /** "Disarida yap" yonlendirmesi; seviye A0 ise SPEC 10.1'in A0 yolu. */
    static String hint(StudyType type, Level level) {
        if (level == Level.A0) {
            return switch (type) {
                case KELIME -> "Alfabe, sayılar, selamlaşma ve temel kelimelerle başla; bir liste ya da uygulama kullanabilirsin.";
                case GRAMER -> "Zamirler, sein/haben ve temel cümle yapısıyla başla.";
                case HOEREN -> "Yeni başlayanlar için yavaş konuşulan kısa bir video ya da podcast dinle.";
                case LESEN -> "Çok kısa, resimli bir başlangıç metni oku.";
                case SPRECHEN -> "Selamlaşmayı ve kendini tanıtmayı sesli tekrar et.";
                case SCHREIBEN -> "Kendini tanıtan 3–4 basit cümle yaz.";
            };
        }
        return switch (type) {
            case KELIME -> level + " seviyesine uygun kelimeleri kendi kaynağından çalış.";
            case GRAMER -> level + " seviyesinden bir gramer konusuna çalış.";
            case HOEREN -> level + " seviyesinde bir dinleme yap (podcast, video, modelltest bölümü).";
            case LESEN -> level + " seviyesinde bir metin oku ve soruları çöz.";
            case SPRECHEN -> level + " seviyesinde bir konuda konuş; kendini kaydet ya da biriyle pratik yap.";
            case SCHREIBEN -> level + " seviyesinde kısa bir metin yaz (e-posta, mesaj, görüş).";
        };
    }

    int shareMinutes(StudyType t, CoachInput in) {
        Integer share = config.sharesFor(in.working().level()).get(t.key());
        int m = roundDown((int) Math.round(in.dailyMinutes() * (share == null ? 0 : share) / 100.0));
        return Math.min(in.dailyMinutes(), Math.max(config.planMinItemMinutes(), m));
    }

    private List<StudyType> bySharesDesc(CoachInput in) {
        Map<String, Integer> shares = config.sharesFor(in.working().level());
        List<StudyType> types = new ArrayList<>(List.of(StudyType.values()));
        types.sort(Comparator.comparingInt((StudyType t) -> shares.getOrDefault(t.key(), 0)).reversed());
        return types;
    }

    private static Optional<LocalDate> lastActive(CoachInput in) {
        return in.activities().stream().map(DoneActivity::date).max(Comparator.naturalOrder());
    }

    private boolean comebackActive(CoachInput in) {
        return lastActive(in).map(d -> ChronoUnit.DAYS.between(d, in.today()) >= config.comebackDays())
                .orElse(false);
    }

    /** Birikmis tekrarlarin gunlere yayilmis bugunku payi (en gecikmisten baslayarak). */
    private int comebackReviewsToday(CoachInput in) {
        int spread = (int) Math.ceil((double) in.dueReviews() / config.comebackSpreadDays());
        return Math.min(spread, srs.dailyReviewLimit());
    }

    private static Optional<StudyType> mostStudied(CoachInput in) {
        Map<StudyType, Integer> minutes = new LinkedHashMap<>();
        for (DoneActivity a : in.activities()) {
            minutes.merge(a.type(), a.minutes(), Integer::sum);
        }
        return minutes.entrySet().stream().max(Map.Entry.comparingByValue()).map(Map.Entry::getKey);
    }

    private int roundDown(int minutes) {
        int b = config.planBlockMinutes();
        return (minutes / b) * b;
    }

    private int roundUp(int minutes) {
        int b = config.planBlockMinutes();
        return ((minutes + b - 1) / b) * b;
    }

    private static int pct(double ratio) {
        return (int) Math.round(ratio * 100);
    }

    private static Map<String, Object> basis(Object... kv) {
        Map<String, Object> m = new LinkedHashMap<>();
        for (int i = 0; i + 1 < kv.length; i += 2) {
            m.put((String) kv[i], kv[i + 1]);
        }
        return m;
    }
}
