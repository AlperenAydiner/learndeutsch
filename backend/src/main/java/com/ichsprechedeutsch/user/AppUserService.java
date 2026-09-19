package com.ichsprechedeutsch.user;

import com.ichsprechedeutsch.common.error.NotFoundException;
import com.ichsprechedeutsch.common.error.ValidationException;
import com.ichsprechedeutsch.user.api.UpdateMeRequest;
import java.time.DateTimeException;
import java.time.ZoneId;
import java.util.UUID;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Supabase Auth kullanicisi ile uygulamanin {@code app_user} kaydi arasindaki
 * kopru.
 *
 * Kayit akisi sirasinda backend'e hicbir sey gonderilmez: kullanici Supabase'e
 * kaydolur, e-postasini dogrular ve elinde bir JWT ile ilk kez API'ye gelir.
 * Uygulama kaydi iste o anda, tembel olarak olusturulur.
 */
@Service
public class AppUserService {

    private final AppUserRepository repository;

    public AppUserService(AppUserRepository repository) {
        this.repository = repository;
    }

    /**
     * Token'daki kimlige karsilik gelen kullaniciyi dondurur; yoksa olusturur.
     *
     * <p>Kimlik bilgileri HER ZAMAN token'dan okunur, istek govdesinden degil.
     */
    @Transactional
    public AppUser findOrCreate(Jwt jwt) {
        UUID authUserId = UUID.fromString(jwt.getSubject());

        return repository.findByAuthUserId(authUserId)
                .map(existing -> syncEmail(existing, jwt))
                .orElseGet(() -> create(authUserId, jwt));
    }

    /** Profil guncelleme. Yalnizca {@code null} olmayan alanlar degisir. */
    @Transactional
    public AppUser updateProfile(UUID userId, UpdateMeRequest request) {
        AppUser user = repository.findById(userId)
                .orElseThrow(() -> new NotFoundException("Kullanici bulunamadi"));

        if (request.displayName() != null) {
            String name = request.displayName().trim();
            user.setDisplayName(name.isEmpty() ? null : name);
        }
        if (request.timezone() != null) {
            user.setTimezone(validZone(request.timezone()));
        }
        return user;
    }

    private String validZone(String timezone) {
        try {
            return ZoneId.of(timezone.trim()).getId();
        } catch (DateTimeException e) {
            throw new ValidationException("Gecersiz saat dilimi: " + timezone);
        }
    }

    private AppUser create(UUID authUserId, Jwt jwt) {
        AppUser user = new AppUser(authUserId, readEmail(jwt), readDisplayName(jwt));
        try {
            return repository.saveAndFlush(user);
        } catch (DataIntegrityViolationException race) {
            // Ayni kullanicinin iki istegi ayni anda geldiyse biri kaybeder.
            // auth_user_id UNIQUE oldugu icin veri bozulmaz; kazananı okuruz.
            return repository.findByAuthUserId(authUserId).orElseThrow(() -> race);
        }
    }

    /** Kullanici Supabase tarafinda e-postasini degistirmis olabilir. */
    private AppUser syncEmail(AppUser user, Jwt jwt) {
        String email = readEmail(jwt);
        if (email != null && !email.equals(user.getEmail())) {
            user.setEmail(email);
        }
        return user;
    }

    private String readEmail(Jwt jwt) {
        return jwt.getClaimAsString("email");
    }

    /**
     * Kayit formunda girilen ad. Supabase bunu {@code user_metadata} altinda
     * saklar ve JWT'ye {@code user_metadata} claim'i olarak koyar.
     */
    private String readDisplayName(Jwt jwt) {
        Object metadata = jwt.getClaim("user_metadata");
        if (metadata instanceof java.util.Map<?, ?> map) {
            Object name = map.get("display_name");
            if (name instanceof String s && !s.isBlank()) {
                return s.trim();
            }
        }
        return null;
    }
}
