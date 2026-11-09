package ru.warehouse.auth.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import ru.warehouse.auth.dto.AuthDtos;
import ru.warehouse.auth.dto.AuthDtos.WorkerInfo;
import ru.warehouse.auth.service.UserService;
import java.util.List;
import java.util.UUID;

/** Справочник для интерфейса и других сервисов: кладовщик и администратор видят работников склада. */
@RestController
@RequestMapping("/api/v1/directory")
@RequiredArgsConstructor
@PreAuthorize("hasAnyRole('STOREKEEPER', 'ADMIN')")
public class DirectoryController {
    private final UserService userService;

    @GetMapping("/workers")
    public List<WorkerInfo> workers() {
        return userService.activeWorkers();
    }

    @GetMapping("/users")
    public List<AuthDtos.UserInfo> users(@RequestParam(required = false) List<UUID> ids) {
        return userService.directoryUsers(ids);
    }
}