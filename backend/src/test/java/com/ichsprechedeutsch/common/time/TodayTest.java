package com.ichsprechedeutsch.common.time;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.ichsprechedeutsch.common.error.ValidationException;
import com.ichsprechedeutsch.user.AppUser;
import java.lang.reflect.Field;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

/**
 * SPEC 12.2 — demo modunda zaman ileri sarilabilir; gercek hesapta
 * simule tarih YOK SAYILIR.
 */
class TodayTest {

    /** 2026-05-10T21:30Z: Istanbul'da (UTC+3) 11 Mayis. */
    private static final Clock SAAT = Clock.fixed(Instant.parse("2026-05-10T21:30:00Z"), ZoneOffset.UTC);

    private final Today today = new Today(SAAT);

    private static AppUser kullanici(boolean demo) {
        AppUser u = yeni();
        set(u, "timezone", "Europe/Istanbul");
        set(u, "demo", demo);
        return u;
    }

    private static AppUser yeni() {
        try {
            var ctor = AppUser.class.getDeclaredConstructor();
            ctor.setAccessible(true);
            return ctor.newInstance();
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException(e);
        }
    }

    private static void set(AppUser u, String alan, Object deger) {
        try {
            Field f = AppUser.class.getDeclaredField(alan);
            f.setAccessible(true);
            f.set(u, deger);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException(e);
        }
    }

    private static void istekBasligi(String tarih) {
        MockHttpServletRequest istek = new MockHttpServletRequest();
        if (tarih != null) {
            istek.addHeader(Today.DEMO_HEADER, tarih);
        }
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(istek));
    }

    @AfterEach
    void temizle() {
        RequestContextHolder.resetRequestAttributes();
    }

    @Test
    void bugunKullanicininSaatDilimindeHesaplanir() {
        assertEquals(LocalDate.of(2026, 5, 11), today.of(kullanici(false)),
                "UTC'de 10 Mayis 21:30, Istanbul'da 11 Mayis");
    }

    @Test
    void gercekHesaptaSimuleTarihYokSayilir() {
        istekBasligi("2030-01-01");
        assertEquals(LocalDate.of(2026, 5, 11), today.of(kullanici(false)),
                "gercek kullanicinin verisi simule tarihle yazilamaz");
    }

    @Test
    void demoHesabindaZamanIleriSarilir() {
        istekBasligi("2030-01-01");
        assertEquals(LocalDate.of(2030, 1, 1), today.of(kullanici(true)));
    }

    @Test
    void demoHesabindaBaslikYoksaGercekTarih() {
        istekBasligi(null);
        assertEquals(LocalDate.of(2026, 5, 11), today.of(kullanici(true)));
    }

    @Test
    void bozukTarihReddedilir() {
        istekBasligi("dun");
        assertThrows(ValidationException.class, () -> today.of(kullanici(true)));
    }

    @Test
    void istekBaglamiYokkenGercekTarih() {
        assertEquals(LocalDate.of(2026, 5, 11), today.of(kullanici(true)),
                "zamanlanmis islerde HTTP baglami olmaz");
    }
}
