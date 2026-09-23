package com.ichsprechedeutsch.content;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * Icerik dogrulayici (SPEC 10.2). Depodaki /content klasorunu okur ve her
 * build'de calisir. Kesin kurallar testi kirar; sezgisel kontrol (ornek
 * cumle kelimeyi iceriyor mu) yalniz raporlanir, cunku cekimli ve ayrilan
 * fiiller ("anfangen" -> "faengt ... an") yanlis alarm uretir.
 */
class ContentValidatorTest {

    private static final Set<String> LEVELS = Set.of("A0", "A1", "A2", "B1", "B2", "C1");
    private static final Set<String> TOPIC_KINDS = Set.of("GRAMMAR", "VOCABULARY", "READING", "LISTENING");
    private static final Set<String> PLACEMENT_KINDS = Set.of("GRAMMAR", "VOCABULARY", "READING");
    private static final Set<String> ARTICLES = Set.of("der", "die", "das");
    /** K-003: tekrar cozumde farkli sorular icin iki ayri 6'lik blok. */
    private static final int MIN_PLACEMENT_PER_LEVEL = 12;

    private static final Map<String, JsonNode> topics = new HashMap<>();
    private static final List<Item> words = new ArrayList<>();
    private static final List<Item> questions = new ArrayList<>();
    private static final List<Item> placement = new ArrayList<>();
    private static final List<Item> lessons = new ArrayList<>();
    private static final List<Item> readings = new ArrayList<>();
    private static final List<Item> listenings = new ArrayList<>();
    private static final List<Item> writings = new ArrayList<>();
    private static final List<Item> speakings = new ArrayList<>();
    private static final List<Item> canDos = new ArrayList<>();
    private static final List<Item> examLinks = new ArrayList<>();
    private static final Set<String> SKILLS = Set.of("LESEN", "HOEREN", "SCHREIBEN", "SPRECHEN");
    private static final Set<String> LISTENING_KINDS = Set.of("DICTATION", "COMPREHENSION");
    private static final Set<String> EXERCISE_TYPES = Set.of("GAP", "WORD_ORDER");

    record Item(String file, JsonNode node) {
        String id() {
            return node.path("id").asString("");
        }
    }

    @BeforeAll
    static void load() throws IOException {
        String dir = System.getProperty("content.dir");
        Path root = Path.of(dir != null ? dir : "../content");
        JsonMapper mapper = JsonMapper.builder().build();
        try (Stream<Path> files = Files.walk(root)) {
            for (Path p : files.filter(f -> f.toString().endsWith(".json")).toList()) {
                String rel = root.relativize(p).toString().replace('\\', '/');
                JsonNode arr = mapper.readTree(p.toFile());
                assertTrue(arr.isArray(), rel + ": kok eleman dizi olmali");
                for (JsonNode n : arr) {
                    switch (p.getFileName().toString()) {
                        case "grammar-tree.json" -> topics.put(n.path("id").asString(), n);
                        case "words.json" -> words.add(new Item(rel, n));
                        case "questions.json" -> questions.add(new Item(rel, n));
                        case "placement.json" -> placement.add(new Item(rel, n));
                        case "grammar.json" -> lessons.add(new Item(rel, n));
                        case "reading.json" -> readings.add(new Item(rel, n));
                        case "listening.json" -> listenings.add(new Item(rel, n));
                        case "writing.json" -> writings.add(new Item(rel, n));
                        case "speaking.json" -> speakings.add(new Item(rel, n));
                        case "cando.json" -> canDos.add(new Item(rel, n));
                        case "exam-links.json" -> examLinks.add(new Item(rel, n));
                        default -> fail("Bilinmeyen icerik dosyasi: " + rel);
                    }
                }
            }
        }
    }

    @Test
    void idsAreUniqueAcrossAllContent() {
        List<String> errors = new ArrayList<>();
        Set<String> seen = new HashSet<>(topics.keySet());
        for (List<Item> list : List.of(words, questions, placement, readings, listenings,
                writings, speakings, canDos, examLinks)) {
            for (Item it : list) {
                if (it.id().isBlank()) {
                    errors.add(it.file() + ": id bos");
                } else if (!seen.add(it.id())) {
                    errors.add(it.file() + ": yinelenen id " + it.id());
                }
            }
        }
        report(errors);
    }

