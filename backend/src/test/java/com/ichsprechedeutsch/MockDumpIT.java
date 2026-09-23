package com.ichsprechedeutsch;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;

import com.ichsprechedeutsch.content.ContentCatalog;
import com.ichsprechedeutsch.content.Question;
import java.nio.file.Files;
import java.nio.file.Path;
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
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * Arayuz dogrulamasi icin gercek API yanitlarini dosyaya doker (K-020).
 * Girisi olmayan bir sahte kopyada sayfalari denemek icin kullanilir:
 * gercek hesap acilmaz, uydurma JSON yazilmaz.
 *
 * <pre>MOCK_DUMP=&lt;klasor&gt; ./gradlew test --tests '*MockDumpIT'</pre>
 *
 * <p>Kendi test kullanicisini gelistirme veritabaninda olusturur ve
 * sonunda siler. MOCK_DUMP verilmezse atlanir.
 */
@SpringBootTest
@AutoConfigureMockMvc
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@EnabledIfEnvironmentVariable(named = "MOCK_DUMP", matches = ".+")
class MockDumpIT {

    private static final UUID AUTH_ID = UUID.randomUUID();

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
        jdbc.update("DELETE FROM app_user WHERE auth_user_id = ?", AUTH_ID);
    }

    private String call(MockHttpServletRequestBuilder req, Object body, int expected) throws Exception {
        req.with(jwt().jwt(j -> j.subject(AUTH_ID.toString()).claim("email", "mock@test.invalid")));
        if (body != null) {
            req.contentType(MediaType.APPLICATION_JSON).content(mapper.writeValueAsString(body));
        }
        var res = mvc.perform(req).andReturn().getResponse();
        assertEquals(expected, res.getStatus(), "yanit: " + res.getContentAsString());
        return res.getContentAsString();
    }

    private Map<String, Object> activity(LocalDate date, String type, int minutes) {
        Map<String, Object> a = new LinkedHashMap<>();
        a.put("date", date.toString());
        a.put("type", type);
        a.put("durationMinutes", minutes);
        a.put("sourceKind", "OTHER_APP");
        return a;
    }

    @Test
    void dump() throws Exception {
        Path out = Path.of(System.getenv("MOCK_DUMP"));
        Files.createDirectories(out);
        LocalDate today = LocalDate.now(ZoneId.of("Europe/Istanbul"));

        call(put("/api/onboarding/start"), Map.of("startMode", "SOME_KNOWLEDGE"), 200);

        // Yerlestirme: ilk blok 5/6 (yukari), ikinci blok 3/6 (asagi) -> sonuc
        for (int blok = 0; blok < 2; blok++) {
            JsonNode node = mapper.readTree(call(post("/api/placement/start"), null, 200));
            String session = node.path("sessionId").asString();
            Map<String, String> answers = new LinkedHashMap<>();
            int k = 0;
            for (JsonNode q : node.path("questions")) {
                Question full = catalog.question(q.path("id").asString()).orElseThrow();
                answers.put(full.id(), k++ < (blok == 0 ? 5 : 3) ? full.answer() : "yanlis");
            }
            call(post("/api/placement/" + session + "/answers"), Map.of("answers", answers), 200);
        }

        call(post("/api/onboarding/complete"),
                Map.of("target", "B1", "purpose", "EXAM", "dailyMinutes", 45, "daysPerWeek", 5), 200);

        // Birkac gunluk calisma gecmisi
        Map<String, Object> lesen = activity(today.minusDays(2), "LESEN", 30);
        lesen.put("level", "A2");
        lesen.put("score", 8);
        lesen.put("maxScore", 10);
        lesen.put("resultSource", "TEACHER");
        lesen.put("sourceKind", "BOOK");
        call(post("/api/activities"), lesen, 201);

        Map<String, Object> hoeren = activity(today.minusDays(1), "HOEREN", 25);
        hoeren.put("level", "A2");
        hoeren.put("score", 6);
        hoeren.put("maxScore", 10);
        hoeren.put("resultSource", "MODELLTEST");
        hoeren.put("sourceKind", "PODCAST");
        call(post("/api/activities"), hoeren, 201);

        call(post("/api/activities"), activity(today.minusDays(1), "KELIME", 20), 201);
        call(post("/api/activities"), activity(today.minusDays(4), "GRAMER", 40), 201);
        call(post("/api/activities"), activity(today, "KELIME", 15), 201);

        // Ogrenme modulleri: bir kelime ve bir artikel yaniti ver ki
        // istatistik ekranlari bos gorunmesin.
        JsonNode kelimeOturum = mapper.readTree(call(post("/api/words/session"), null, 200));
        Files.writeString(out.resolve("words-session.json"), kelimeOturum.toString());
        String wordSession = kelimeOturum.path("sessionId").asString();
        JsonNode ilk = kelimeOturum.path("cards").get(0);
        Files.writeString(out.resolve("words-answer.json"),
                call(post("/api/words/answer"), Map.of("sessionId", wordSession,
                        "wordId", ilk.path("wordId").asString(), "grade", "BILDIM"), 200));

        JsonNode artikelOturum = mapper.readTree(call(post("/api/article/session?count=8"), null, 200));
        Files.writeString(out.resolve("article-session.json"), artikelOturum.toString());
        String artikelId = artikelOturum.path("items").get(0).path("wordId").asString();
        Files.writeString(out.resolve("article-answer.json"),
                call(post("/api/article/answer"), Map.of("sessionId", artikelOturum.path("sessionId").asString(),
                        "wordId", artikelId, "given", "die"), 200));

        // Gramer: bir konuyu calis, bir yanlis bir dogru ver ki hata hafizasi dolsun.
        JsonNode ders = mapper.readTree(call(post("/api/grammar/topics/DATIV/session"), null, 200));
        Files.writeString(out.resolve("grammar-lesson.json"), ders.toString());
        String gramerSession = ders.path("sessionId").asString();
        String ilkAdim = ders.path("steps").get(0).path("id").asString();
        Files.writeString(out.resolve("grammar-answer.json"),
                call(post("/api/grammar/answer"), Map.of("sessionId", gramerSession,
                        "topicId", "DATIV", "itemId", ilkAdim, "given", "yanlis-cevap"), 200));
        String ikinciAdim = ders.path("steps").get(1).path("id").asString();
        call(post("/api/grammar/answer"), Map.of("sessionId", gramerSession,
                "topicId", "DATIV", "itemId", ikinciAdim, "given", "der"), 200);
        call(post("/api/grammar/session/" + gramerSession + "/finish"),
                Map.of("interactions", new long[]{System.currentTimeMillis()},
                        "completed", true, "topicId", "DATIV"), 204);

        Files.writeString(out.resolve("grammar-topics.json"), call(get("/api/grammar/topics"), null, 200));
        Files.writeString(out.resolve("grammar-stats.json"), call(get("/api/grammar/stats"), null, 200));
        Files.writeString(out.resolve("grammar-errors.json"), call(get("/api/grammar/errors"), null, 200));

        // Dort beceri ve sinav (Faz 3c, 4)
        for (String yol : new String[]{"reading", "listening", "writing", "speaking"}) {
            JsonNode liste = mapper.readTree(call(get("/api/skills/" + yol), null, 200));
            Files.writeString(out.resolve("skills-" + yol + ".json"), liste.toString());
            String id = liste.get(0).path("id").asString();
            Files.writeString(out.resolve("skills-" + yol + "-session.json"),
                    call(post("/api/skills/" + yol + "/" + id + "/session"), null, 200));
        }
        JsonNode okuma = mapper.readTree(java.nio.file.Files.readString(out.resolve("skills-reading-session.json")));
        Map<String, String> okumaCevap = new LinkedHashMap<>();
        for (JsonNode q : okuma.path("questions")) {
            okumaCevap.put(q.path("id").asString(), "yanlis");
        }
        Files.writeString(out.resolve("skills-reading-submit.json"),
                call(post("/api/skills/reading/" + okuma.path("id").asString() + "/submit"),
                        Map.of("sessionId", okuma.path("sessionId").asString(), "answers", okumaCevap), 200));
        JsonNode dinleme = mapper.readTree(java.nio.file.Files.readString(out.resolve("skills-listening-session.json")));
        Files.writeString(out.resolve("skills-listening-submit.json"),
                call(post("/api/skills/listening/" + dinleme.path("id").asString() + "/submit"),
                        Map.of("sessionId", dinleme.path("sessionId").asString(),
                                "dictation", "Ich heisse Anna und komme aus"), 200));
        Files.writeString(out.resolve("exam.json"), call(get("/api/exam"), null, 200));

        Files.writeString(out.resolve("onboarding.json"), call(get("/api/onboarding"), null, 200));
        Files.writeString(out.resolve("level.json"), call(get("/api/level"), null, 200));
        Files.writeString(out.resolve("coach.json"), call(get("/api/coach"), null, 200));
        Files.writeString(out.resolve("activity-options.json"), call(get("/api/activities/options"), null, 200));
        Files.writeString(out.resolve("activities.json"),
                call(get("/api/activities?from=" + today.minusDays(6) + "&to=" + today), null, 200));
        Files.writeString(out.resolve("me.json"), call(get("/api/me"), null, 200));
        Files.writeString(out.resolve("words-stats.json"), call(get("/api/words/stats"), null, 200));
        Files.writeString(out.resolve("article-stats.json"), call(get("/api/article/stats"), null, 200));
        Files.writeString(out.resolve("placement-block.json"), call(post("/api/placement/start"), null, 200));
    }
}
