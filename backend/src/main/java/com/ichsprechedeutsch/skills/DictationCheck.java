package com.ichsprechedeutsch.skills;

import com.ichsprechedeutsch.answer.AnswerCheck;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Dikte kontrolu (SPEC 8.3): kelime bazli karsilastirma, puan dogru
 * kelime oranidir. Saf.
 *
 * <p>Noktalama ve fazla bosluk yok sayilir; buyuk/kucuk harf ve umlaut
 * yazimi {@link AnswerCheck} ile ayni mantikla degerlendirilir: kelime
 * dogru sayilir ama "yaklasik" olarak isaretlenir.
 */
public final class DictationCheck {

    /**
     * @param expected beklenen kelime
     * @param given    kullanicinin yazdigi (eksikse null)
     * @param status   EXACT, CLOSE (yazim farki) ya da WRONG
     */
    public record WordResult(String expected, String given, String status) {
    }

    public record Result(int total, int correct, double ratio, List<WordResult> words) {
    }

    private DictationCheck() {
    }

    public static Result check(String expected, String given) {
        List<String> hedef = kelimeler(expected);
        List<String> cevap = kelimeler(given);
        List<WordResult> out = new ArrayList<>();
        int dogru = 0;

        for (int i = 0; i < hedef.size(); i++) {
            String h = hedef.get(i);
            String c = i < cevap.size() ? cevap.get(i) : null;
            String durum;
            if (c == null) {
                durum = "WRONG";
            } else if (c.equals(h)) {
                durum = "EXACT";
            } else if (AnswerCheck.umlautAc(c.toLowerCase(Locale.ROOT))
                    .equals(AnswerCheck.umlautAc(h.toLowerCase(Locale.ROOT)))) {
                durum = "CLOSE";
            } else {
                durum = "WRONG";
            }
            if (!"WRONG".equals(durum)) {
                dogru++;
            }
            out.add(new WordResult(h, c, durum));
        }

        // Fazla yazilan kelimeler de gosterilir ama puani dusurmez.
        for (int i = hedef.size(); i < cevap.size(); i++) {
            out.add(new WordResult(null, cevap.get(i), "EXTRA"));
        }

        double oran = hedef.isEmpty() ? 0 : (double) dogru / hedef.size();
        return new Result(hedef.size(), dogru, oran, out);
    }

    private static List<String> kelimeler(String s) {
        if (s == null || s.isBlank()) {
            return List.of();
        }
        return List.of(AnswerCheck.sadelestir(s).split(" "));
    }
}
