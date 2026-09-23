package com.ichsprechedeutsch.userdata;

import com.ichsprechedeutsch.common.error.ValidationException;
import java.time.OffsetDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ObjectNode;

/**
 * Kullanici verisinin JSON disa/ice aktarimi ve sifirlanmasi (SPEC 9.2).
 *
 * <p>Yalniz kullanici durumu tasinir; icerik (kelime, soru) dosyalardadir
 * ve aktarilmaz. Hesap (app_user) korunur. Disa aktarma veritabani sema
 * surumunu tasir; farkli surumden ice aktarma reddedilir (ileride o surumden
 * donusum yazilir).
 */
@Service
public class UserDataService {

    /**
     * Kullanici tablolari, ice aktarma sirasiyla (yabanci anahtar bagimliligina
     * gore). Silme ters sirayla yapilir.
     */
    // Sira onemli: ice aktarmada once referans verilenler, silmede tersi.
    static final List<String> TABLES = List.of(
            "user_profile", "goal", "study_rhythm", "study_session", "placement_session",
            "level_assessment", "activity_log", "skill_evidence",
            "vocabulary_progress", "learning_answer", "grammar_progress", "error_record");

    private final JdbcTemplate jdbc;
    private final ObjectMapper mapper;

    public UserDataService(JdbcTemplate jdbc, ObjectMapper mapper) {
        this.jdbc = jdbc;
        this.mapper = mapper;
    }

    @Transactional(readOnly = true)
    public Map<String, Object> export(UUID userId) {
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("format", "ichsprechedeutsch-userdata");
        out.put("schemaVersion", schemaVersion());
        out.put("exportedAt", OffsetDateTime.now().toString());
        Map<String, Object> data = new LinkedHashMap<>();
        for (String table : TABLES) {
            String json = jdbc.queryForObject(
                    "SELECT coalesce(json_agg(t), '[]'::json)::text FROM " + table + " t WHERE user_id = ?",
                    String.class, userId);
            data.put(table, mapper.readTree(json));
        }
        out.put("data", data);
        return out;
    }

    /** Mevcut verinin yerine gecer; hepsi ya da hicbiri (tek islem). */
    @Transactional
    public void importData(UUID userId, JsonNode body) {
        if (body == null || !"ichsprechedeutsch-userdata".equals(body.path("format").asString(""))) {
            throw new ValidationException("Bu dosya bir Ich spreche Deutsch dışa aktarımı değil");
        }
        String version = body.path("schemaVersion").asString("");
        if (!version.equals(schemaVersion())) {
            throw new ValidationException("Dışa aktarım farklı bir sürümden (" + version
                    + "); bu sürümde içe aktarılamaz");
        }
        deleteAll(userId);
        JsonNode data = body.path("data");
        for (String table : TABLES) {
            JsonNode rows = data.path(table);
            if (!rows.isArray() || rows.isEmpty()) {
                continue;
            }
            for (JsonNode row : rows) {
                if (row instanceof ObjectNode o) {
                    // Kayitlar her zaman bu kullaniciya yazilir, dosyadaki user_id'ye bakilmaz.
                    o.put("user_id", userId.toString());
                }
            }
            jdbc.update("INSERT INTO " + table + " SELECT * FROM json_populate_recordset(null::" + table
                    + ", ?::json)", mapper.writeValueAsString(rows));
        }
    }

    /** Tum calisma verisini siler; hesap ve giris bilgisi kalir. */
    @Transactional
    public void reset(UUID userId) {
        deleteAll(userId);
    }

    private void deleteAll(UUID userId) {
        for (int i = TABLES.size() - 1; i >= 0; i--) {
            jdbc.update("DELETE FROM " + TABLES.get(i) + " WHERE user_id = ?", userId);
        }
    }

    private String schemaVersion() {
        return jdbc.queryForObject("""
                SELECT version FROM flyway_schema_history
                WHERE success AND version IS NOT NULL
                ORDER BY installed_rank DESC LIMIT 1
                """, String.class);
    }
}
