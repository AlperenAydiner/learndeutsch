package com.ichsprechedeutsch;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;

import com.ichsprechedeutsch.common.time.Today;
import com.ichsprechedeutsch.content.ContentCatalog;
import com.ichsprechedeutsch.content.Question;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * SPEC 12.3 — kabul senaryolari. Her senaryo kendi DEMO kullanicisinda
 * calisir ve zamani {@code X-Demo-Date} ile ileri sarar (12.2): gercek
 * kullanici verisine dokunulmaz.
 *
 * <pre>IT_DB=dev ./gradlew test --tests '*AcceptanceIT'</pre>
 */
@SpringBootTest
@AutoConfigureMockMvc
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@EnabledIfEnvironmentVariable(named = "IT_DB", matches = TestDatabase.ENABLED)
class AcceptanceIT {

    private final List<UUID> acilanHesaplar = new ArrayList<>();

    @Autowired
    MockMvc mvc;
    @Autowired
    ObjectMapper mapper;
    @Autowired
    ContentCatalog catalog;
    @Autowired
    JdbcTemplate jdbc;

    @DynamicPropertySource
    static void database(DynamicPropertyRegistry r) {
        TestDatabase.configure(r);
    }

    @AfterAll
    void cleanup() {
        for (UUID authId : acilanHesaplar) {
            jdbc.update("DELETE FROM app_user WHERE auth_user_id = ?", authId);
        }
    }

    // --- Demo kullanici yardimcilari -------------------------------------

    /** Kendi demo hesabiyla calisan bir senaryo baglami. */
    private final class Senaryo {
        private final UUID authId = UUID.randomUUID();
        private LocalDate tarih = LocalDate.now(ZoneId.of("Europe/Istanbul"));
        private boolean demoIsaretli;

        Senaryo() {
            acilanHesaplar.add(authId);
        }

        /** Zamani ileri sarar (SPEC 12.2). */
        Senaryo gunIlerle(int gun) {
            tarih = tarih.plusDays(gun);
            return this;
        }

        LocalDate bugun() {
            return tarih;
        }

        JsonNode call(MockHttpServletRequestBuilder req, Object body, int expected) throws Exception {
            req.with(jwt().jwt(j -> j.subject(authId.toString())
                    .claim("email", "kabul-" + authId + "@test.invalid")));
            req.header(Today.DEMO_HEADER, tarih.toString());
            if (body != null) {
                req.contentType(MediaType.APPLICATION_JSON).content(mapper.writeValueAsString(body));
            }
            var res = mvc.perform(req).andReturn().getResponse();
            assertEquals(expected, res.getStatus(), "yanit: " + res.getContentAsString());
            String text = res.getContentAsString();
            if (!demoIsaretli) {
                // Hesap ilk istekte olusur; sonrasinda demo olarak isaretlenir.
                jdbc.update("UPDATE app_user SET is_demo = true WHERE auth_user_id = ?", authId);
                demoIsaretli = true;
            }
            return text.isBlank() ? null : mapper.readTree(text);
        }
    }

    private Map<String, Object> disCalisma(LocalDate tarih, String tur, int dakika, String seviye,
                                           Integer puan, Integer azami, String kaynak) {
        Map<String, Object> a = new LinkedHashMap<>();
        a.put("date", tarih.toString());
        a.put("type", tur);
        a.put("durationMinutes", dakika);
        a.put("sourceKind", "OTHER_APP");
        if (seviye != null) {
            a.put("level", seviye);
            a.put("score", puan);
            a.put("maxScore", azami);
            a.put("resultSource", kaynak);
        }
        return a;
    }

    /** Bir gramer konusunda oturum acip verilen sayida yanlis/dogru cevap verir. */
    private void gramerOturumu(Senaryo s, String konu, boolean dogruCevapla) throws Exception {
        JsonNode ders = s.call(post("/api/grammar/topics/" + konu + "/session"), null, 200);
        String oturum = ders.path("sessionId").asString();
        for (JsonNode adim : ders.path("steps")) {
            String itemId = adim.path("id").asString();
            String cevap = dogruCevapla ? dogruCevap(konu, itemId) : "kesinlikle-yanlis";
            s.call(post("/api/grammar/answer"), Map.of("sessionId", oturum, "topicId", konu,
                    "itemId", itemId, "given", cevap), 200);
        }
        s.call(post("/api/grammar/session/" + oturum + "/finish"),
                Map.of("interactions", new long[]{System.currentTimeMillis()}), 204);
    }

