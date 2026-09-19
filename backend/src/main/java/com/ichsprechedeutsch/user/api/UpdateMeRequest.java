package com.ichsprechedeutsch.user.api;

import jakarta.validation.constraints.Size;

/**
 * {@code PATCH /api/me} govdesi. Alanlar istege baglidir: {@code null}
 * gelen alan degistirilmez.
 *
 * E-posta burada degistirilemez; o Supabase Auth'un isidir ve dogrulama
 * gerektirir.
 */
public record UpdateMeRequest(

        @Size(max = 80, message = "Ad en fazla 80 karakter olabilir")
        String displayName,

        @Size(max = 64, message = "Saat dilimi en fazla 64 karakter olabilir")
        String timezone) {
}
