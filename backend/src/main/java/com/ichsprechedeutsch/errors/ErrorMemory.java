package com.ichsprechedeutsch.errors;

import com.ichsprechedeutsch.config.CoachProperties;
import java.time.LocalDate;

/**
 * Hata hafizasi (SPEC 8.4). Saf: bugun parametre, veritabani yok.
 *
 * <p>Bir etiket (gramer konusu, yazim hatasi turu) hatali cevapla
 * <b>aktif</b> olur. Sonraki denemelerde dogru gelmeye basladiginda
 * <b>duzeliyor</b>, art arda yeterince dogru gelince <b>cozuldu</b>
 * olur. Cozulen etiket yeniden hatali olursa tekrar aktife doner.
 *
 * <p>Esikler config'den gelir (Ek A: {@code coach.recurring-error-sessions},
 * {@code coach.error-resolved-streak}); koda sabit sayi yazilmaz (K7).
 */
public final class ErrorMemory {

    public enum Status { ACTIVE, IMPROVING, RESOLVED }

    /**
     * @param distinctSessions etiketin hatali geldigi FARKLI oturum sayisi
     * @param correctStreak    art arda kac dogru geldi
     */
    public record State(String tag, Status status, int occurrences, int distinctSessions,
                        int correctStreak, LocalDate firstSeen, LocalDate lastSeen, LocalDate resolvedOn) {

        public static State first(String tag, LocalDate today) {
            return new State(tag, Status.ACTIVE, 0, 0, 0, today, today, null);
        }
    }

    private final CoachProperties config;

    public ErrorMemory(CoachProperties config) {
        this.config = config;
    }

    /**
     * Bir cevaptan sonra etiketin yeni durumu.
     *
     * @param newSession bu oturumda bu etiket ilk kez mi hatali (farkli oturum sayaci icin)
     */
    public State onAnswer(State current, boolean correct, boolean newSession, LocalDate today) {
        if (!correct) {
            return new State(current.tag(), Status.ACTIVE, current.occurrences() + 1,
                    current.distinctSessions() + (newSession ? 1 : 0), 0,
                    current.firstSeen(), today, null);
        }

        int streak = current.correctStreak() + 1;
        if (current.status() == Status.RESOLVED) {
            // Zaten cozulmus: dogru cevap durumu degistirmez.
            return new State(current.tag(), Status.RESOLVED, current.occurrences(),
                    current.distinctSessions(), streak, current.firstSeen(), today, current.resolvedOn());
        }
        if (streak >= config.errorResolvedStreak()) {
            return new State(current.tag(), Status.RESOLVED, current.occurrences(),
                    current.distinctSessions(), streak, current.firstSeen(), today, today);
        }
        return new State(current.tag(), Status.IMPROVING, current.occurrences(),
                current.distinctSessions(), streak, current.firstSeen(), today, null);
    }

    /** Ayni etiket yeterince farkli oturumda hatali mi (SPEC 5.3 kural 3)? */
    public boolean recurring(State state) {
        return state.status() != Status.RESOLVED
                && state.distinctSessions() >= config.recurringErrorSessions();
    }
}
