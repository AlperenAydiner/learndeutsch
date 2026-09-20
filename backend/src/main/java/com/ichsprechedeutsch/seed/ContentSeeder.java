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

        log.info("Icerik yuklendi ({} ms): {} kategori, {} birim, {} birim-kategori, "
                        + "{} blok, {} kelime, {} birim-kelime bagi",
                System.currentTimeMillis() - start,
                count("mistake_category"), count("content_unit"),
                count("content_unit_category"), count("content_unit_block"),
                count("word"), count("content_unit_word"));
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
