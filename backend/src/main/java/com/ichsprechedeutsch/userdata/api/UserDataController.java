package com.ichsprechedeutsch.userdata.api;

import com.ichsprechedeutsch.common.error.ValidationException;
import com.ichsprechedeutsch.common.security.CurrentUser;
import com.ichsprechedeutsch.user.AppUser;
import com.ichsprechedeutsch.userdata.UserDataService;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import tools.jackson.databind.JsonNode;

@RestController
@RequestMapping("/api/me/data")
public class UserDataController {

    /** Sifirlama geri alinamaz; istemci bu kelimeyi gondermeli. */
    static final String RESET_CONFIRMATION = "SIFIRLA";

    public record ResetRequest(String confirm) {
    }

    private final UserDataService service;

    public UserDataController(UserDataService service) {
        this.service = service;
    }

    @GetMapping("/export")
    public Map<String, Object> export(@CurrentUser AppUser user) {
        return service.export(user.getId());
    }

    @PostMapping("/import")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void importData(@CurrentUser AppUser user, @RequestBody JsonNode body) {
        service.importData(user.getId(), body);
    }

    @PostMapping("/reset")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void reset(@CurrentUser AppUser user, @RequestBody ResetRequest request) {
        if (request == null || !RESET_CONFIRMATION.equals(request.confirm())) {
            throw new ValidationException("Sıfırlamak için onay kelimesini yaz: " + RESET_CONFIRMATION);
        }
        service.reset(user.getId());
    }
}