    private String dogruCevap(String konu, String itemId) {
        return catalog.question(itemId)
                .map(Question::answer)
                .orElseGet(() -> catalog.lesson(konu).orElseThrow().production().stream()
                        .filter(e -> e.id().equals(itemId))
                        .findFirst().orElseThrow().answer());
    }

    // --- Senaryolar -------------------------------------------------------

    @Test
    @DisplayName("Senaryo 1: A0 kullanici, hedef A1 — plan, oneri ve otomatik kayit")
    void senaryo1() throws Exception {
        Senaryo s = new Senaryo();
        s.call(put("/api/onboarding/start"), Map.of("startMode", "FROM_ZERO"), 200);
        s.call(post("/api/onboarding/complete"),
                Map.of("target", "A1", "purpose", "PERSONAL", "dailyMinutes", 15, "daysPerWeek", 5), 200);

        JsonNode koc = s.call(get("/api/coach"), null, 200);
        assertEquals(15 * 5, koc.path("weekly").path("totalMinutes").asInt(), "haftalik plan ritimden gelir");
        assertTrue(koc.path("recommendations").size() <= 3, "gunde en fazla uc oneri");
        assertFalse(koc.path("daily").path("items").isEmpty(), "ilk gun onerisi var");
        for (JsonNode r : koc.path("recommendations")) {
            assertFalse(r.path("reason").asString("").isBlank(), "her oneri gerekcesini yazar");
        }

        // Site ici calisma otomatik loglanir; ayni oturum iki kayit uretmez.
        JsonNode oturum = s.call(post("/api/words/session"), null, 200);
        String wordSession = oturum.path("sessionId").asString();
        JsonNode ilk = oturum.path("cards").get(0);
        s.call(post("/api/words/answer"), Map.of("sessionId", wordSession,
                "wordId", ilk.path("wordId").asString(), "grade", "BILDIM"), 200);
        s.call(post("/api/words/session/" + wordSession + "/finish"),
                Map.of("interactions", new long[]{System.currentTimeMillis()}), 204);

        int kelimeKaydi = 0;
        for (JsonNode a : s.call(get("/api/activities"), null, 200)) {
            if ("KELIME".equals(a.path("type").asString()) && "SITE".equals(a.path("origin").asString())) {
                kelimeKaydi++;
            }
        }
        assertEquals(1, kelimeKaydi, "cift kayit olusmamali");

        // Bir hafta boyunca kayit: gecmis dogru gunlere yazilir
        for (int gun = 1; gun <= 6; gun++) {
            s.gunIlerle(1);
            s.call(post("/api/activities"), disCalisma(s.bugun(), "KELIME", 15, null, null, null, null), 201);
        }
        JsonNode hafta = s.call(get("/api/coach"), null, 200).path("last7Days");
        assertEquals(7, hafta.size());
        assertEquals(15, hafta.get(6).path("minutes").path("KELIME").asInt(), "bugunun kaydi");
    }

    @Test
    @DisplayName("Senaryo 2: tekrarlayan Dativ hatasi -> koc onerisi -> cozuldu")
    void senaryo2() throws Exception {
        Senaryo s = new Senaryo();
        s.call(put("/api/onboarding/start"), Map.of("startMode", "SOME_KNOWLEDGE"), 200);
        s.call(post("/api/onboarding/complete"),
                Map.of("target", "B1", "purpose", "EXAM", "dailyMinutes", 30, "daysPerWeek", 5), 200);

        // Uc farkli oturumda Dativ hatasi -> tekrarlayan hata (Ek A: 3 oturum)
        for (int oturum = 0; oturum < 3; oturum++) {
            gramerOturumu(s, "DATIV", false);
            s.gunIlerle(1);
        }
        JsonNode hatalar = s.call(get("/api/grammar/errors"), null, 200);
        JsonNode dativ = null;
        for (JsonNode h : hatalar) {
            if ("DATIV".equals(h.path("tag").asString())) {
                dativ = h;
            }
        }
        assertNotNull(dativ, "Dativ hata kaydi olusmali");
        assertTrue(dativ.path("recurring").asBoolean(), "uc farkli oturum -> tekrarlayan hata");
        assertEquals("ACTIVE", dativ.path("status").asString());

        boolean dativOnerisi = false;
        for (JsonNode r : s.call(get("/api/coach"), null, 200).path("recommendations")) {
            dativOnerisi |= "DATIV".equals(r.path("basis").path("tag").asString(null))
                    || "DATIV".equals(r.path("basis").path("topic").asString(null));
        }
        assertTrue(dativOnerisi, "koc Dativ tekrarini onermeli");

        // Art arda dogrularla "cozuldu"ye gecis (Ek A: 3 dogru)
        for (int oturum = 0; oturum < 3; oturum++) {
            gramerOturumu(s, "DATIV", true);
            s.gunIlerle(1);
        }
        for (JsonNode h : s.call(get("/api/grammar/errors"), null, 200)) {
            if ("DATIV".equals(h.path("tag").asString())) {
                assertEquals("RESOLVED", h.path("status").asString(), "art arda dogrular hatayi cozer");
                assertFalse(h.path("recurring").asBoolean());
            }
        }
    }

