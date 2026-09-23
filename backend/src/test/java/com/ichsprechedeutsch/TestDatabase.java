package com.ichsprechedeutsch;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.test.context.DynamicPropertyRegistry;

/**
 * Entegrasyon testlerinin hangi veritabanina baglanacagi (K-014, K-030).
 *
 * <ul>
 *   <li>{@code IT_DB=local} -> {@code .env.local}: Docker'daki yerel Postgres.</li>
 *   <li>{@code IT_DB=dev}   -> {@code .env.dev}: Supabase gelistirme projesi.</li>
 * </ul>
 *
 * Iki durumda da CANLI veritabanina baglanmayi reddeder: testler kendi
 * kullanicilarini olusturup siler ama yanlis hedefte calismamalidir.
 */
final class TestDatabase {

    /** Testlerin etkinlesmesi icin IT_DB'nin alabilecegi degerler. */
    static final String ENABLED = "dev|local";

    private static final String DEV_PROJECT = "lokxrmomepycydvxcsfq";

    private TestDatabase() {
    }

    static void configure(DynamicPropertyRegistry r) {
        String hedef = System.getenv().getOrDefault("IT_DB", "dev");
        Map<String, String> env = read("local".equals(hedef) ? ".env.local" : ".env.dev");

        if ("local".equals(hedef)) {
            String url = env.getOrDefault("DB_URL", "");
            if (!url.contains("//localhost:") && !url.contains("//127.0.0.1:")) {
                throw new IllegalStateException("IT_DB=local ama DB_URL yerel degil: " + url);
            }
        } else if (!env.getOrDefault("DB_USERNAME", "").contains(DEV_PROJECT)) {
            throw new IllegalStateException("Entegrasyon testi yalniz gelistirme veritabaninda calisir");
        }

        r.add("spring.datasource.url", () -> env.get("DB_URL"));
        r.add("spring.datasource.username", () -> env.get("DB_USERNAME"));
        r.add("spring.datasource.password", () -> env.get("DB_PASSWORD"));
        r.add("spring.security.oauth2.resourceserver.jwt.jwk-set-uri", () -> env.get("SUPABASE_JWKS_URI"));
    }

    private static Map<String, String> read(String file) {
        Map<String, String> env = new LinkedHashMap<>();
        try {
            for (String line : Files.readAllLines(Path.of(file))) {
                int i = line.indexOf('=');
                if (i > 0 && !line.startsWith("#")) {
                    env.put(line.substring(0, i).trim(), line.substring(i + 1).trim());
                }
            }
        } catch (IOException e) {
            throw new UncheckedIOException(file + " okunamadi", e);
        }
        return env;
    }
}
