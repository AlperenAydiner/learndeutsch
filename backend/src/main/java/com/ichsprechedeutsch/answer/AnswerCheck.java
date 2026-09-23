package com.ichsprechedeutsch.answer;

import com.ichsprechedeutsch.config.AnswerProperties;
import com.ichsprechedeutsch.config.AnswerProperties.Mode;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Yazmali cevaplarin kontrolu (SPEC 8.3). Saf: config disinda bagimliligi yok.
 *
 * <ul>
 *   <li>Fazla bosluk ve noktalama farki yok sayilir.</li>
 *   <li>Buyuk/kucuk harf farki ve ae/oe/ue/ss yazimi varsayilan olarak
 *       kabul edilir ama uyari uretir; config'den kati mod secilebilir.</li>
 *   <li>Soru birden fazla kabul edilen cevap tasiyabilir.</li>
 * </ul>
 *
 * <p>Turkce yerelde {@code "I".toLowerCase()} = {@code "ı"} oldugu icin her
 * kucultme {@link Locale#ROOT} ile yapilir.
 */
public final class AnswerCheck {

    /** Dogru sayilan ama yazimi kusurlu cevaplarda verilen uyari. */
    public enum Warning {
        /** Buyuk/kucuk harf: Almancada isimler buyuk harfle baslar. */
        BUYUK_KUCUK_HARF,
        /** ä/ö/ü/ß yerine ae/oe/ue/ss yazilmis. */
        UMLAUT_YAZIMI,
    }

    /**
     * @param correct  kabul edildi mi (kati modda kusurlu yazim kabul edilmez)
     * @param warnings kabul edildiyse yazim uyarilari (hata etiketi olarak da kaydedilir)
     * @param matched  hangi kabul edilen cevaba denk geldi; hicbiri degilse null
     */
    public record Result(boolean correct, List<Warning> warnings, String matched) {

        public boolean flawless() {
            return correct && warnings.isEmpty();
        }
    }

    private AnswerCheck() {
    }

    /**
     * @param given    kullanicinin yazdigi
     * @param expected dogru cevap
     * @param accepted ek kabul edilen cevaplar (null olabilir)
     */
    public static Result check(String given, String expected, List<String> accepted, AnswerProperties config) {
        List<String> hedefler = new ArrayList<>();
        hedefler.add(expected);
        if (accepted != null) {
            accepted.stream().filter(a -> a != null && !a.isBlank()).forEach(hedefler::add);
        }
        if (given == null || given.isBlank()) {
            return new Result(false, List.of(), null);
        }

        String cevap = sadelestir(given);
        Result enIyi = new Result(false, List.of(), null);
        for (String hedef : hedefler) {
            Result r = karsilastir(cevap, sadelestir(hedef), hedef, config);
            if (r.flawless()) {
                return r;
            }
            if (r.correct() && !enIyi.correct()) {
                enIyi = r;
            }
        }
        return enIyi;
    }

    private static Result karsilastir(String cevap, String hedef, String hedefHam, AnswerProperties config) {
        if (cevap.equals(hedef)) {
            return new Result(true, List.of(), hedefHam);
        }

        List<Warning> uyarilar;
        if (kucuk(cevap).equals(kucuk(hedef))) {
            uyarilar = List.of(Warning.BUYUK_KUCUK_HARF);
        } else if (umlautAc(cevap).equals(umlautAc(hedef))) {
            uyarilar = List.of(Warning.UMLAUT_YAZIMI);
        } else if (umlautAc(kucuk(cevap)).equals(umlautAc(kucuk(hedef)))) {
            uyarilar = List.of(Warning.BUYUK_KUCUK_HARF, Warning.UMLAUT_YAZIMI);
        } else {
            return new Result(false, List.of(), null);
        }

        boolean kabul = uyarilar.stream().allMatch(w -> mode(w, config) == Mode.ACCEPT_WARN);
        return new Result(kabul, kabul ? uyarilar : List.of(), kabul ? hedefHam : null);
    }

    private static String kucuk(String s) {
        return s.toLowerCase(Locale.ROOT);
    }

    private static Mode mode(Warning w, AnswerProperties config) {
        return w == Warning.BUYUK_KUCUK_HARF ? config.letterCase() : config.umlautTranscription();
    }

    /** Bosluklari tekler, noktalamayi atar. Dikte kontrolu de kullanir. */
    public static String sadelestir(String s) {
        String temiz = s.replaceAll("[.,!?;:…\"'„“”‚‘’]", " ");
        return temiz.trim().replaceAll("\\s+", " ");
    }

    /** ä/ö/ü/ß -> ae/oe/ue/ss; iki yazim da ayni metne iner. */
    public static String umlautAc(String s) {
        return s.replace("ä", "ae").replace("ö", "oe").replace("ü", "ue").replace("ß", "ss")
                .replace("Ä", "Ae").replace("Ö", "Oe").replace("Ü", "Ue");
    }
}
