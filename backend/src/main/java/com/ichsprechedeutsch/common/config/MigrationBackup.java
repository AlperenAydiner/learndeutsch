package com.ichsprechedeutsch.common.config;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import javax.sql.DataSource;
import org.flywaydb.core.Flyway;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.flyway.autoconfigure.FlywayMigrationStrategy;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Migrasyondan once otomatik yedek (SPEC 9.2).
 *
 * <p>Bekleyen migrasyon varsa, public semasindaki tum tablolar (Flyway'in
 * kendi tablosu haric) tarihli bir {@code backup_YYYYMMDD_HHMMSS} semasina
 * kopyalanir, sonra migrasyon calisir. Bekleyen migrasyon yoksa hicbir sey
 * yapilmaz. Yedek alinamazsa migrasyon da calismaz.
 */
@Configuration
public class MigrationBackup {

    private static final Logger log = LoggerFactory.getLogger(MigrationBackup.class);
    private static final DateTimeFormatter STAMP = DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss");

    @Bean
    FlywayMigrationStrategy backupBeforeMigrate(DataSource dataSource) {
        return flyway -> {
            int pending = flyway.info().pending().length;
            if (pending > 0) {
                backup(dataSource, flyway);
            }
            flyway.migrate();
        };
    }

    private void backup(DataSource dataSource, Flyway flyway) {
        String schema = "backup_" + LocalDateTime.now().format(STAMP);
        String historyTable = flyway.getConfiguration().getTable();
        try (Connection c = dataSource.getConnection(); Statement st = c.createStatement()) {
            List<String> tables = new ArrayList<>();
            try (ResultSet rs = st.executeQuery(
                    "SELECT tablename FROM pg_tables WHERE schemaname = 'public'")) {
                while (rs.next()) {
                    String t = rs.getString(1);
                    if (!t.equals(historyTable)) {
                        tables.add(t);
                    }
                }
            }
            if (tables.isEmpty()) {
                log.info("Migrasyon oncesi yedek: public semasinda tablo yok, atlandi");
                return;
            }
            st.execute("CREATE SCHEMA " + schema);
            for (String t : tables) {
                st.execute("CREATE TABLE " + schema + ".\"" + t + "\" AS TABLE public.\"" + t + "\"");
            }
            log.info("Migrasyon oncesi yedek alindi: {} tablo -> {}", tables.size(), schema);
        } catch (SQLException e) {
            throw new IllegalStateException("Migrasyon oncesi yedek alinamadi; migrasyon durduruldu", e);
        }
    }
}
