package com.ichsprechedeutsch;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;

import com.ichsprechedeutsch.content.ContentCatalog;
import com.ichsprechedeutsch.content.Question;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.AfterAll;
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
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * Uctan uca API akisi, GELISTIRME veritabanina karsi. Yalniz acikca
 * istendiginde calisir (IT_DB=dev) ve sonunda kendi test kullanicisini
 * siler. Giris icin gercek hesap acilmaz: istek, test JWT'siyle imzalanmis
 * sayilir (Spring Security test destegi).
 *
 * <pre>IT_DB=dev ./gradlew test --tests '*ApiFlowIT'</pre>
 */
@SpringBootTest
@AutoConfigureMockMvc
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@EnabledIfEnvironmentVariable(named = "IT_DB", matches = TestDatabase.ENABLED)
class ApiFlowIT {

    private static final UUID AUTH_ID = UUID.randomUUID();
    /** Gramer akisi kendi kullanicisiyla calisir: testler birbirinin durumunu bozmasin. */
    private static final UUID AUTH_ID_2 = UUID.randomUUID();
    /** Beceri akisi da kendi kullanicisiyla calisir. */
    private static final UUID AUTH_ID_3 = UUID.randomUUID();

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
        jdbc.update("DELETE FROM app_user WHERE auth_user_id IN (?, ?, ?)",
                AUTH_ID, AUTH_ID_2, AUTH_ID_3);
    }

    private RequestPostProcessor user() {
        return user(AUTH_ID);
    }

    private RequestPostProcessor user(UUID authId) {
        return jwt().jwt(j -> j.subject(authId.toString()).claim("email", "it-" + authId + "@test.invalid"));
    }

    private JsonNode call(MockHttpServletRequestBuilder req, Object body, int expectedStatus) throws Exception {
        return call(req, body, expectedStatus, AUTH_ID);
    }

    private JsonNode call(MockHttpServletRequestBuilder req, Object body, int expectedStatus, UUID authId)
            throws Exception {
        req.with(user(authId));
        if (body != null) {
            req.contentType(MediaType.APPLICATION_JSON).content(mapper.writeValueAsString(body));
        }
        var res = mvc.perform(req).andReturn().getResponse();
        assertEquals(expectedStatus, res.getStatus(), "yanit: " + res.getContentAsString());
        String text = res.getContentAsString();
        return text.isBlank() ? null : mapper.readTree(text);
    }

    @Test
    void fullFaz1Flow() throws Exception {
        String today = LocalDate.now(ZoneId.of("Europe/Istanbul")).toString();

        // 1) Onboarding: yeni kullanici
        JsonNode state = call(get("/api/onboarding"), null, 200);
        assertTrue(state.path("startMode").isNull());
        assertFalse(state.path("completed").asBoolean());
        assertEquals("A0", state.path("working").path("level").asString());

        // Hedef ve ritim yokken koc calismaz: uydurma plan uretmez (K3)
        call(get("/api/coach"), null, 404);

        call(put("/api/onboarding/start"), Map.of("startMode", "SOME_KNOWLEDGE"), 200);
        state = call(get("/api/onboarding"), null, 200);
        assertEquals("A1", state.path("working").path("level").asString());
        assertTrue(state.path("working").path("recommendPlacement").asBoolean());

        // 2) Yerlestirme: ilk blok A2; 4/6 dogru -> sonuc A2 (gec ama yukari cikma)
        JsonNode block = call(post("/api/placement/start"), null, 200);
        String session = block.path("sessionId").asString();
        assertEquals(6, block.path("questions").size());
        for (JsonNode q : block.path("questions")) {
            assertTrue(q.path("answer").isMissingNode(), "dogru cevap istemciye gitmemeli");
        }
        Map<String, String> answers = new LinkedHashMap<>();
        int k = 0;
        for (JsonNode q : block.path("questions")) {
            Question full = catalog.question(q.path("id").asString()).orElseThrow();
            assertEquals("A2", full.level().name());
            answers.put(full.id(), k++ < 4 ? full.answer() : "yanlis");
        }
        long now = System.currentTimeMillis();
        JsonNode outcome = call(post("/api/placement/" + session + "/answers"),
                Map.of("answers", answers, "interactions", new long[]{now - 120_000, now - 60_000, now}), 200);
        JsonNode result = outcome.path("result");
        assertEquals("A2", result.path("result").asString());
        assertEquals(4, result.path("correct").asInt());
        assertEquals(6, result.path("review").size());

        // Ayni oturum tekrar cevaplanamaz
        call(post("/api/placement/" + session + "/answers"), Map.of("answers", answers), 400);

        // 3) Hedef secenekleri test sonucunun ustundekiler; tamamla
        state = call(get("/api/onboarding"), null, 200);
        assertEquals("[\"B1\",\"B2\",\"C1\"]", state.path("targetOptions").toString());
        call(post("/api/onboarding/complete"),
                Map.of("target", "A2", "purpose", "EXAM", "dailyMinutes", 30, "daysPerWeek", 5), 400);
        state = call(post("/api/onboarding/complete"),
                Map.of("target", "B1", "purpose", "EXAM", "dailyMinutes", 30, "daysPerWeek", 5), 200);
        assertTrue(state.path("completed").asBoolean());
        assertEquals("A2", state.path("working").path("level").asString());
        assertEquals("[\"A2\",\"B1\"]", state.path("path").toString());

        // 4) Seviye: yerlestirme beceri profilini doldurmaz
        JsonNode level = call(get("/api/level"), null, 200);
        for (JsonNode s : level.path("skills")) {
            assertEquals("NO_DATA", s.path("confidence").asString());
        }
        assertFalse(level.path("overall").path("sufficient").asBoolean());
        assertEquals("A2", level.path("placement").path("result").asString());

        // 5) Bugun ne yaptin: Lesen sonucu -> kanit
        Map<String, Object> lesen = new LinkedHashMap<>();
        lesen.put("date", today);
        lesen.put("type", "LESEN");
        lesen.put("durationMinutes", 30);
        lesen.put("sourceKind", "BOOK");
        lesen.put("level", "A2");
        lesen.put("score", 8);
        lesen.put("maxScore", 10);
        lesen.put("resultSource", "TEACHER");
        JsonNode created = call(post("/api/activities"), lesen, 201);
        String lesenId = created.path("id").asString();

        level = call(get("/api/level"), null, 200);
        JsonNode lesenSkill = skill(level, "LESEN");
        assertEquals("AT_LEAST", lesenSkill.path("kind").asString());
        assertEquals("A2", lesenSkill.path("level").asString());
        assertEquals("MEDIUM", lesenSkill.path("basis").get(0).path("tier").asString());

        // Gramer sonucu kanit olmaz (K2)
        Map<String, Object> gramer = new LinkedHashMap<>(lesen);
        gramer.put("type", "GRAMER");
        gramer.put("grammarTopicId", "DATIV");
        call(post("/api/activities"), gramer, 201);
        assertEquals(1, skill(call(get("/api/level"), null, 200), "LESEN").path("evidenceCount").asInt());

        // Dogrulama: ileri tarih, sonucsuz kaynak
        Map<String, Object> future = new LinkedHashMap<>(lesen);
        future.put("date", LocalDate.parse(today).plusDays(1).toString());
        call(post("/api/activities"), future, 400);
        Map<String, Object> noSource = new LinkedHashMap<>(lesen);
        noSource.remove("resultSource");
        call(post("/api/activities"), noSource, 400);

        // Duzenleme kaniti yeniden hesaplar: 3/10 -> A2'nin altinda
        Map<String, Object> worse = new LinkedHashMap<>(lesen);
        worse.put("score", 3);
        call(put("/api/activities/" + lesenId), worse, 200);
        lesenSkill = skill(call(get("/api/level"), null, 200), "LESEN");
        assertEquals("BELOW", lesenSkill.path("kind").asString());
        assertEquals("A2", lesenSkill.path("level").asString());

        // Bugunun listesi: otomatik yerlestirme kaydi + iki dis kayit
        JsonNode todays = call(get("/api/activities"), null, 200);
        assertEquals(3, todays.size());
        boolean hasSite = false;
        for (JsonNode a : todays) {
            hasSite |= "SEVIYE_TESTI".equals(a.path("type").asString()) && "SITE".equals(a.path("origin").asString());
        }
        assertTrue(hasSite, "yerlestirme testi otomatik kaydedilmeli");

        // 6) Koc: oneriler ve plan
        JsonNode coach = call(get("/api/coach"), null, 200);
        assertTrue(coach.path("recommendations").size() <= 3, "gunde en fazla 3 oneri");
        java.util.Set<String> categories = new java.util.HashSet<>();
        for (JsonNode r : coach.path("recommendations")) {
            assertTrue(categories.add(r.path("category").asString()), "kategori basina bir oneri");
            assertFalse(r.path("reason").asString("").isBlank(), "her oneri gerekcesini yazar (K3)");
            assertTrue(r.path("minutes").asInt() > 0);
        }
        // Gunluk plan, kullanicinin verdigi sureyi asmaz
        assertTrue(coach.path("daily").path("totalMinutes").asInt() <= 30);
        assertEquals(30 * 5, coach.path("weekly").path("totalMinutes").asInt(),
                "haftalik sure = gunluk sure x gun sayisi");
        int weekly = 0;
        for (JsonNode w : coach.path("weekly").path("items")) {
            weekly += w.path("targetMinutes").asInt();
        }
        assertTrue(weekly > 0 && weekly <= 30 * 5, "turlere dagitilan sure haftalik sureyi asmaz: " + weekly);
        assertEquals(7, coach.path("last7Days").size());
        // Bugunku Lesen ve Gramer kayitlari aliskanlik ozetinde gorunur
        JsonNode bugun = coach.path("last7Days").get(6);
        assertEquals(today, bugun.path("date").asString());
        assertEquals(30, bugun.path("minutes").path("LESEN").asInt());

        // 7) Disa aktar -> sifirla -> ice aktar
        JsonNode export = call(get("/api/me/data/export"), null, 200);
        assertEquals(3, export.path("data").path("activity_log").size());
        call(post("/api/me/data/reset"), Map.of("confirm", "hayir"), 400);
        call(post("/api/me/data/reset"), Map.of("confirm", "SIFIRLA"), 204);
        assertEquals(0, call(get("/api/activities"), null, 200).size());
        assertTrue(call(get("/api/onboarding"), null, 200).path("startMode").isNull());

        call(post("/api/me/data/import"), export, 204);
        assertEquals(3, call(get("/api/activities"), null, 200).size());
        state = call(get("/api/onboarding"), null, 200);
        assertEquals("B1", state.path("goal").path("target").asString());
        assertNotNull(skill(call(get("/api/level"), null, 200), "LESEN").path("level").asString(null));

        // 8) Silme kaniti da siler
        call(delete("/api/activities/" + lesenId), null, 204);
        assertEquals("NONE", skill(call(get("/api/level"), null, 200), "LESEN").path("kind").asString());

        // 9) Kelime oturumu: yeni kelimeler merdivene girer
        JsonNode oturum = call(post("/api/words/session"), null, 200);
        String wordSession = oturum.path("sessionId").asString();
        assertEquals(0, oturum.path("reviewCount").asInt(), "yeni kullanicida tekrar yok");
        assertEquals(8, oturum.path("newCount").asInt(), "gunde 30 dk -> 8 yeni kelime");
        JsonNode ilkKart = oturum.path("cards").get(0);
        assertTrue(ilkKart.path("isNew").asBoolean());

        JsonNode cevap = call(post("/api/words/answer"), Map.of("sessionId", wordSession,
                "wordId", ilkKart.path("wordId").asString(), "grade", "BILDIM"), 200);
        assertEquals("BILDIM", cevap.path("effectiveGrade").asString());
        assertEquals(1, cevap.path("step").asInt());
        assertEquals(1, cevap.path("intervalDays").asInt(), "merdivenin ilk araligi");
        assertEquals(LocalDate.parse(today).plusDays(1).toString(), cevap.path("due").asString());

        // Artikel yanlissa "Bildim" -> "Zorlandim" (SPEC 8.1)
        JsonNode isim = null;
        for (JsonNode c : oturum.path("cards")) {
            if (c.path("askArticle").asBoolean() && !c.path("wordId").asString().equals(ilkKart.path("wordId").asString())) {
                isim = c;
                break;
            }
        }
        assertNotNull(isim, "oturumda en az bir isim olmali");
        String yanlisArtikel = "der".equals(isim.path("article").asString()) ? "die" : "der";
        JsonNode artikelli = call(post("/api/words/answer"), Map.of("sessionId", wordSession,
                "wordId", isim.path("wordId").asString(), "grade", "BILDIM",
                "articleGiven", yanlisArtikel), 200);
        assertEquals("ZORLANDIM", artikelli.path("effectiveGrade").asString());
        assertFalse(artikelli.path("articleCorrect").asBoolean());

        JsonNode wordStats = call(get("/api/words/stats"), null, 200);
        assertEquals(2, wordStats.path("studied").asInt());
        assertEquals(0, wordStats.path("strong").asInt());
        assertEquals(0, wordStats.path("dueToday").asInt(), "vade yarin");

        call(post("/api/words/session/" + wordSession + "/finish"),
                Map.of("interactions", new long[]{now - 300_000, now - 120_000, now}), 204);
        assertTrue(call(get("/api/activities"), null, 200).toString().contains("KELIME"),
                "kelime oturumu calisma gecmisine otomatik yazilir");

        // 10) Artikel pratigi: dogru cevap istatistige gider, seviye kaniti olmaz
        JsonNode artikelOturum = call(post("/api/article/session?count=5"), null, 200);
        String articleSession = artikelOturum.path("sessionId").asString();
        assertEquals(5, artikelOturum.path("items").size());
        String artikelKelime = artikelOturum.path("items").get(0).path("wordId").asString();
        String dogruArtikel = catalogArticle(artikelKelime);
        JsonNode artikelCevap = call(post("/api/article/answer"),
                Map.of("sessionId", articleSession, "wordId", artikelKelime, "given", dogruArtikel), 200);
        assertTrue(artikelCevap.path("correct").asBoolean());
        call(post("/api/article/answer"),
                Map.of("sessionId", articleSession, "wordId", artikelKelime, "given", "xyz"), 400);

        JsonNode artikelStats = call(get("/api/article/stats"), null, 200);
        // Iki artikel yaniti var: kelime oturumundaki yanlis secim + buradaki dogru.
        assertEquals(2, artikelStats.path("total").path("answers").asInt());
        assertEquals(1, artikelStats.path("total").path("correct").asInt());
        assertTrue(artikelStats.path("byArticle").path(dogruArtikel).path("correct").asInt() >= 1);
        for (JsonNode s : call(get("/api/level"), null, 200).path("skills")) {
            assertEquals("NO_DATA", s.path("confidence").asString(), "ogrenme testi seviye kaniti degil (K2)");
        }

        // Koc artik gercek kelime verisini goruyor: kelime onerisi sitede yapilir
        JsonNode koc = call(get("/api/coach"), null, 200);
        boolean kelimeSitede = false;
        for (JsonNode r : koc.path("recommendations")) {
            if ("KELIME".equals(r.path("type").asString(null))) {
                kelimeSitede |= "SITE".equals(r.path("action").path("kind").asString());
            }
        }
        for (JsonNode it : koc.path("daily").path("items")) {
            if ("KELIME".equals(it.path("type").asString())) {
                kelimeSitede = true;
            }
        }
        assertTrue(kelimeSitede, "kelime modulu sitede: koc artik disariya yonlendirmemeli");
    }

    private String catalogArticle(String wordId) {
        return catalog.words().get(wordId).article();
    }

    /** Faz 3b: gramer dongusu ve hata hafizasi. */
    @Test
    void grammarFlow() throws Exception {
        String today = LocalDate.now(ZoneId.of("Europe/Istanbul")).toString();
        call2(put("/api/onboarding/start"), Map.of("startMode", "FROM_ZERO"), 200);
        call2(post("/api/onboarding/complete"),
                Map.of("target", "A2", "purpose", "DAILY_LIFE", "dailyMinutes", 30, "daysPerWeek", 5), 200);

        // Konu listesi: A1 konulari ders icerigiyle gelir
        JsonNode konular = call2(get("/api/grammar/topics"), null, 200);
        JsonNode dativ = null;
        for (JsonNode k : konular) {
            if ("DATIV".equals(k.path("id").asString())) {
                dativ = k;
            }
        }
        assertNotNull(dativ, "DATIV konusu listede olmali");
        assertTrue(dativ.path("hasLesson").asBoolean(), "ders icerigi var");
        assertEquals(0, dativ.path("answers").asInt());

        // Ders dongusu: mini sorular + kontrollu uretim
        JsonNode ders = call2(post("/api/grammar/topics/DATIV/session"), null, 200);
        String session = ders.path("sessionId").asString();
        assertFalse(ders.path("explanationTr").asString("").isBlank());
        assertTrue(ders.path("examples").size() >= 2);
        assertTrue(ders.path("steps").size() >= 3, "mini sorular + uretim alistirmalari");
        for (JsonNode step : ders.path("steps")) {
            assertTrue(step.path("answer").isMissingNode(), "dogru cevap istemciye gitmemeli");
        }
        assertFalse(ders.path("checkRound").asBoolean(), "ilk calisma kontrol turu degil");

        // Yanlis cevap: hata hafizasina konu etiketi olarak yazilir
        String ilkAdim = ders.path("steps").get(0).path("id").asString();
        JsonNode yanlis = call2(post("/api/grammar/answer"), Map.of("sessionId", session,
                "topicId", "DATIV", "itemId", ilkAdim, "given", "kesinlikle-yanlis"), 200);
        assertFalse(yanlis.path("correct").asBoolean());
        assertFalse(yanlis.path("answer").asString("").isBlank(), "dogru cevap cevapta doner");

        JsonNode hatalar = call2(get("/api/grammar/errors"), null, 200);
        assertEquals(1, hatalar.size());
        assertEquals("DATIV", hatalar.get(0).path("tag").asString());
        assertEquals("ACTIVE", hatalar.get(0).path("status").asString());
        assertFalse(hatalar.get(0).path("recurring").asBoolean(), "tek oturum tekrarlayan degil");

        // Yazim uyarisi: kucuk harfle yazilan uretim cevabi kabul edilir ama etiketlenir
        JsonNode uretim = null;
        for (JsonNode step : ders.path("steps")) {
            if ("PRODUCTION".equals(step.path("kind").asString())
                    && "GAP".equals(step.path("type").asString())) {
                uretim = step;
                break;
            }
        }
        assertNotNull(uretim, "en az bir bosluk doldurma alistirmasi olmali");
        String dogruCevap = catalogExerciseAnswer("DATIV", uretim.path("id").asString());
        JsonNode kucuk = call2(post("/api/grammar/answer"), Map.of("sessionId", session,
                "topicId", "DATIV", "itemId", uretim.path("id").asString(),
                "given", dogruCevap.toUpperCase(java.util.Locale.ROOT)), 200);
        assertTrue(kucuk.path("correct").asBoolean(), "buyuk/kucuk harf farki kabul edilir");
        assertEquals("BUYUK_KUCUK_HARF", kucuk.path("warnings").get(0).asString());
        assertEquals(2, call2(get("/api/grammar/errors"), null, 200).size(), "yazim etiketi de eklendi");

        // Konuyu tamamla: kontrol tekrari planlanir (Ek A: 7 gun)
        call2(post("/api/grammar/session/" + session + "/finish"),
                Map.of("interactions", new long[]{System.currentTimeMillis()},
                        "completed", true, "topicId", "DATIV"), 204);
        for (JsonNode k : call2(get("/api/grammar/topics"), null, 200)) {
            if ("DATIV".equals(k.path("id").asString())) {
                assertEquals(today, k.path("completedOn").asString());
                assertEquals(LocalDate.parse(today).plusDays(7).toString(), k.path("nextCheck").asString());
                assertFalse(k.path("dueCheck").asBoolean(), "kontrol gunu henuz gelmedi");
                assertEquals(2, k.path("answers").asInt());
            }
        }

        JsonNode stats = call2(get("/api/grammar/stats"), null, 200);
        assertEquals(1, stats.path("studiedTopics").asInt());
        assertEquals(1, stats.path("completedTopics").asInt());
        assertEquals(2, stats.path("activeErrors").asInt());

        // Gramer calismasi otomatik kaydedilir ama seviye kaniti degildir (K2)
        assertTrue(call2(get("/api/activities"), null, 200).toString().contains("GRAMER"));
        for (JsonNode sk : call2(get("/api/level"), null, 200).path("skills")) {
            assertEquals("NO_DATA", sk.path("confidence").asString());
        }

        // Koc gramer verisini goruyor: zayif konu onerisi site ici konuya baglanir
        JsonNode koc = call2(get("/api/coach"), null, 200);
        assertTrue(koc.path("recommendations").size() <= 3);
    }

    /** Faz 3c: dort becerinin site ici calismasi ve kanit kurallari (SPEC 4.2). */
    @Test
    void skillPracticeFlow() throws Exception {
        call3(put("/api/onboarding/start"), Map.of("startMode", "FROM_ZERO"), 200);
        call3(post("/api/onboarding/complete"),
                Map.of("target", "A2", "purpose", "WORK", "dailyMinutes", 30, "daysPerWeek", 4), 200);

        // --- Lesen: otomatik puanlanir ve kanit uretir ---
        JsonNode metinler = call3(get("/api/skills/reading"), null, 200);
        assertTrue(metinler.size() >= 2, "A1 okuma metinleri listelenmeli");
        String metinId = metinler.get(0).path("id").asString();

        JsonNode okuma = call3(post("/api/skills/reading/" + metinId + "/session"), null, 200);
        assertFalse(okuma.path("text").asString("").isBlank());
        for (JsonNode q : okuma.path("questions")) {
            assertTrue(q.path("answer").isMissingNode(), "dogru cevap istemciye gitmemeli");
        }

        Map<String, String> cevaplar = new LinkedHashMap<>();
        int i = 0;
        for (JsonNode q : okuma.path("questions")) {
            String qid = q.path("id").asString();
            cevaplar.put(qid, i++ == 0 ? "kesinlikle-yanlis" : readingAnswer(metinId, qid));
        }
        JsonNode sonuc = call3(post("/api/skills/reading/" + metinId + "/submit"),
                Map.of("sessionId", okuma.path("sessionId").asString(), "answers", cevaplar,
                        "interactions", new long[]{System.currentTimeMillis()}), 200);
        assertTrue(sonuc.path("evidence").asBoolean(), "Lesen sonucu kanit olur");
        assertEquals(okuma.path("questions").size() - 1, (int) sonuc.path("score").asDouble());
        assertFalse(sonuc.path("review").get(0).path("correct").asBoolean());
        assertFalse(sonuc.path("review").get(0).path("correctAnswer").asString("").isBlank(),
                "dogru cevap ancak gonderimden sonra gelir");

        JsonNode lesen = skill(call3(get("/api/level"), null, 200), "LESEN");
        assertTrue(lesen.path("evidenceCount").asInt() > 0, "kanit yazildi");
        assertEquals("SITE_TEST", lesen.path("basis").get(0).path("source").asString());
        assertEquals("LOW", lesen.path("basis").get(0).path("tier").asString(),
                "verified:false icerik bir kademe dusurur (K-008)");

        // --- Hören: site ici alistirma KANIT DEGIL ---
        JsonNode dinlemeler = call3(get("/api/skills/listening"), null, 200);
        String dikteId = null;
        for (JsonNode l : dinlemeler) {
            if ("Dikte".equals(l.path("subtitleTr").asString())) {
                dikteId = l.path("id").asString();
                break;
            }
        }
        assertNotNull(dikteId, "en az bir dikte alistirmasi olmali");
        JsonNode dinleme = call3(post("/api/skills/listening/" + dikteId + "/session"), null, 200);
        JsonNode dikteSonuc = call3(post("/api/skills/listening/" + dikteId + "/submit"),
                Map.of("sessionId", dinleme.path("sessionId").asString(),
                        "dictation", dinleme.path("text").asString(),
                        "interactions", new long[]{System.currentTimeMillis()}), 200);
        assertEquals(1.0, dikteSonuc.path("ratio").asDouble(), "metnin aynisi yazildi");
        assertFalse(dikteSonuc.path("evidence").asBoolean(), "site ici Hören kanit uretmez (4.2)");
        assertEquals("NO_DATA", skill(call3(get("/api/level"), null, 200), "HOEREN")
                .path("confidence").asString());

        // --- Schreiben: rubrikle oz degerlendirme kanit uretir ---
        JsonNode gorevler = call3(get("/api/skills/writing"), null, 200);
        String yaziId = gorevler.get(0).path("id").asString();
        JsonNode yazma = call3(post("/api/skills/writing/" + yaziId + "/session"), null, 200);
        assertTrue(yazma.path("criteria").size() >= 4, "rubrik olcutleri config'den gelir");

        Map<String, Integer> rubrik = new LinkedHashMap<>();
        for (JsonNode c : yazma.path("criteria")) {
            rubrik.put(c.asString(), 3);
        }
        // Kisa metin reddedilir (SPEC: gorevin asgari uzunlugu)
        call3(post("/api/skills/writing/" + yaziId + "/submit"),
                Map.of("sessionId", yazma.path("sessionId").asString(), "text", "Zu kurz.",
                        "rubric", rubrik), 400);

        String metin = "Liebe Frau Meier, ich heisse Mert und ich komme aus Izmir. ".repeat(4);
        JsonNode yaziSonuc = call3(post("/api/skills/writing/" + yaziId + "/submit"),
                Map.of("sessionId", yazma.path("sessionId").asString(), "text", metin,
                        "rubric", rubrik, "interactions", new long[]{System.currentTimeMillis()}), 200);
        assertTrue(yaziSonuc.path("evidence").asBoolean());
        assertEquals(1.0, yaziSonuc.path("ratio").asDouble(), "hepsi tam puan");
        assertFalse(yaziSonuc.path("sampleAnswer").asString("").isBlank(), "ornek cevap gonderimden sonra");
        assertTrue(skill(call3(get("/api/level"), null, 200), "SCHREIBEN")
                .path("evidenceCount").asInt() > 0);

        // --- Sprechen: Kann-Beschreibung oz degerlendirmesi ---
        JsonNode konusmalar = call3(get("/api/skills/speaking"), null, 200);
        String konusmaId = konusmalar.get(0).path("id").asString();
        JsonNode konusma = call3(post("/api/skills/speaking/" + konusmaId + "/session"), null, 200);
        assertTrue(konusma.path("canDos").size() >= 2, "seviyeye ait ifadeler gelmeli");

        Map<String, String> canDo = new LinkedHashMap<>();
        for (JsonNode c : konusma.path("canDos")) {
            canDo.put(c.path("id").asString(), canDo.isEmpty() ? "PARTIAL" : "YES");
        }
        // Eksik isaretleme reddedilir (K3: veri yoksa sonuc yok)
        call3(post("/api/skills/speaking/" + konusmaId + "/submit"),
                Map.of("sessionId", konusma.path("sessionId").asString(),
                        "canDo", Map.of(canDo.keySet().iterator().next(), "YES")), 400);

        JsonNode konusmaSonuc = call3(post("/api/skills/speaking/" + konusmaId + "/submit"),
                Map.of("sessionId", konusma.path("sessionId").asString(), "canDo", canDo,
                        "interactions", new long[]{System.currentTimeMillis()}), 200);
        assertTrue(konusmaSonuc.path("evidence").asBoolean());
        assertTrue(konusmaSonuc.path("ratio").asDouble() < 1.0, "bir ifade 'kismen'");
        assertTrue(skill(call3(get("/api/level"), null, 200), "SPRECHEN")
                .path("evidenceCount").asInt() > 0);

        // Dort calisma da gecmise yazildi; Hören puansiz
        String gecmis = call3(get("/api/activities"), null, 200).toString();
        for (String tur : new String[]{"LESEN", "HOEREN", "SCHREIBEN", "SPRECHEN"}) {
            assertTrue(gecmis.contains(tur), "calisma gecmisinde yok: " + tur);
        }

        // Sinav: hedef (A2) calisma seviyesinin (A1) ustunde; pratik yine de hedef seviyeden gelir
        JsonNode sinav = call3(get("/api/exam"), null, 200);
        assertEquals("A2", sinav.path("target").asString());
        for (JsonNode beceri : sinav.path("skills")) {
            assertFalse(beceri.path("practice").isEmpty(),
                    beceri.path("skill").asString() + ": hedef seviyedeki pratik listelenmeli");
            for (JsonNode t : beceri.path("practice")) {
                assertEquals("A2", t.path("level").asString(), "sinav pratigi hedef seviyeden olmali");
            }
        }

        // Koc artik dort beceriyi de sitede gorebiliyor
        JsonNode koc = call3(get("/api/coach"), null, 200);
        assertTrue(koc.path("recommendations").size() <= 3);
    }

    private String readingAnswer(String textId, String questionId) {
        return catalog.readings().get(textId).questions().stream()
                .filter(q -> q.id().equals(questionId))
                .findFirst().orElseThrow().answer();
    }

    /** Beceri testinin istekleri: ucuncu kullanici. */
    private JsonNode call3(MockHttpServletRequestBuilder req, Object body, int expectedStatus)
            throws Exception {
        return call(req, body, expectedStatus, AUTH_ID_3);
    }

    /** Gramer testinin istekleri: ikinci kullanici. */
    private JsonNode call2(MockHttpServletRequestBuilder req, Object body, int expectedStatus) throws Exception {
        return call(req, body, expectedStatus, AUTH_ID_2);
    }

    private String catalogExerciseAnswer(String topicId, String exerciseId) {
        return catalog.lesson(topicId).orElseThrow().production().stream()
                .filter(e -> e.id().equals(exerciseId))
                .findFirst().orElseThrow().answer();
    }

    private static JsonNode skill(JsonNode level, String name) {
        for (JsonNode s : level.path("skills")) {
            if (name.equals(s.path("skill").asString())) {
                return s;
            }
        }
        throw new AssertionError("beceri yok: " + name);
    }
}
