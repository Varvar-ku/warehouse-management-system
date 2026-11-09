package ru.warehouse.auth.service;

import java.util.Arrays;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Разделение обязанностей (S6): сочетания ролей, которые нельзя назначать одному пользователю.
 * Иначе один человек создаёт заявку, проверяет, резервирует и выдаёт её сам, либо создаёт и одобряет списание.
 */
final class RoleCombinations {
    private RoleCombinations() {}

    /** @param combinations записи вида «USER+STOREKEEPER»; null или пусто: ограничений нет. */
    static String violation(Collection<String> roles, List<String> combinations) {
        if (combinations == null) return null;
        Set<String> assigned = new HashSet<>();
        roles.forEach(r -> assigned.add(r.trim().toUpperCase(Locale.ROOT)));
        for (String combination : combinations) {
            if (combination == null || combination.isBlank()) continue;
            Set<String> forbidden = new HashSet<>();
            Arrays.stream(combination.split("\\+")).map(String::trim).filter(s -> !s.isEmpty())
                    .forEach(s -> forbidden.add(s.toUpperCase(Locale.ROOT)));
            if (forbidden.size() >= 2 && assigned.containsAll(forbidden)) {
                return "Роли " + String.join(" и ", forbidden.stream().sorted().toList())
                        + " нельзя назначать одному пользователю (разделение обязанностей)";
            }
        }
        return null;
    }
}
