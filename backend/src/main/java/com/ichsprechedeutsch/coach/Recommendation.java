package com.ichsprechedeutsch.coach;

import java.util.Map;

/**
 * Bir koc onerisi (SPEC 5.1). Her oneri hangi kurala ve hangi verilere
 * dayandigini tasir (K3): {@code reason} kullaniciya gosterilen cumle,
 * {@code basis} ayni verinin ham hali.
 *
 * @param rule      onerinin kurali (coach.rule-order'daki ad)
 * @param category  ayni kategoriden en fazla bir oneri gosterilir (5.3)
 * @param type      ilgili calisma turu; yoksa null (or. yerlestirme testi)
 * @param minutes   tahmini sure
 * @param priority  kural sirasi; kucuk olan once
 */
public record Recommendation(
        String rule,
        Category category,
        StudyType type,
        String title,
        int minutes,
        int priority,
        String reason,
        Map<String, Object> basis,
        Action action) {

    /** Ayni anda ayni kategoriden en fazla bir oneri (SPEC 5.3). */
    public enum Category { ASSESSMENT, COMEBACK, REVIEW, VOCABULARY, GRAMMAR, SKILL, ARTICLE, EXAM }

    /**
     * Ne yapilacak: sitede bir sayfa, ya da "disarida yap + sonucu gir"
     * (sitede o tur/seviyede icerik yoksa, SPEC 5.3).
     *
     * @param kind SITE ya da EXTERNAL
     * @param href SITE icin sayfa
     * @param hint EXTERNAL icin ne yapilacagi
     * @param logResult disaridaki calismanin sonucu "Bugun ne yaptin?" ile girilmeli mi
     */
    public record Action(String kind, String href, String hint, boolean logResult) {

        public static Action site(String href) {
            return new Action("SITE", href, null, false);
        }

        public static Action external(String hint, boolean logResult) {
            return new Action("EXTERNAL", null, hint, logResult);
        }
    }
}
