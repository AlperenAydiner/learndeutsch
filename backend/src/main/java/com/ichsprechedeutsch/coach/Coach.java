package com.ichsprechedeutsch.coach;

import java.util.List;

/**
 * Koc arayuzu (SPEC 5.6). Simdiki gerceklestirim kural tabanlidir
 * ({@link RuleBasedCoach}); ileride ayni girdi ve ciktiyla calisan baska
 * bir gerceklestirim (or. AI destekli) takilabilir. API anahtari gerektiren
 * bir gerceklestirim on yuze konmaz.
 */
public interface Coach {

    /** Oncelik sirasina gore en fazla N oneri (N ve kategori siniri config'de). */
    List<Recommendation> advise(CoachInput input);
}
