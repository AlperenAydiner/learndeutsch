package com.ichsprechedeutsch.common.security;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Denetleyici metodunda oturum acmis kullaniciyi almak icin kullanilir:
 *
 * <pre>{@code
 * @GetMapping("/me")
 * MeResponse me(@CurrentUser AppUser user) { ... }
 * }</pre>
 *
 * Kullanici kimligi token'dan cozulur. Istekten gelen hicbir deger
 * (parametre, govde, baslik) kimlik belirlemede kullanilmaz.
 */
@Documented
@Target(ElementType.PARAMETER)
@Retention(RetentionPolicy.RUNTIME)
public @interface CurrentUser {
}
