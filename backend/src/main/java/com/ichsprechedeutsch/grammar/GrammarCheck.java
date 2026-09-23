package com.ichsprechedeutsch.grammar;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

/**
 * Tamamlanan gramer konusunun kontrol tekrarlari (SPEC 8.2). Saf.
 *
 * <p>Konu dongusu bitince config'deki araliklarla (Ek A: 7 ve 30 gun)
 * "tekrar bekleyen" listesine duser. Son araliktan sonra kontrol
 * planlanmaz; konu tekrar calisilirsa sayac bastan baslar.
 */
public final class GrammarCheck {

    private GrammarCheck() {
    }

    /**
     * @param checkIndex kacinci kontrol yapildi (0: konu yeni tamamlandi)
     * @return bir sonraki kontrolun tarihi; sira bittiyse bos
     */
    public static Optional<LocalDate> next(LocalDate completedOn, int checkIndex, List<Integer> days) {
        if (completedOn == null || days == null || checkIndex >= days.size()) {
            return Optional.empty();
        }
        return Optional.of(completedOn.plusDays(days.get(checkIndex)));
    }

    public static boolean due(LocalDate nextCheck, LocalDate today) {
        return nextCheck != null && !nextCheck.isAfter(today);
    }
}
