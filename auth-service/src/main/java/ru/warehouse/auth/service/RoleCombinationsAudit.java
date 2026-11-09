package ru.warehouse.auth.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;
import java.util.List;

/** S6: при запуске предупреждает о пользователях с запрещённым сочетанием ролей, назначенным до введения запрета. */
@Slf4j
@Component
@RequiredArgsConstructor
class RoleCombinationsAudit implements ApplicationRunner {
    private final UserService userService;

    @Override
    public void run(ApplicationArguments args) {
        try {
            List<String> violations = userService.loginsWithForbiddenRoleCombinations();
            if (!violations.isEmpty()) {
                log.warn("Пользователи с запрещённым сочетанием ролей (разделение обязанностей): {}. "
                        + "Измените роли через PATCH /api/v1/users/{{id}}", violations);
            }
        } catch (RuntimeException e) {
            log.warn("Не удалось проверить сочетания ролей пользователей: {}", e.getMessage());
        }
    }
}