    @Test
    void grammarTreeIsWellFormed() {
        List<String> errors = new ArrayList<>();
        topics.forEach((id, n) -> {
            String kind = n.path("kind").asString("");
            if (!TOPIC_KINDS.contains(kind)) {
                errors.add(id + ": gecersiz kind " + kind);
            }
            if ("GRAMMAR".equals(kind)) {
                if (!LEVELS.contains(n.path("level").asString(""))) {
                    errors.add(id + ": gramer konusunun seviyesi yok");
                }
                if (n.path("estimatedMinutes").asInt(0) <= 0) {
                    errors.add(id + ": estimatedMinutes eksik");
                }
            }
            requireText(errors, id, n, "titleTr");
            requireBoolean(errors, id, n, "core");
            requireBoolean(errors, id, n, "verified");
        });
        report(errors);
    }

    @Test
    void wordsHaveRequiredFieldsAndValidMorphology() {
        List<String> errors = new ArrayList<>();
        Set<String> lemmaMeaning = new HashSet<>();
        for (Item it : words) {
            JsonNode n = it.node();
            String where = it.file() + " " + it.id();
            requireText(errors, where, n, "lemma");
            requireText(errors, where, n, "meaningTr");
            requireText(errors, where, n, "exampleDe");
            requireText(errors, where, n, "exampleTr");
            requireLevel(errors, where, n);
            requireMinutes(errors, where, n);
            requireBoolean(errors, where, n, "verified");

            String article = n.path("article").isString() ? n.path("article").asString() : null;
            boolean noun = "NOUN".equals(n.path("partOfSpeech").asString(""));
            if (noun && (article == null || !ARTICLES.contains(article))) {
                errors.add(where + ": isim ama artikel der/die/das degil (" + article + ")");
            }
            if (!noun && article != null) {
                errors.add(where + ": isim olmayan kelimede artikel var");
            }
            if (n.path("plural").isString()) {
                String plural = n.path("plural").asString();
                if (plural.isBlank() || !plural.equals(plural.trim())
                        || plural.toLowerCase(Locale.ROOT).startsWith("die ")) {
                    errors.add(where + ": cogul bicimi hatali (artikelsiz, bosluksuz olmali): '" + plural + "'");
                }
            }
            if (!lemmaMeaning.add(n.path("lemma").asString() + "|" + n.path("meaningTr").asString())) {
                errors.add(where + ": ayni kelime + anlam iki kez");
            }
        }
        report(errors);
    }

    @Test
    void questionsHaveExactlyOneCorrectAnswer() {
        List<String> errors = new ArrayList<>();
        List<Item> all = new ArrayList<>(questions);
        all.addAll(placement);
        for (Item it : all) {
            JsonNode n = it.node();
            String where = it.file() + " " + it.id();
            requireText(errors, where, n, "prompt");
            requireLevel(errors, where, n);
            requireMinutes(errors, where, n);
            requireBoolean(errors, where, n, "verified");

            List<String> options = texts(n.path("options"));
            String answer = n.path("answer").asString("");
            if ("MULTIPLE_CHOICE".equals(n.path("type").asString(""))) {
                if (options.size() < 2) {
                    errors.add(where + ": en az 2 sik olmali");
                }
                if (new HashSet<>(options).size() != options.size()) {
                    errors.add(where + ": ayni sik iki kez var");
                }
                if (!options.contains(answer)) {
                    errors.add(where + ": dogru cevap siklar arasinda degil: " + answer);
                }
                for (String accepted : texts(n.path("acceptedAnswers"))) {
                    if (options.contains(accepted) && !accepted.equals(answer)) {
                        errors.add(where + ": kabul edilen cevap baska bir sik, iki dogru sik olur: " + accepted);
                    }
                }
            } else if (answer.isBlank()) {
                errors.add(where + ": cevap bos");
            }

            List<String> tags = texts(n.path("tags"));
            if (tags.isEmpty()) {
                errors.add(where + ": etiket yok");
            }
            for (String tag : tags) {
                if (!topics.containsKey(tag)) {
                    errors.add(where + ": etiket gramer agacinda yok: " + tag);
                }
            }
        }
        report(errors);
    }

