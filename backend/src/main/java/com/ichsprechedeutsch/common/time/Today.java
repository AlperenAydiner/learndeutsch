package com.ichsprechedeutsch.common.time;

import com.ichsprechedeutsch.common.error.ValidationException;
import com.ichsprechedeutsch.user.AppUser;
import java.time.Clock;
import java.time.DateTimeException;
import java.time.LocalDate;
import java.time.ZoneId;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestAttributes;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

/**
 * "Bugun" tek yerden gelir. Alan mantigi sistem saatini okumaz; tarihi
 * parametre alir (SPEC 9.1). Servisler bugunu buradan alip iletir; demo
 * modu (Faz 4) burayi degistirerek zamani ileri sarar.
 */
@Component
public class Today {

    /** Simule tarih basligi (SPEC 12.2). */
    public static final String DEMO_HEADER = "X-Demo-Date";

    private final Clock clock;

    public Today() {
        this(Clock.systemUTC());
    }

    Today(Clock clock) {
        this.clock = clock;
    }

    /**
     * Kullanicinin kendi saat dilimindeki takvim gunu.
     *
     * <p>Demo hesabinda (SPEC 12.2) istek {@code X-Demo-Date} basligi
     * tasiyorsa zaman ileri sarilir. Baglik baska hesaplarda YOK SAYILIR:
     * gercek kullanicinin verisi simule tarihle yazilamaz.
     */
    public LocalDate of(AppUser user) {
        LocalDate gercek = LocalDate.now(clock.withZone(ZoneId.of(user.getTimezone())));
        if (!user.isDemo()) {
            return gercek;
        }
        String baslik = demoHeader();
        if (baslik == null || baslik.isBlank()) {
            return gercek;
        }
        try {
            return LocalDate.parse(baslik.trim());
        } catch (DateTimeException e) {
            throw new ValidationException("X-Demo-Date biçimi yyyy-MM-dd olmalı");
        }
    }

    /** Demo tarihi yalniz bir HTTP istegi baglaminda okunur. */
    private static String demoHeader() {
        RequestAttributes attrs = RequestContextHolder.getRequestAttributes();
        if (attrs instanceof ServletRequestAttributes servlet) {
            return servlet.getRequest().getHeader(DEMO_HEADER);
        }
        return null;
    }
}
