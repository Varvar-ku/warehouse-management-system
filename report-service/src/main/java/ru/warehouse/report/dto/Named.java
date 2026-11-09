package ru.warehouse.report.dto;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Строка отчёта, в которой есть люди: сервис подставляет имена по идентификаторам. */
public interface Named<T> {

    List<UUID> peopleIds();

    T withNames(Map<UUID, String> names);
}