    /** SPEC 8.2: ders icerigi konuya bagli, ornekli ve kontrollu uretimli olmali. */
    @Test
    void grammarLessonsAreWellFormed() {
        List<String> errors = new ArrayList<>();
        Set<String> seenTopics = new HashSet<>();
        for (Item it : lessons) {
            JsonNode n = it.node();
            String topicId = n.path("topicId").asString("");
            String where = it.file() + " " + topicId;
            JsonNode topic = topics.get(topicId);
            if (topic == null) {
                errors.add(where + ": gramer agacinda olmayan konu");
                continue;
            }
            if (!"GRAMMAR".equals(topic.path("kind").asString(""))) {
                errors.add(where + ": ders yalniz GRAMMAR konusuna baglanir");
            }
            if (!topic.path("level").asString("").equals(n.path("level").asString(""))) {
                errors.add(where + ": ders seviyesi konunun seviyesiyle ayni olmali");
            }
            if (!seenTopics.add(topicId)) {
                errors.add(where + ": ayni konu icin birden fazla ders");
            }
            requireText(errors, where, n, "explanationTr");
            requireBoolean(errors, where, n, "verified");

            if (n.path("examples").size() < 2) {
                errors.add(where + ": en az iki ornek gerekli");
            }
            for (JsonNode e : n.path("examples")) {
                requireText(errors, where + " ornek", e, "de");
                requireText(errors, where + " ornek", e, "tr");
            }

            if (n.path("production").size() < 2) {
                errors.add(where + ": en az iki kontrollu uretim alistirmasi gerekli");
            }
            for (JsonNode ex : n.path("production")) {
                String exId = ex.path("id").asString("");
                String exWhere = where + " " + exId;
                if (exId.isBlank() || !seenTopics.add("EX:" + exId)) {
                    errors.add(exWhere + ": alistirma id'si bos ya da yinelenen");
                }
                String type = ex.path("type").asString("");
                if (!EXERCISE_TYPES.contains(type)) {
                    errors.add(exWhere + ": gecersiz alistirma turu " + type);
                }
                requireText(errors, exWhere, ex, "promptTr");
                requireText(errors, exWhere, ex, "answer");
                requireText(errors, exWhere, ex, "explanationTr");

                if ("GAP".equals(type)) {
                    if (!ex.path("prompt").asString("").contains("___")) {
                        errors.add(exWhere + ": bosluk doldurmada ___ isareti yok");
                    }
                } else if ("WORD_ORDER".equals(type)) {
                    List<String> parts = new ArrayList<>();
                    ex.path("parts").forEach(p2 -> parts.add(p2.asString("")));
                    if (parts.size() < 3) {
                        errors.add(exWhere + ": kelime siralamada en az uc parca gerekli");
                    }
                    String cevap = ex.path("answer").asString("").toLowerCase(Locale.ROOT);
                    for (String part : parts) {
                        if (!cevap.contains(part.toLowerCase(Locale.ROOT))) {
                            errors.add(exWhere + ": parca cevapta gecmiyor: " + part);
                        }
                    }
                }
            }
        }
        report(errors);
    }

    /** Ders icerigi olan konularin mini sorusu da olmali (SPEC 8.2 dongusu). */
    @Test
    void lessonTopicsHaveMiniQuestions() {
        List<String> errors = new ArrayList<>();
        for (Item it : lessons) {
            String topicId = it.node().path("topicId").asString("");
            boolean var = questions.stream()
                    .anyMatch(q -> {
                        for (JsonNode t : q.node().path("tags")) {
                            if (topicId.equals(t.asString(""))) {
                                return true;
                            }
                        }
                        return false;
                    });
            if (!var) {
                errors.add(it.file() + " " + topicId + ": konunun mini sorusu yok");
            }
        }
        report(errors);
    }

    /** SPEC 7 / 4.2: okuma ve dinleme sorulari tek dogru cevap tasimali. */
    @Test
    void readingAndListeningAreWellFormed() {
        List<String> errors = new ArrayList<>();
        for (Item it : readings) {
            JsonNode n = it.node();
            String where = it.file() + " " + it.id();
            requireLevel(errors, where, n);
            requireMinutes(errors, where, n);
            requireText(errors, where, n, "titleTr");
            requireText(errors, where, n, "topicTr");
            requireText(errors, where, n, "text");
            requireBoolean(errors, where, n, "verified");
            if (n.path("questions").size() < 3) {
                errors.add(where + ": okuma metninde en az uc soru olmali");
            }
            sorulariKontrolEt(errors, where, n.path("questions"));
        }

        for (Item it : listenings) {
            JsonNode n = it.node();
            String where = it.file() + " " + it.id();
            requireLevel(errors, where, n);
            requireMinutes(errors, where, n);
            requireText(errors, where, n, "titleTr");
            requireText(errors, where, n, "text");
            requireText(errors, where, n, "textTr");
            requireBoolean(errors, where, n, "verified");
            String kind = n.path("kind").asString("");
            if (!LISTENING_KINDS.contains(kind)) {
                errors.add(where + ": gecersiz dinleme turu " + kind);
            }
            if ("COMPREHENSION".equals(kind) && n.path("questions").size() < 2) {
                errors.add(where + ": anlama alistirmasinda en az iki soru olmali");
            }
            if ("DICTATION".equals(kind) && !n.path("questions").isEmpty()
                    && n.path("questions").size() > 0) {
                errors.add(where + ": dikte alistirmasinda soru listesi bos olmali");
            }
            sorulariKontrolEt(errors, where, n.path("questions"));
        }
        report(errors);
    }

