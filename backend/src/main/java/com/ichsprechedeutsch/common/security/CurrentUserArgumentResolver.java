package com.ichsprechedeutsch.common.security;

import com.ichsprechedeutsch.user.AppUser;
import com.ichsprechedeutsch.user.AppUserService;
import org.springframework.core.MethodParameter;
import org.springframework.security.authentication.AuthenticationCredentialsNotFoundException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Component;
import org.springframework.web.bind.support.WebDataBinderFactory;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.method.support.ModelAndViewContainer;

/**
 * {@link CurrentUser} ile isaretli parametreyi doldurur.
 *
 * Token'daki {@code sub} degerinden uygulama kullanicisini bulur; kullanici
 * ilk kez geliyorsa kaydini olusturur. Buraya gelindiginde Spring Security
 * token'i zaten dogrulamistir (imza, sure, JWKS); gecersiz token istek
 * denetleyiciye hic ulasmaz.
 */
@Component
public class CurrentUserArgumentResolver implements HandlerMethodArgumentResolver {

    private final AppUserService userService;

    public CurrentUserArgumentResolver(AppUserService userService) {
        this.userService = userService;
    }

    @Override
    public boolean supportsParameter(MethodParameter parameter) {
        return parameter.hasParameterAnnotation(CurrentUser.class)
                && AppUser.class.isAssignableFrom(parameter.getParameterType());
    }

    @Override
    public Object resolveArgument(MethodParameter parameter,
                                  ModelAndViewContainer mavContainer,
                                  NativeWebRequest webRequest,
                                  WebDataBinderFactory binderFactory) {

        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !(auth.getPrincipal() instanceof Jwt jwt)) {
            throw new AuthenticationCredentialsNotFoundException(
                    "Istekte gecerli bir token yok");
        }
        return userService.findOrCreate(jwt);
    }
}
