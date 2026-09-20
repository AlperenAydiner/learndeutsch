package com.ichsprechedeutsch.vocabulary.api;

import java.util.List;
import java.util.UUID;

/**
 * Gunluk tekrar oturumu.
 *
 * Kart hem soruyu hem cevabi tasir: cevap cevrilince gosterilir, ayrica
 * istek atilmaz. Dogru cevabi istemciye gondermek burada sorun degil,
 * cunku bu bir sinav degil kendi kendini yoklama; puan kullanicinin
 * bildirdigi zorluktan gelir.
 */
public record SessionResponse(
        List<Card> cards,
        int newWords,
        int dueReviews,
        int blockMinutes,
        String quotaReasonTr,
        int learnedTotal) {

    public record Card(
            UUID userWordId,
            /** DE_TR | TR_DE | ARTICLE */
            String direction,
            String lemma,
            /** DER | DIE | DAS, isim degilse null. */
            String article,
            String pluralForm,
            String meaningTr,
            String exampleDe,
            String exampleTr,
            boolean isNew) {
    }
}
