package com.ichsprechedeutsch.user.api;

import com.ichsprechedeutsch.common.security.CurrentUser;
import com.ichsprechedeutsch.user.AppUser;
import com.ichsprechedeutsch.user.AppUserService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/me")
public class UserController {

    private final AppUserService userService;

    public UserController(AppUserService userService) {
        this.userService = userService;
    }

    /**
     * Oturum acmis kullanicinin profili.
     *
     * Frontend bunu girisin gercekten gecerli oldugunu dogrulamak icin de
     * kullanir: token bozuksa veya suresi dolmussa 401 doner.
     */
    @GetMapping
    public MeResponse me(@CurrentUser AppUser user) {
        return MeResponse.from(user);
    }

    @PatchMapping
    public MeResponse update(@CurrentUser AppUser user,
                             @Valid @RequestBody UpdateMeRequest request) {
        return MeResponse.from(userService.updateProfile(user.getId(), request));
    }
}
