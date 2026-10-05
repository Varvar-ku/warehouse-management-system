package ru.warehouse.auth.config;

import lombok.RequiredArgsConstructor;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;
import ru.warehouse.auth.service.UserService;

/** При первом запуске (пустая таблица users) создаёт администратора, иначе в систему не войти. */
@Component
@RequiredArgsConstructor
class BootstrapAdminRunner implements ApplicationRunner {
    private final UserService userService;
    private final BootstrapAdminProperties props;

    @Override
    public void run(ApplicationArguments args) {
        userService.bootstrapAdmin(props.login(), props.password(), props.fullName());
    }
}