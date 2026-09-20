package com.ichsprechedeutsch.task;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.ichsprechedeutsch.common.error.ValidationException;
import java.util.LinkedHashMap;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * "Serbest metin yoktur" kuralinin gercekten tutuldugunu dogrular.
 * Kural yalnizca arayuzde durursa kural degildir.
 */
class SelfReportSpecTest {

    @Test
    @DisplayName("Gecerli secenekler kabul edilir")
    void acceptsValidChoices() {
        String json = SelfReportSpec.toJson("HOEREN", Map.of("anlama", "COGU"));
        assertEquals("{\"anlama\":\"COGU\"}", json);
    }

    @Test
    @DisplayName("Sayisal alan tirnaksiz yazilir")
    void numericFieldIsNotQuoted() {
        Map<String, String> answers = new LinkedHashMap<>();
        answers.put("metin_sayisi", "3");
        answers.put("kalip", "KISMEN");

        String json = SelfReportSpec.toJson("SCHREIBEN", answers);

        assertTrue(json.contains("\"metin_sayisi\":3"), json);
        assertTrue(json.contains("\"kalip\":\"KISMEN\""), json);
    }

    @Test
    @DisplayName("Serbest metin reddedilir")
    void rejectsFreeText() {
        ValidationException e = assertThrows(ValidationException.class,
                () -> SelfReportSpec.toJson("HOEREN",
                        Map.of("anlama", "bugun cok iyi anladim sayilir")));

        assertTrue(e.getMessage().contains("gecersiz deger"), e.getMessage());
    }

    @Test
    @DisplayName("Tanimsiz alan sessizce atilmaz, reddedilir")
    void rejectsUnknownField() {
        ValidationException e = assertThrows(ValidationException.class,
                () -> SelfReportSpec.toJson("HOEREN", Map.of("notlarim", "AZ")));

        assertTrue(e.getMessage().contains("notlarim"), e.getMessage());
    }

    @Test
    @DisplayName("Baska gorev tipinin alani kabul edilmez")
    void rejectsFieldFromAnotherTaskType() {
        assertThrows(ValidationException.class,
                () -> SelfReportSpec.toJson("HOEREN", Map.of("kayit", "EVET")));
    }

    @Test
    @DisplayName("Sayisal alan sinir disina cikamaz")
    void rejectsOutOfRangeNumber() {
        assertThrows(ValidationException.class,
                () -> SelfReportSpec.toJson("SCHREIBEN", Map.of("metin_sayisi", "9")));
    }

    @Test
    @DisplayName("Oz bildirimi olmayan gorev tipleri bos JSON uretir")
    void typesWithoutSelfReport() {
        for (String type : new String[]{"KELIME", "GRAMER", "SINAV_PRATIGI"}) {
            assertEquals("{}", SelfReportSpec.toJson(type, Map.of()));
            assertTrue(SelfReportSpec.fieldsFor(type).isEmpty(), type);
        }
    }

    @Test
    @DisplayName("Bos cevap gecerlidir: kullanici hicbir sey secmek zorunda degil")
    void emptyAnswersAreFine() {
        assertEquals("{}", SelfReportSpec.toJson("SPRECHEN", Map.of()));
        assertEquals("{}", SelfReportSpec.toJson("SPRECHEN", null));
    }

    @Test
    @DisplayName("Gecersiz durum reddedilir")
    void rejectsInvalidStatus() {
        SelfReportSpec.validateStatus("DONE");
        SelfReportSpec.validateStatus("PARTIAL");
        SelfReportSpec.validateStatus("MISSED");

        assertThrows(ValidationException.class,
                () -> SelfReportSpec.validateStatus("BELKI"));
    }
}