    /** SPEC 4.2: yazma gorevi ornek cevap ve kalip tasir; konusma gorevi yonlendirici sorular. */
    @Test
    void writingAndSpeakingTasksAreWellFormed() {
        List<String> errors = new ArrayList<>();
        for (Item it : writings) {
            JsonNode n = it.node();
            String where = it.file() + " " + it.id();
            requireLevel(errors, where, n);
            requireMinutes(errors, where, n);
            requireText(errors, where, n, "titleTr");
            requireText(errors, where, n, "taskTr");
            requireText(errors, where, n, "taskDe");
            requireText(errors, where, n, "sampleAnswer");
            requireBoolean(errors, where, n, "verified");
            int minWords = n.path("minWords").asInt(0);
            if (minWords <= 0) {
                errors.add(where + ": minWords eksik");
            }
            if (n.path("phrases").size() < 3) {
                errors.add(where + ": en az uc hazir kalip gerekli");
            }
            int ornekKelime = n.path("sampleAnswer").asString("").trim().split("\\s+").length;
            if (ornekKelime < minWords) {
                errors.add(where + ": ornek cevap istenen uzunluktan kisa (" + ornekKelime + " < " + minWords + ")");
            }
        }

        for (Item it : speakings) {
            JsonNode n = it.node();
            String where = it.file() + " " + it.id();
            requireLevel(errors, where, n);
            requireMinutes(errors, where, n);
            requireText(errors, where, n, "titleTr");
            requireText(errors, where, n, "taskTr");
            requireBoolean(errors, where, n, "verified");
            if (n.path("promptsDe").size() < 3) {
                errors.add(where + ": en az uc yonlendirici soru gerekli");
            }
            if (n.path("phrases").size() < 3) {
                errors.add(where + ": en az uc hazir kalip gerekli");
            }
        }
        report(errors);
    }

    /**
     * SPEC 4.2: Kann-Beschreibungen oz degerlendirme icin kullanilir; her
     * konusma gorevinin seviyesinde ifade bulunmali (yoksa kanit uretilemez).
     */
    @Test
    void canDosCoverSpeakingLevels() {
        List<String> errors = new ArrayList<>();
        Set<String> konusmaSeviyeleri = new HashSet<>();
        for (Item it : canDos) {
            JsonNode n = it.node();
            String where = it.file() + " " + it.id();
            requireLevel(errors, where, n);
            requireText(errors, where, n, "textTr");
            requireBoolean(errors, where, n, "verified");
            if (!SKILLS.contains(n.path("skill").asString(""))) {
                errors.add(where + ": gecersiz beceri " + n.path("skill").asString(""));
            }
        }
        for (Item it : speakings) {
            konusmaSeviyeleri.add(it.node().path("level").asString(""));
        }
        for (String seviye : konusmaSeviyeleri) {
            boolean var = canDos.stream().anyMatch(c -> seviye.equals(c.node().path("level").asString(""))
                    && "SPRECHEN".equals(c.node().path("skill").asString("")));
            if (!var) {
                errors.add(seviye + ": konusma gorevi var ama Kann-Beschreibung yok");
            }
        }
        report(errors);
    }

    private static void sorulariKontrolEt(List<String> errors, String where, JsonNode sorular) {
        for (JsonNode q : sorular) {
            String qid = q.path("id").asString("");
            String qWhere = where + " " + qid;
            if (qid.isBlank()) {
                errors.add(qWhere + ": soru id'si bos");
            }
            requireText(errors, qWhere, q, "prompt");
            requireText(errors, qWhere, q, "answer");
            requireText(errors, qWhere, q, "explanationTr");
            List<String> secenekler = new ArrayList<>();
            q.path("options").forEach(o -> secenekler.add(o.asString("")));
            if (!secenekler.isEmpty()) {
                if (secenekler.size() < 2) {
                    errors.add(qWhere + ": en az iki secenek gerekli");
                }
                if (!secenekler.contains(q.path("answer").asString(""))) {
                    errors.add(qWhere + ": dogru cevap secenekler arasinda yok");
                }
                long dogruSayisi = secenekler.stream()
                        .filter(o -> o.equals(q.path("answer").asString(""))).count();
                if (dogruSayisi != 1) {
                    errors.add(qWhere + ": dogru cevap secenek listesinde bir kez gecmeli");
                }
            }
        }
    }

