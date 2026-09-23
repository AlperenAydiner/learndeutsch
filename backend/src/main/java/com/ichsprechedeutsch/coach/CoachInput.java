package com.ichsprechedeutsch.coach;

import com.ichsprechedeutsch.common.model.Level;
import com.ichsprechedeutsch.common.model.Skill;
import com.ichsprechedeutsch.common.model.StartMode;
import com.ichsprechedeutsch.level.GoalCompletion;
import com.ichsprechedeutsch.level.Reassessment;
import com.ichsprechedeutsch.level.SkillEstimate;
import com.ichsprechedeutsch.level.WorkingLevel;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Koc motorunun tum girdisi (SPEC 5.1). Motor bunun disinda hicbir sey
 * okumaz: veritabani ve sistem saati yok, bugun parametre.
 *
 * <p>Henuz yazilmamis modullerin verileri (kelime tekrari, gramer
 * penceresi, hata hafizasi, artikel) bos gelir; kurallari veri modeline
 * gore yazilidir ve test verisiyle dogrulanir. Moduller geldikce canli
 * veriye baglanir.
 *
 * @param siteModules sitede icerigi olan calisma turleri (yoksa oneri "disarida yap" olur)
 */
public record CoachInput(
        LocalDate today,
        StartMode startMode,
        Level goalTarget,
        int dailyMinutes,
        int daysPerWeek,
        WorkingLevel working,
        Map<Skill, SkillEstimate> skills,
        GoalCompletion goal,
        Reassessment reassessment,
        boolean placementDone,
        List<DoneActivity> activities,
        int dueReviews,
        int dueGrammarChecks,
        /** Bugunku tekrarlarin icerikteki estimatedMinutes toplamindan tahmini suresi. */
        int dueReviewMinutes,
        /** Yerlestirme testinin tahmini suresi (sorularin estimatedMinutes'i). */
        int placementMinutes,
        List<GrammarWindow> grammarWindows,
        List<ErrorTag> recurringErrors,
        Map<String, ArticleWindow> articleWindows,
        Set<StudyType> siteModules) {

    /** Gecmis bir calisma (plan ve bosta kalma kurallari icin). */
    public record DoneActivity(LocalDate date, StudyType type, int minutes) {
    }

    /** Bir gramer konusunda son N yanit (5.2 basari penceresi). */
    public record GrammarWindow(String topicId, String titleTr, int answers, int correct) {
        public double ratio() {
            return answers == 0 ? 0 : (double) correct / answers;
        }
    }

    /** Hata hafizasindaki bir etiket (8.4). */
    public record ErrorTag(String tag, String titleTr, int distinctSessions, String status) {
    }

    /** Bir artikel turunde son N yanit. */
    public record ArticleWindow(int answers, int correct) {
        public double ratio() {
            return answers == 0 ? 0 : (double) correct / answers;
        }
    }
}
