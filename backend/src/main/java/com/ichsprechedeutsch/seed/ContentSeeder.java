package com.ichsprechedeutsch.seed;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Icerik havuzunu CSV'lerden veritabanina yukler.
 *
 * Idempotenttir: her acilista calisir, var olan kaydi gunceller, yenisini
 * ekler, gecersiz kalan bagi siler. Dogal anahtarlar (code, lemma+meaning_tr)
 * uzerinden calistigi icin iki kez calismak kayit ciftlemez.
 *
 * Icerik eklemek icin /content altindaki CSV'ye satir eklemek yeterlidir;
 * kod degismez.
 *
 * Butun yazmalar batchUpdate ile toplu gonderilir. Satir satir gondermek
 * Frankfurt'a her seferinde ~85 ms gidis-donus demek; 271 kelime tek basina
 * 23 saniye ediyordu. Render'da her soguk acilista bu bedel odeniyor.
 */
@Component
public class ContentSeeder implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(ContentSeeder.class);

    private final JdbcTemplate jdbc;
    private final boolean enabled;

    public ContentSeeder(JdbcTemplate jdbc,
                         @Value("${app.seed.enabled:true}") boolean enabled) {
        this.jdbc = jdbc;
        this.enabled = enabled;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) throws Exception {
        if (!enabled) {
            log.info("Icerik yukleme kapali (app.seed.enabled=false)");
            return;
        }

        long start = System.currentTimeMillis();

        seedMistakeCategories();
        seedContentUnits();
        seedContentUnitCategories();
        seedBlocks();
        seedWords();
        linkWordsToUnits();
        seedTests();
        seedQuestions();
        seedQuestionOptions();
        linkTestQuestions();

        log.info("Icerik yuklendi ({} ms): {} kategori, {} birim, {} birim-kategori, "
                        + "{} blok, {} kelime, {} birim-kelime bagi",
                System.currentTimeMillis() - start,
                count("mistake_category"), count("content_unit"),
                count("content_unit_category"), count("content_unit_block"),
                count("word"), count("content_unit_word"));

        log.info("Olcme: {} test, {} soru, {} secenek, {} test-soru bagi",
                count("test"), count("question"),
                count("question_option"), count("test_question"));
    }

    // ------------------------------------------------------------------

    private void seedMistakeCategories() throws Exception {
        List<Object[]> batch = new ArrayList<>();
        for (Map<String, String> r : load("seed/mistake_categories.csv")) {
            batch.add(new Object[]{r.get("code"), r.get("name_tr"), r.get("skill")});
        }
        jdbc.batchUpdate("""
                INSERT INTO mistake_category (code, name_tr, skill)
                VALUES (?, ?, ?)
                ON CONFLICT (code) DO UPDATE
                SET name_tr = EXCLUDED.name_tr, skill = EXCLUDED.skill
                """, batch);
    }

    private void seedContentUnits() throws Exception {
        List<Object[]> batch = new ArrayList<>();
        for (Map<String, String> r : load("seed/content_units.csv")) {
            batch.add(new Object[]{
                    r.get("code"), r.get("level"), r.get("phase"),
                    Integer.parseInt(r.get("sequence_no")), r.get("title_tr"),
                    nullIfBlank(r.get("grammar_summary")),
                    nullIfBlank(r.get("vocab_theme")),
                    Integer.parseInt(r.get("estimated_minutes")),
                    Boolean.parseBoolean(r.get("is_new_content")),
            });
        }
        jdbc.batchUpdate("""
                INSERT INTO content_unit
                    (code, level, phase, sequence_no, title_tr, grammar_summary,
                     vocab_theme, estimated_minutes, is_new_content)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
                ON CONFLICT (code) DO UPDATE SET
                    level = EXCLUDED.level,
                    phase = EXCLUDED.phase,
                    sequence_no = EXCLUDED.sequence_no,
                    title_tr = EXCLUDED.title_tr,
                    grammar_summary = EXCLUDED.grammar_summary,
                    vocab_theme = EXCLUDED.vocab_theme,
                    estimated_minutes = EXCLUDED.estimated_minutes,
                    is_new_content = EXCLUDED.is_new_content
                """, batch);
    }

    private void seedContentUnitCategories() throws Exception {
        List<Object[]> batch = new ArrayList<>();
        for (Map<String, String> r : load("seed/content_unit_categories.csv")) {
            batch.add(new Object[]{
                    Short.parseShort(r.get("weight")),
                    r.get("content_unit_code"),
                    r.get("mistake_category_code"),
            });
        }
        int[] written = jdbc.batchUpdate("""
                INSERT INTO content_unit_category
                    (content_unit_id, mistake_category_id, weight)
                SELECT cu.id, mc.id, ?
                FROM content_unit cu, mistake_category mc
                WHERE cu.code = ? AND mc.code = ?
                ON CONFLICT (content_unit_id, mistake_category_id) DO UPDATE
                SET weight = EXCLUDED.weight
                """, batch);

        long eslesmeyen = Arrays.stream(written).filter(n -> n == 0).count();
        if (eslesmeyen > 0) {
            log.warn("{} birim-kategori satiri eslesmedi (kod yanlis olabilir)", eslesmeyen);
        }
    }

    /**
     * Blok sablonlari ya bir birim koduna ya da bir asamaya baglidir.
     * Birime ozel satir varsa o kullanilir; yoksa asamanin sablonu uygulanir.
     *
     * Ikinci gecis birincinin yazdigina baktigi icin ikisi sirayla calisir;
     * her gecis kendi icinde toplu gonderilir.
     */
    private void seedBlocks() throws Exception {
        List<Object[]> byUnit = new ArrayList<>();
        List<Object[]> byPhase = new ArrayList<>();

        for (Map<String, String> r : load("seed/block_templates.csv")) {
            String appliesTo = r.get("applies_to");
            String taskType = r.get("task_type");
            int minutes = Integer.parseInt(r.get("reference_minutes"));
            String instruction = nullIfBlank(r.get("instruction_tr"));

            byUnit.add(new Object[]{taskType, minutes, instruction, appliesTo});
            byPhase.add(new Object[]{taskType, minutes, instruction, appliesTo, taskType});
        }

        jdbc.batchUpdate("""
                INSERT INTO content_unit_block
                    (content_unit_id, task_type, reference_minutes, instruction_tr)
                SELECT cu.id, ?, ?, ?
                FROM content_unit cu
                WHERE cu.code = ?
                ON CONFLICT (content_unit_id, task_type) DO UPDATE SET
                    reference_minutes = EXCLUDED.reference_minutes,
                    instruction_tr = EXCLUDED.instruction_tr
                """, byUnit);

        jdbc.batchUpdate("""
                INSERT INTO content_unit_block
                    (content_unit_id, task_type, reference_minutes, instruction_tr)
                SELECT cu.id, ?, ?, ?
                FROM content_unit cu
                WHERE cu.phase = ?
                  AND NOT EXISTS (
                      SELECT 1 FROM content_unit_block b
                      WHERE b.content_unit_id = cu.id AND b.task_type = ?
                  )
                ON CONFLICT (content_unit_id, task_type) DO NOTHING
                """, byPhase);
    }

    private void seedWords() throws Exception {
        List<Object[]> batch = new ArrayList<>();
        for (String path : List.of("seed/words_a1.csv", "seed/words_a2.csv")) {
            for (Map<String, String> r : load(path)) {
                batch.add(new Object[]{
                        r.get("lemma"), r.get("word_type"),
                        nullIfBlank(r.get("article")), nullIfBlank(r.get("plural_form")),
                        r.get("meaning_tr"),
                        nullIfBlank(r.get("example_de")), nullIfBlank(r.get("example_tr")),
                        r.get("level"), nullIfBlank(r.get("theme")),
                        "Ozgun icerik; morfoloji de.wiktionary.org ile dogrulandi",
                        "Ozgun (morfolojik dogrulama: CC BY-SA 4.0)",
                });
            }
        }
        jdbc.batchUpdate("""
                INSERT INTO word
                    (lemma, word_type, article, plural_form, meaning_tr,
                     example_de, example_tr, level, theme, source, license)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                ON CONFLICT (lemma, meaning_tr) DO UPDATE SET
                    word_type = EXCLUDED.word_type,
                    article = EXCLUDED.article,
                    plural_form = EXCLUDED.plural_form,
                    example_de = EXCLUDED.example_de,
                    example_tr = EXCLUDED.example_tr,
                    level = EXCLUDED.level,
                    theme = EXCLUDED.theme
                """, batch);
    }

    /**
     * Kelimeyi temasi uyan icerik birimine baglar.
     *
     * Once artik gecerli olmayan baglar silinir: bir kelimenin temasi CSV'de
     * degistirilirse eski bag tabloda kalmasin. Bu olmadan seeder yalnizca
     * ekleyen, hic temizlemeyen bir islem olur ve tekrar calistirmak
     * veritabanini CSV ile ayni hale getirmez.
     */
    private void linkWordsToUnits() {
        int removed = jdbc.update("""
                DELETE FROM content_unit_word cuw
                USING content_unit cu, word w
                WHERE cuw.content_unit_id = cu.id
                  AND cuw.word_id = w.id
                  AND w.theme IS DISTINCT FROM cu.vocab_theme
                """);
        if (removed > 0) {
            log.info("Temasi degisen {} birim-kelime bagi temizlendi", removed);
        }

        jdbc.update("""
                INSERT INTO content_unit_word (content_unit_id, word_id)
                SELECT cu.id, w.id
                FROM content_unit cu
                JOIN word w ON w.theme = cu.vocab_theme
                ON CONFLICT DO NOTHING
                """);
    }


    // ---- olcme -------------------------------------------------------

    private void seedTests() throws Exception {
        List<Object[]> batch = new ArrayList<>();
        for (Map<String, String> r : load("seed/tests.csv")) {
            batch.add(new Object[]{
                    r.get("code"), r.get("test_type"), r.get("level"), r.get("title_tr"),
                    Integer.parseInt(r.get("question_count")),
                    Integer.parseInt(r.get("time_limit_minutes")),
            });
        }
        jdbc.batchUpdate("""
                INSERT INTO test
                    (code, test_type, level, title_tr, question_count, time_limit_minutes)
                VALUES (?, ?, ?, ?, ?, ?)
                ON CONFLICT (code) DO UPDATE SET
                    test_type = EXCLUDED.test_type,
                    level = EXCLUDED.level,
                    title_tr = EXCLUDED.title_tr,
                    question_count = EXCLUDED.question_count,
                    time_limit_minutes = EXCLUDED.time_limit_minutes
                """, batch);
    }

    private void seedQuestions() throws Exception {
        List<Object[]> batch = new ArrayList<>();
        for (Map<String, String> r : load("seed/questions.csv")) {
            // Sira SQL'deki ? sirasiyla ayni: kategori kodu WHERE'de, en sonda.
            batch.add(new Object[]{
                    r.get("code"), r.get("question_type"), r.get("skill"), r.get("level"),
                    r.get("prompt_de"), nullIfBlank(r.get("explanation_tr")),
                    Short.parseShort(r.get("difficulty")),
                    r.get("mistake_category_code"),
            });
        }
        int[] written = jdbc.batchUpdate("""
                INSERT INTO question
                    (code, question_type, skill, level, mistake_category_id,
                     prompt_de, explanation_tr, difficulty)
                SELECT ?, ?, ?, ?, mc.id, ?, ?, ?
                FROM mistake_category mc
                WHERE mc.code = ?
                ON CONFLICT (code) DO UPDATE SET
                    question_type = EXCLUDED.question_type,
                    skill = EXCLUDED.skill,
                    level = EXCLUDED.level,
                    mistake_category_id = EXCLUDED.mistake_category_id,
                    prompt_de = EXCLUDED.prompt_de,
                    explanation_tr = EXCLUDED.explanation_tr,
                    difficulty = EXCLUDED.difficulty
                """, batch);

        long eslesmeyen = Arrays.stream(written).filter(n -> n == 0).count();
        if (eslesmeyen > 0) {
            log.warn("{} sorunun hata kategorisi bulunamadi", eslesmeyen);
        }
    }


    private void seedQuestionOptions() throws Exception {
        List<Object[]> batch = new ArrayList<>();
        for (Map<String, String> r : load("seed/question_options.csv")) {
            batch.add(new Object[]{
                    r.get("option_text"),
                    Boolean.parseBoolean(r.get("is_correct")),
                    Short.parseShort(r.get("order_no")),
                    r.get("question_code"),
            });
        }
        jdbc.batchUpdate("""
                INSERT INTO question_option (question_id, option_text, is_correct, order_no)
                SELECT q.id, ?, ?, ?
                FROM question q
                WHERE q.code = ?
                ON CONFLICT (question_id, option_text) DO UPDATE SET
                    is_correct = EXCLUDED.is_correct,
                    order_no = EXCLUDED.order_no
                """, batch);

        retireOptionsNotInCsv(batch);
    }

    /**
     * CSV'de artik bulunmayan sikkilari siler.
     *
     * Upsert tek basina yetmiyor: bir sikkin metni degistiginde yenisi
     * eklenir ama eskisi kalir ve soru bes sikli goruunur. Tohumlamanin
     * gercekten yakinsamasi icin fazlaligi da temizlemek gerekiyor.
     * Gecmis denemelerde secilmis bir sik silinirse o cevabin secimi
     * bosalir (V3), dogru/yanlis bilgisi durur.
     */
    private void retireOptionsNotInCsv(List<Object[]> batch) {
        // Ayirici olarak satir sonu kullaniliyor: ne soru kodunda ne de
        // sik metninde satir sonu bulunur.
        String codes = batch.stream().map(r -> (String) r[3])
                .collect(java.util.stream.Collectors.joining("\n"));
        String texts = batch.stream().map(r -> (String) r[0])
                .collect(java.util.stream.Collectors.joining("\n"));

        int silinen = jdbc.update("""
                DELETE FROM question_option o
                USING question q
                WHERE q.id = o.question_id
                  AND NOT EXISTS (
                      SELECT 1
                      FROM unnest(string_to_array(?, chr(10)),
                                  string_to_array(?, chr(10))) AS t(code, txt)
                      WHERE t.code = q.code AND t.txt = o.option_text)
                """, codes, texts);

        if (silinen > 0) {
            log.info("Guncel olmayan {} secenek silindi", silinen);
        }
    }

    /** Sorunun testteki sirasi CSV'deki satir sirasidir. */
    private void linkTestQuestions() throws Exception {
        List<Object[]> batch = new ArrayList<>();
        short order = 1;
        for (Map<String, String> r : load("seed/questions.csv")) {
            String testCode = nullIfBlank(r.get("test_code"));
            if (testCode == null) {
                continue;
            }
            batch.add(new Object[]{order++, testCode, r.get("code")});
        }
        jdbc.batchUpdate("""
                INSERT INTO test_question (test_id, question_id, order_no)
                SELECT t.id, q.id, ?
                FROM test t, question q
                WHERE t.code = ? AND q.code = ?
                ON CONFLICT (test_id, question_id) DO UPDATE
                SET order_no = EXCLUDED.order_no
                """, batch);
    }

    // ------------------------------------------------------------------

    private int count(String table) {
        Integer n = jdbc.queryForObject("SELECT count(*) FROM " + table, Integer.class);
        return n == null ? 0 : n;
    }

    private List<Map<String, String>> load(String path) throws Exception {
        ClassPathResource resource = new ClassPathResource(path);
        if (!resource.exists()) {
            log.warn("Icerik dosyasi bulunamadi: {}", path);
            return List.of();
        }
        try (InputStream in = resource.getInputStream()) {
            return CsvReader.read(in);
        }
    }

    private static String nullIfBlank(String value) {
        return (value == null || value.isBlank()) ? null : value;
    }
}
