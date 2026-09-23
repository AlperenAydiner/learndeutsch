package com.ichsprechedeutsch.content;

import com.ichsprechedeutsch.common.model.Level;
import java.util.List;

/**
 * Resmi sinav saglayicisi ve kendi ornek sinav sayfasi (SPEC 7).
 * Telifli sinav materyali kopyalanmaz (10.2): yalniz kuruma yonlendirilir.
 * Link dogrulugu kullanici tarafindan isaretlenir ({@code verified}).
 */
public record ExamLink(
        String id,
        String institution,
        String nameTr,
        List<Level> levels,
        String url,
        String noteTr,
        boolean free,
        boolean verified) {
}
