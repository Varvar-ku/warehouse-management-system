package ru.warehouse.report.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import ru.warehouse.common.exception.ApiException;
import ru.warehouse.report.client.AuthDirectoryClient;
import ru.warehouse.report.dto.Named;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Подставляет имена людей в строки отчёта одним запросом к auth-service. При недоступности отдаёт строки без имён. */
@Component
public class NameResolver {

    private static final Logger log = LoggerFactory.getLogger(NameResolver.class);

    private final AuthDirectoryClient authDirectory;

    public NameResolver(AuthDirectoryClient authDirectory) {
        this.authDirectory = authDirectory;
    }

    public <T extends Named<T>> List<T> apply(List<T> rows) {
        List<UUID> ids = rows.stream().flatMap(r -> r.peopleIds().stream()).distinct().toList();
        if (ids.isEmpty()) {
            return rows;
        }
        Map<UUID, String> names = new HashMap<>();
        try {
            authDirectory.users(ids).forEach((id, u) ->
                    names.put(id, u.fullName() != null && !u.fullName().isBlank() ? u.fullName() : u.login()));
        } catch (ApiException e) {
            log.warn("User directory unavailable, report returned without names: {}", e.getMessage());
            return rows;
        }
        return rows.stream().map(r -> r.withNames(names)).toList();
    }
}
