package com.ichsprechedeutsch.placement.api;

import com.ichsprechedeutsch.common.security.CurrentUser;
import com.ichsprechedeutsch.placement.PlacementService;
import com.ichsprechedeutsch.user.AppUser;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/placement")
public class PlacementController {

    private final PlacementService service;

    public PlacementController(PlacementService service) {
        this.service = service;
    }

    /** Sorular ve secenekler. Dogru cevap bilgisi gonderilmez. */
    @GetMapping("/test")
    public PlacementTestResponse test(@CurrentUser AppUser user) {
        return service.loadTest();
    }

    @PostMapping("/submit")
    public PlacementResultResponse submit(@CurrentUser AppUser user,
                                          @Valid @RequestBody PlacementSubmitRequest request) {
        return service.submit(user.getId(), request);
    }
}
