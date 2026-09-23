package com.ichsprechedeutsch.activity.api;

import com.ichsprechedeutsch.activity.ActivityService;
import com.ichsprechedeutsch.activity.ActivityStore.Activity;
import com.ichsprechedeutsch.common.security.CurrentUser;
import com.ichsprechedeutsch.common.time.Today;
import com.ichsprechedeutsch.config.ActivityProperties;
import com.ichsprechedeutsch.content.ContentCatalog;
import com.ichsprechedeutsch.user.AppUser;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/activities")
public class ActivityController {

    public record TopicOption(String id, String title, String level) {
    }

    public record Options(LocalDate today, List<TopicOption> grammarTopics, int maxNoteLength) {
    }

    private final ActivityService service;
    private final Today today;
    private final ContentCatalog catalog;
    private final ActivityProperties config;

    public ActivityController(ActivityService service, Today today, ContentCatalog catalog,
                              ActivityProperties config) {
        this.service = service;
        this.today = today;
        this.catalog = catalog;
        this.config = config;
    }

    /** Varsayilan: bugun. Formda o gun otomatik kaydedilenler de gorunur (6.2). */
    @GetMapping
    public List<Activity> list(@CurrentUser AppUser user,
                               @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
                               @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        LocalDate t = today.of(user);
        return service.between(user.getId(), from == null ? t : from, to == null ? t : to);
    }

    /** Formun hazir secenekleri: gramer agaci ve bugunun tarihi. */
    @GetMapping("/options")
    public Options options(@CurrentUser AppUser user) {
        List<TopicOption> topics = catalog.topics().values().stream()
                .filter(t -> "GRAMMAR".equals(t.kind()))
                .map(t -> new TopicOption(t.id(), t.titleTr(), t.level() == null ? null : t.level().name()))
                .toList();
        return new Options(today.of(user), topics, config.maxNoteLength());
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public Activity create(@CurrentUser AppUser user, @RequestBody ActivityRequest request) {
        return service.createExternal(user.getId(), request, today.of(user));
    }

    @PutMapping("/{id}")
    public Activity update(@CurrentUser AppUser user, @PathVariable UUID id,
                           @RequestBody ActivityRequest request) {
        return service.update(user.getId(), id, request, today.of(user));
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@CurrentUser AppUser user, @PathVariable UUID id) {
        service.delete(user.getId(), id);
    }
}
