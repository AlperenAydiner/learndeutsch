package com.ichsprechedeutsch.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/** Cevap kontrolu (SPEC 8.3, Ek A). Faz 3a'da kullanilir. */
@ConfigurationProperties(prefix = "answer")
public record AnswerProperties(Mode letterCase, Mode umlautTranscription) {

    public enum Mode { STRICT, ACCEPT_WARN }
}
