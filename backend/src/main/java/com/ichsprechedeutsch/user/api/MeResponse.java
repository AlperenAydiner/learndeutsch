package com.ichsprechedeutsch.user.api;

import com.ichsprechedeutsch.user.AppUser;
import java.time.OffsetDateTime;
import java.util.UUID;

/** {@code GET /api/me} yaniti. */
public record MeResponse(
        UUID id,
        String email,
        String displayName,
        String timezone,
        /** SPEC 12.2: demo hesabiysa arayuz gorunur bir DEMO etiketi gosterir. */
        boolean demo,
        OffsetDateTime createdAt) {

    public static MeResponse from(AppUser user) {
        return new MeResponse(
                user.getId(),
                user.getEmail(),
                user.getDisplayName(),
                user.getTimezone(),
                user.isDemo(),
                user.getCreatedAt());
    }
}