    /** SPEC 10.2: dis link uydurulmaz; verified:false ve kurum koku olmali. */
    @Test
    void examLinksAreHonest() {
        List<String> errors = new ArrayList<>();
        for (Item it : examLinks) {
            JsonNode n = it.node();
            String where = it.file() + " " + it.id();
            requireText(errors, where, n, "institution");
            requireText(errors, where, n, "nameTr");
            requireText(errors, where, n, "noteTr");
            requireBoolean(errors, where, n, "free");
            requireBoolean(errors, where, n, "verified");
            if (n.path("verified").asBoolean(false)) {
                errors.add(where + ": link dogrulugunu yalniz kullanici isaretler");
            }
            String url = n.path("url").asString("");
            if (!url.startsWith("https://")) {
                errors.add(where + ": url https ile baslamali");
            }
            // Derin link uydurmamak icin kurum koku yeter (SPEC 10.2).
            if (url.chars().filter(c -> c == '/').count() > 3) {
                errors.add(where + ": derin link yerine kurumun kok adresi kullanilmali: " + url);
            }
            if (n.path("levels").isEmpty()) {
                errors.add(where + ": en az bir seviye gerekli");
            }
            for (JsonNode lvl : n.path("levels")) {
                if (!LEVELS.contains(lvl.asString(""))) {
                    errors.add(where + ": gecersiz seviye " + lvl.asString(""));
                }
            }
        }
        report(errors);
    }

    @Test
    void placementPoolCoversEveryLevel() {
        List<String> errors = new ArrayList<>();
        Map<String, Integer> perLevel = new HashMap<>();
        for (Item it : placement) {
            String kind = it.node().path("placementKind").asString("");
            if (!PLACEMENT_KINDS.contains(kind)) {
                errors.add(it.id() + ": placementKind gecersiz: " + kind);
            }
            perLevel.merge(it.node().path("level").asString(), 1, Integer::sum);
        }
        for (String level : List.of("A1", "A2", "B1", "B2", "C1")) {
            int n = perLevel.getOrDefault(level, 0);
            if (n < MIN_PLACEMENT_PER_LEVEL) {
                errors.add(level + ": yerlestirme havuzunda " + n + " soru var, en az " + MIN_PLACEMENT_PER_LEVEL);
            }
        }
        report(errors);
    }

    /** Sezgisel: ornek cumle kelimeyi iceriyor mu. Testi kirmaz, raporlar. */
    @Test
    void exampleSentencesMentionTheWordReportOnly() {
        List<String> suspicious = new ArrayList<>();
        for (Item it : words) {
            JsonNode n = it.node();
            String example = n.path("exampleDe").asString("").toLowerCase(Locale.ROOT);
            String lemma = n.path("lemma").asString("").toLowerCase(Locale.ROOT);
            String plural = n.path("plural").isString() ? n.path("plural").asString().toLowerCase(Locale.ROOT) : "";
            String stem = lemma.replaceAll("(en|n|e)$", "");
            boolean found = example.contains(lemma)
                    || (!plural.isBlank() && example.contains(plural))
                    || (stem.length() >= 3 && example.contains(stem));
            if (!found) {
                suspicious.add(it.id() + " (" + n.path("lemma").asString() + ")");
            }
        }
        System.out.println("[icerik] ornek cumlede kelime bulunamayan " + suspicious.size()
                + " kayit (elle incelenmeli): " + suspicious);
    }

    // ------------------------------------------------------------------

    private static void report(List<String> errors) {
        if (!errors.isEmpty()) {
            fail(errors.size() + " icerik hatasi:\n  " + String.join("\n  ", errors));
        }
    }

    private static List<String> texts(JsonNode arr) {
        List<String> out = new ArrayList<>();
        if (arr != null && arr.isArray()) {
            arr.forEach(x -> out.add(x.asString()));
        }
        return out;
    }

    private static void requireText(List<String> errors, String where, JsonNode n, String field) {
        if (n.path(field).asString("").isBlank()) {
            errors.add(where + ": zorunlu alan bos: " + field);
        }
    }

    private static void requireBoolean(List<String> errors, String where, JsonNode n, String field) {
        if (!n.path(field).isBoolean()) {
            errors.add(where + ": " + field + " true/false olmali");
        }
    }

    private static void requireLevel(List<String> errors, String where, JsonNode n) {
        if (!LEVELS.contains(n.path("level").asString(""))) {
            errors.add(where + ": seviye gecersiz: " + n.path("level").asString(""));
        }
    }

    private static void requireMinutes(List<String> errors, String where, JsonNode n) {
        if (n.path("estimatedMinutes").asInt(0) <= 0) {
            errors.add(where + ": estimatedMinutes eksik");
        }
    }
}