    @Test
    @DisplayName("Senaryo 3: veri yoksa 'veri yok' ve eksik genel tahmin")
    void senaryo3() throws Exception {
        Senaryo s = new Senaryo();
        s.call(put("/api/onboarding/start"), Map.of("startMode", "SOME_KNOWLEDGE"), 200);
        s.call(post("/api/onboarding/complete"),
                Map.of("target", "C1", "purpose", "UNIVERSITY", "dailyMinutes", 60, "daysPerWeek", 5), 200);

        // Yalniz Lesen ve Schreiben kaniti gir
        s.call(post("/api/activities"),
                disCalisma(s.bugun(), "LESEN", 45, "B2", 8, 10, "MODELLTEST"), 201);
        s.call(post("/api/activities"),
                disCalisma(s.bugun(), "SCHREIBEN", 45, "B2", 16, 20, "TEACHER"), 201);

        JsonNode seviye = s.call(get("/api/level"), null, 200);
        for (JsonNode k : seviye.path("skills")) {
            String beceri = k.path("skill").asString();
            if ("HOEREN".equals(beceri) || "SPRECHEN".equals(beceri)) {
                assertEquals("NO_DATA", k.path("confidence").asString(), beceri + ": veri yok denmeli");
                assertTrue(k.path("level").isNull(), beceri + ": seviye uydurulmamali");
            }
        }
        JsonNode genel = seviye.path("overall");
        assertEquals(2, genel.path("missing").size(), "eksik beceriler sayilmali");

        // C1'de site ici kelime icerigi yok -> oneri disariya yonlendirir (SPEC 10.1)
        JsonNode koc = s.call(get("/api/coach"), null, 200);
        for (JsonNode r : koc.path("recommendations")) {
            if ("EXTERNAL".equals(r.path("action").path("kind").asString())) {
                assertFalse(r.path("action").path("hint").asString("").isBlank(),
                        "disarida yap onerisi nasil yapilacagini soylemeli");
            }
        }
    }

    @Test
    @DisplayName("Senaryo 4: hedefe ulasan kullanici otomatik yukseltilmez (K6)")
    void senaryo4() throws Exception {
        Senaryo s = new Senaryo();
        s.call(put("/api/onboarding/start"), Map.of("startMode", "SOME_KNOWLEDGE"), 200);
        s.call(post("/api/onboarding/complete"),
                Map.of("target", "A2", "purpose", "DAILY_LIFE", "dailyMinutes", 30, "daysPerWeek", 5), 200);

        // Dort beceride de A2 kaniti; guven icin ikiser kayit (Ek A: min 2 pozitif)
        for (String beceri : new String[]{"LESEN", "HOEREN", "SCHREIBEN", "SPRECHEN"}) {
            s.call(post("/api/activities"),
                    disCalisma(s.bugun(), beceri, 30, "A2", 9, 10, "TEACHER"), 201);
            s.gunIlerle(1);
            s.call(post("/api/activities"),
                    disCalisma(s.bugun(), beceri, 30, "A2", 8, 10, "MODELLTEST"), 201);
        }

        JsonNode hedef = s.call(get("/api/level"), null, 200).path("goal");
        assertTrue(hedef.path("complete").asBoolean(), "dort beceride hedefe ulasildi");
        for (JsonNode k : hedef.path("skills")) {
            assertFalse(k.path("confidence").asString("").isBlank(), "beceri bazli guven dokumu gosterilmeli");
        }

        // Hedef kendiliginden degismedi
        assertEquals("A2", s.call(get("/api/onboarding"), null, 200).path("goal").path("target").asString());

        JsonNode sinav = s.call(get("/api/exam"), null, 200);
        assertTrue(sinav.path("goalComplete").asBoolean());
        assertEquals("A2", sinav.path("target").asString(), "sistem B1'e itmez");
    }

