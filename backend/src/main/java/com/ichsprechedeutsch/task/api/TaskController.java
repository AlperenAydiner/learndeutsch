package com.ichsprechedeutsch.task.api;

import com.ichsprechedeutsch.common.security.CurrentUser;
import com.ichsprechedeutsch.task.TaskService;
import com.ichsprechedeutsch.user.AppUser;
import jakarta.validation.Valid;
import java.time.LocalDate;
import java.util.UUID;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api")
public class TaskController {

    private final TaskService service;

    public TaskController(TaskService service) {
        this.service = service;
    }

    /** Bugunun ekrani. Tarih verilirse o gun gosterilir. */
    @GetMapping("/today")
    public TodayResponse today(
            @CurrentUser AppUser user,
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {

        return date == null ? service.today(user.getId()) : service.dayFor(user.getId(), date);
    }

    /**
     * Gorevi isaretler ve gunun guncel halini doner.
     *
     * Tek istek: istemci isaretledikten sonra ilerlemeyi ogrenmek icin
     * ikinci bir cagri yapmak zorunda kalmaz.
     */
    @PostMapping("/tasks/{taskId}/complete")
    public TodayResponse complete(@CurrentUser AppUser user,
                                  @PathVariable UUID taskId,
                                  @Valid @RequestBody CompleteTaskRequest request) {
        return service.complete(user.getId(), taskId, request);
    }
}
