package com.ichsprechedeutsch.content;

import com.ichsprechedeutsch.common.model.Level;
import java.util.List;

/**
 * Bir gramer konusunun ders icerigi (SPEC 8.2): kisa aciklama, ornekler
 * ve kontrollu uretim alistirmalari. Mini sorular ayri durur: ilgili
 * konunun etiketini tasiyan questions.json sorulari kullanilir.
 *
 * <p>Uzun ders sayfasi yok; aciklama birkac cumledir.
 */
public record GrammarLesson(
        String topicId,
        Level level,
        String explanationTr,
        /** Turkceyle kisa karsilastirma (SPEC 8.2); yoksa null. */
        String turkishNoteTr,
        List<Example> examples,
        List<Exercise> production,
        boolean verified) {

    public record Example(String de, String tr) {
    }

    /**
     * Kontrollu uretim: serbest cumle AI olmadan puanlanamaz (SPEC 8.2).
     *
     * @param type  GAP (bosluk doldurma) ya da WORD_ORDER (kelime siralama)
     * @param parts WORD_ORDER'da karistirilip gosterilecek parcalar
     */
    public record Exercise(
            String id,
            String type,
            String promptTr,
            String prompt,
            List<String> parts,
            String answer,
            List<String> acceptedAnswers,
            String explanationTr) {
    }
}
