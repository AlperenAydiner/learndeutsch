package com.ichsprechedeutsch.task;

import com.ichsprechedeutsch.common.error.ValidationException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Gorev tipine ozel oz bildirim alanlari.
 *
 * Projenin degismez kurallarindan biri: SERBEST METIN YOKTUR. Kullanici
 * ne yazdigini degil, hazir seceneklerden hangisini sectigini bildirir.
 * Bu kural yalnizca arayuzde durursa kural degildir - burada, sunucuda
 * zorlanir: tanimsiz alan veya tanimsiz deger reddedilir.
 *
 * Kelime, gramer ve sinav pratigi bloklarinda oz bildirim yoktur; onlarin
 * performansi sitedeki tekrar oturumundan ve quiz'den otomatik olcelur.
 */
final class SelfReportSpec {

    /** Bir alanin adi ve kabul edilen degerleri. */
    record Field(String name, List<String> allowed, boolean numeric) {

        static Field of(String name, String... allowed) {
            return new Field(name, List.of(allowed), false);
        }

        /** 0..max arasi tam sayi. */
        static Field number(String name, int max) {
            List<String> values = new java.util.ArrayList<>();
            for (int i = 0; i <= max; i++) {
                values.add(String.valueOf(i));
            }
            return new Field(name, List.copyOf(values), true);
        }
    }

    private static final Map<String, List<Field>> BY_TASK_TYPE = Map.of(
            "KELIME", List.of(),
            "GRAMER", List.of(),
            "SINAV_PRATIGI", List.of(),

            "HOEREN", List.of(
                    Field.of("anlama", "AZ", "YARISI", "COGU")),

            "LESEN", List.of(
                    Field.of("anlama", "AZ", "YARISI", "COGU")),

            "SCHREIBEN", List.of(
                    Field.number("metin_sayisi", 5),
                    Field.of("kalip", "HIC", "KISMEN", "RAHAT")),

            "SPRECHEN", List.of(
                    Field.of("kayit", "EVET", "HAYIR"),
                    Field.of("nasil", "COK_TAKILDIM", "IDARE_EDER", "AKICI")));

    private static final Set<String> STATUSES = Set.of("DONE", "PARTIAL", "MISSED");

    private SelfReportSpec() {
    }

    static List<Field> fieldsFor(String taskType) {
        return BY_TASK_TYPE.getOrDefault(taskType, List.of());
    }

    static void validateStatus(String status) {
        if (!STATUSES.contains(status)) {
            throw new ValidationException(
                    "Gecersiz durum. Yaptim, yarim yaptim veya yapamadim olmali.");
        }
    }

    /**
     * Gelen cevaplari dogrular ve veritabanina yazilacak JSON'u uretir.
     *
     * Tanimsiz alan sessizce atilmaz, hata verilir: sessizce atmak, veriyi
     * kaybettigimizi kimseye soylemeden kaybetmek olurdu.
     */
    static String toJson(String taskType, Map<String, String> answers) {
        List<Field> fields = fieldsFor(taskType);

        if (answers == null || answers.isEmpty()) {
            return "{}";
        }

        Map<String, String> known = new LinkedHashMap<>();
        for (Field field : fields) {
            known.put(field.name(), null);
        }

        for (Map.Entry<String, String> entry : answers.entrySet()) {
            if (!known.containsKey(entry.getKey())) {
                throw new ValidationException(
                        "Bu gorev tipinde '" + entry.getKey() + "' diye bir alan yok");
            }
        }

        StringBuilder json = new StringBuilder("{");
        for (Field field : fields) {
            String value = answers.get(field.name());
            if (value == null || value.isBlank()) {
                continue;
            }
            if (!field.allowed().contains(value)) {
                throw new ValidationException(
                        "'" + field.name() + "' icin gecersiz deger: " + value);
            }
            if (json.length() > 1) {
                json.append(',');
            }
            json.append('"').append(field.name()).append("\":");
            if (field.numeric()) {
                json.append(value);
            } else {
                json.append('"').append(value).append('"');
            }
        }
        return json.append('}').toString();
    }
}
