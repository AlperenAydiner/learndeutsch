package com.ichsprechedeutsch.config;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.List;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.boot.context.properties.source.ConfigurationPropertySources;
import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.core.env.PropertySource;
import org.springframework.core.env.StandardEnvironment;
import org.springframework.core.io.ClassPathResource;

/**
 * Testlerde config'i gercek application.yml'den baglar; Ek A degerleri tek
 * yerde durur (K7), testler kopyasini tutmaz. Veritabani gerekmez.
 */
public final class TestConfig {

    private static final Binder BINDER = binder();

    private TestConfig() {
    }

    public static LevelProperties level() {
        return BINDER.bind("level", LevelProperties.class).get();
    }

    public static PlacementProperties placement() {
        return BINDER.bind("placement", PlacementProperties.class).get();
    }

    public static ActivityProperties activity() {
        return BINDER.bind("activity", ActivityProperties.class).get();
    }

    public static CoachProperties coach() {
        return BINDER.bind("coach", CoachProperties.class).get();
    }

    public static SrsProperties srs() {
        return BINDER.bind("srs", SrsProperties.class).get();
    }

    public static AnswerProperties answer() {
        return BINDER.bind("answer", AnswerProperties.class).get();
    }

    private static Binder binder() {
        try {
            List<PropertySource<?>> sources = new YamlPropertySourceLoader()
                    .load("application", new ClassPathResource("application.yml"));
            StandardEnvironment env = new StandardEnvironment();
            sources.forEach(s -> env.getPropertySources().addLast(s));
            return new Binder(ConfigurationPropertySources.get(env));
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