    @Test
    @DisplayName("Senaryo 5: 5 gun ara -> geri donus plani ve yayilan tekrarlar")
    void senaryo5() throws Exception {
        Senaryo s = new Senaryo();
        s.call(put("/api/onboarding/start"), Map.of("startMode", "FROM_ZERO"), 200);
        s.call(post("/api/onboarding/complete"),
                Map.of("target", "A1", "purpose", "PERSONAL", "dailyMinutes", 30, "daysPerWeek", 5), 200);

        // Ilk gun bir kelime oturumu: tekrarlar sonraki gunlere dusecek
        JsonNode oturum = s.call(post("/api/words/session"), null, 200);
        String wordSession = oturum.path("sessionId").asString();
        for (JsonNode kart : oturum.path("cards")) {
            s.call(post("/api/words/answer"), Map.of("sessionId", wordSession,
                    "wordId", kart.path("wordId").asString(), "grade", "BILDIM"), 200);
        }
        s.call(post("/api/words/session/" + wordSession + "/finish"),
                Map.of("interactions", new long[]{System.currentTimeMillis()}), 204);

        s.gunIlerle(6);
        JsonNode koc = s.call(get("/api/coach"), null, 200);
        boolean geriDonus = false;
        for (JsonNode r : koc.path("recommendations")) {
            geriDonus |= "COMEBACK".equals(r.path("rule").asString());
        }
        assertTrue(geriDonus, "ara verildiginde geri donus onerisi gelmeli");
        assertTrue(koc.path("daily").path("totalMinutes").asInt() < 30,
                "geri donus gunu kisaltilir");

        JsonNode kelime = s.call(get("/api/words/stats"), null, 200);
        assertTrue(kelime.path("dueToday").asInt() > 0, "birikmis tekrarlar bekliyor");
    }

    @Test
    @DisplayName("Senaryo 6: test sonucu hedefin ustunde -> bildir, hedefi degistirme")
    void senaryo6() throws Exception {
        Senaryo s = new Senaryo();
        s.call(put("/api/onboarding/start"), Map.of("startMode", "SOME_KNOWLEDGE"), 200);
        // Testi atlayip hedef sec
        s.call(post("/api/onboarding/complete"),
                Map.of("target", "A2", "purpose", "WORK", "dailyMinutes", 30, "daysPerWeek", 5), 200);

        // Sonra yerlestirme testini coz ve her bloku tam dogru cevapla
        String sonuc = null;
        JsonNode b = s.call(post("/api/placement/start"), null, 200);
        String oturum = b.path("sessionId").asString();
        for (int blok = 0; blok < 6 && sonuc == null; blok++) {
            Map<String, String> cevaplar = new LinkedHashMap<>();
            for (JsonNode q : b.path("questions")) {
                Question full = catalog.question(q.path("id").asString()).orElseThrow();
                cevaplar.put(full.id(), full.answer());
            }
            JsonNode outcome = s.call(post("/api/placement/" + oturum + "/answers"),
                    Map.of("answers", cevaplar), 200);
            if (outcome.path("result").isNull() || outcome.path("result").isMissingNode()) {
                b = outcome.path("next");
            } else {
                sonuc = outcome.path("result").path("result").asString();
                assertEquals("A2", outcome.path("result").path("goalReached").asString(null),
                        "sonuc hedefin ustundeyse istemciye bildirilir");
            }
        }
        assertNotNull(sonuc, "test sonuclanmali");
        assertTrue(sonuc.compareTo("A2") > 0, "hep dogru cevaplandi, sonuc A2'nin ustunde: " + sonuc);

        assertEquals("A2", s.call(get("/api/onboarding"), null, 200).path("goal").path("target").asString(),
                "hedef otomatik degismez (K6)");
    }
}
