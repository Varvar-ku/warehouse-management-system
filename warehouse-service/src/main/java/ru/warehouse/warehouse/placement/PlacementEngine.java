// PlacementEngine.java
package ru.warehouse.warehouse.placement;

import ru.warehouse.warehouse.entity.SectionStatus;
import ru.warehouse.warehouse.placement.PlacementModels.*;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.*;

/**
 * Автоматический выбор секции (разд. 9 ТЗ). Чистая логика без БД.
 * Порядок для каждой позиции:
 *   1. Жёсткие ограничения секций (статус, тип, материал, размеры, вместимость) → список допустимых.
 *   2. Правила по убыванию приоритета: берётся первая группа равного приоритета, у которой есть
 *      целевая секция, прошедшая жёсткие ограничения.
 *   3. Правил нет → лучшая допустимая секция (приоритет, консолидация, свободное место, код).
 *   4. Резервные правила.
 *   5. Иначе зона «Нераспределено» и задача кладовщику.
 * Занятость растёт по мере назначения позиций: вторая позиция видит место, занятое первой.
 * Расчёт ничего не сохраняет и остаток не меняет.
 */
public class PlacementEngine {
    private static final BigDecimal UNLIMITED = new BigDecimal("1E+18");
    private static final int MAX_CANDIDATES = 10;

    /** Срез секции для расчёта. Размеры в мм, коды в верхнем регистре. */
    public record SectionView(UUID id, String code, int priority, boolean unassignedZone, SectionStatus status,
                              Set<String> allowedProductTypes, Set<String> allowedMaterials,
                              BigDecimal lengthMin, BigDecimal lengthMax,
                              BigDecimal widthMin, BigDecimal widthMax,
                              BigDecimal heightMin, BigDecimal heightMax,
                              BigDecimal maxCapacity, String capacityUnitCode) {}

    /** Срез правила для расчёта. */
    public record RuleView(UUID id, int ruleVersion, String name, int priority, boolean fallback,
                           RuleConditions conditions, UUID targetSectionId) {}

    private final UnitConverter units;
    private final BigDecimal tolerance;

    public PlacementEngine(UnitConverter units, BigDecimal tolerance) {
        this.units = units;
        this.tolerance = tolerance;
    }

    public List<PlacementResult> calculate(List<SectionView> sections, List<RuleView> rules,
                                           List<PlacementItem> items, List<SectionOccupancy> occupancy,
                                           OffsetDateTime now) {
        Ctx ctx = new Ctx(sections, occupancy, now);
        List<RuleView> ordered = new ArrayList<>(rules);
        ordered.sort((a, b) -> {
            int c = Integer.compare(b.priority(), a.priority()); // выше приоритет раньше
            return c != 0 ? c : a.name().compareTo(b.name());
        });
        List<PlacementResult> results = new ArrayList<>();
        for (PlacementItem item : items) {
            results.add(place(item, ordered, ctx));
        }
        return results;
    }

    private PlacementResult place(PlacementItem item, List<RuleView> rules, Ctx ctx) {
        List<String> notes = new ArrayList<>();
        try {
            // 1. Допустимые секции по жёстким ограничениям
            List<SectionView> candidates = new ArrayList<>();
            for (SectionView s : ctx.sections) {
                if (!s.unassignedZone() && violation(s, item, ctx) == null) candidates.add(s);
            }
            candidates.sort((a, b) -> compare(a, b, item, ctx));

            // 2. Обычные правила, сгруппированные по приоритету (от высшего)
            TreeMap<Integer, List<RuleView>> groups = new TreeMap<>(Comparator.reverseOrder());
            for (RuleView r : rules) {
                if (!r.fallback() && r.conditions().matches(item, units, tolerance)) {
                    groups.computeIfAbsent(r.priority(), k -> new ArrayList<>()).add(r);
                }
            }
            for (List<RuleView> group : groups.values()) {
                Map<UUID, RuleView> targets = new LinkedHashMap<>();
                for (RuleView r : group) {
                    SectionView t = ctx.byId.get(r.targetSectionId());
                    String reason = targetViolation(t, item, ctx);
                    if (reason == null) {
                        targets.putIfAbsent(t.id(), r);
                    } else {
                        notes.add("Правило «" + r.name() + "»: секция " + (t == null ? "?" : t.code())
                                + " не подходит (" + reason + ")");
                    }
                }
                if (!targets.isEmpty()) {
                    RuleView best = targets.values().stream()
                            .min((x, y) -> compare(ctx.byId.get(x.targetSectionId()),
                                    ctx.byId.get(y.targetSectionId()), item, ctx))
                            .get();
                    if (targets.size() > 1) {
                        notes.add("Несколько правил одного приоритета указывают разные секции: выбрана лучшая "
                                + "по приоритету секции и свободному месту");
                    }
                    return assigned(item, ctx.byId.get(best.targetSectionId()), PlacementStrategy.RULE,
                            best, notes, candidates, ctx);
                }
            }

            // 3. Правил нет (или их секции не подошли): лучшая допустимая секция
            if (!candidates.isEmpty()) {
                return assigned(item, candidates.get(0), PlacementStrategy.SECTION_PRIORITY,
                        null, notes, candidates, ctx);
            }

            // 4. Резервные правила
            for (RuleView r : rules) {
                if (!r.fallback() || !r.conditions().matches(item, units, tolerance)) continue;
                SectionView t = ctx.byId.get(r.targetSectionId());
                String reason = targetViolation(t, item, ctx);
                if (reason == null) {
                    return assigned(item, t, PlacementStrategy.FALLBACK_RULE, r, notes, candidates, ctx);
                }
                notes.add("Резервное правило «" + r.name() + "»: секция " + (t == null ? "?" : t.code())
                        + " не подходит (" + reason + ")");
            }

            // 5. «Нераспределено»
            notes.add("Подходящая секция не найдена: позиция направлена в зону «Нераспределено»");
            return unassigned(item, notes, ctx);
        } catch (UnknownUnitException e) {
            notes.add(e.getMessage() + ". Позиция направлена в «Нераспределено»");
            return unassigned(item, notes, ctx);
        }
    }

    // ================= жёсткие ограничения =================

    private String targetViolation(SectionView target, PlacementItem item, Ctx ctx) {
        if (target == null) return "секция не найдена или в архиве";
        if (target.unassignedZone()) return "зона «Нераспределено» не может быть целью правила";
        return violation(target, item, ctx);
    }

    /** Возвращает причину, по которой секция не подходит, или null, если подходит. */
    private String violation(SectionView s, PlacementItem item, Ctx ctx) {
        if (s.status() != SectionStatus.ACTIVE) return "статус " + s.status();
        String type = item.productTypeCode().trim().toUpperCase(Locale.ROOT);
        if (!s.allowedProductTypes().isEmpty() && !s.allowedProductTypes().contains(type)) {
            return "тип детали " + type + " не разрешён";
        }
        if (!s.allowedMaterials().isEmpty()) {
            if (item.material() == null || item.material().isBlank()) return "материал детали не указан";
            if (!s.allowedMaterials().contains(item.material().trim().toUpperCase(Locale.ROOT))) {
                return "материал не разрешён";
            }
        }
        String d = checkRange("LENGTH", s.lengthMin(), s.lengthMax(), item);
        if (d == null) d = checkRange("WIDTH", s.widthMin(), s.widthMax(), item);
        if (d == null) d = checkRange("HEIGHT", s.heightMin(), s.heightMax(), item);
        if (d != null) return d;
        if (s.maxCapacity() != null) {
            if (!s.capacityUnitCode().equalsIgnoreCase(item.unitCode().trim())) {
                return "единица вместимости " + s.capacityUnitCode() + " не совпадает с " + item.unitCode();
            }
            if (free(s, ctx).compareTo(item.quantity()) < 0) return "недостаточно свободной вместимости";
        }
        return null;
    }

    private String checkRange(String name, BigDecimal min, BigDecimal max, PlacementItem item) {
        if (min == null && max == null) return null;
        NumericValue actual = item.attribute(name);
        if (actual == null) return "нет значения " + name + " для проверки диапазона секции";
        BigDecimal value = units.toBase(actual.value(), actual.unitCode());
        if (min != null && value.add(tolerance).compareTo(min) < 0) return name + " меньше минимума секции";
        if (max != null && value.subtract(tolerance).compareTo(max) > 0) return name + " больше максимума секции";
        return null;
    }

    // ================= выбор лучшей секции =================

    /** Порядок «лучшая раньше»: приоритет секции → уже хранит эту номенклатуру → больше места → код. */
    private int compare(SectionView a, SectionView b, PlacementItem item, Ctx ctx) {
        int c = Integer.compare(b.priority(), a.priority());
        if (c != 0) return c;
        c = Boolean.compare(ctx.holds(b.id(), item.productId()), ctx.holds(a.id(), item.productId()));
        if (c != 0) return c;
        c = free(b, ctx).compareTo(free(a, ctx));
        if (c != 0) return c;
        return a.code().compareTo(b.code()); // детерминированный итог при полном равенстве
    }

    private BigDecimal free(SectionView s, Ctx ctx) {
        return s.maxCapacity() == null ? UNLIMITED
                : s.maxCapacity().subtract(ctx.used(s.id(), s.capacityUnitCode()));
    }

    // ================= результаты =================

    private PlacementResult assigned(PlacementItem item, SectionView s, PlacementStrategy strategy, RuleView rule, List<String> notes, List<SectionView> candidates, Ctx ctx) {
        List<Candidate> view = candidates.stream().limit(MAX_CANDIDATES)
                .map(c -> new Candidate(c.id(), c.code(),
                        c.maxCapacity() == null ? null
                                : c.maxCapacity().subtract(ctx.used(c.id(), c.capacityUnitCode()))))
                .toList(); // состояние ДО учёта текущей позиции
        ctx.add(s.id(), item.unitCode(), item.quantity(), item.productId());
        return new PlacementResult(item.lineId(), PlacementStatus.ASSIGNED, strategy, s.id(), s.code(),
                rule == null ? null : rule.id(),
                rule == null ? null : rule.ruleVersion(),
                rule == null ? null : rule.name(),
                ctx.now, false, List.copyOf(notes), view);
    }

    private PlacementResult unassigned(PlacementItem item, List<String> notes, Ctx ctx) {
        SectionView zone = ctx.zone;
        if (zone == null) notes.add("В складе не найдена зона «Нераспределено»");
        return new PlacementResult(item.lineId(), PlacementStatus.UNASSIGNED, PlacementStrategy.UNASSIGNED_ZONE,
                zone == null ? null : zone.id(), zone == null ? null : zone.code(),
                null, null, null, ctx.now, true, List.copyOf(notes), List.of());
    }

    /** Изменяемое состояние одного расчёта. */
    private static final class Ctx {
        final List<SectionView> sections;
        final Map<UUID, SectionView> byId = new HashMap<>();
        final SectionView zone;
        final OffsetDateTime now;
        private final Map<UUID, Map<String, BigDecimal>> used = new HashMap<>(); // секция → единица → занято
        private final Map<UUID, Set<UUID>> products = new HashMap<>();

        Ctx(List<SectionView> sections, List<SectionOccupancy> occupancy, OffsetDateTime now) {
            this.sections = sections;
            this.now = now;
            sections.forEach(s -> byId.put(s.id(), s));
            this.zone = sections.stream().filter(SectionView::unassignedZone).findFirst().orElse(null);
            for (SectionOccupancy o : occupancy) {
                if (o.usedByUnit() != null) {
                    o.usedByUnit().forEach((unit, qty) -> {
                        if (unit != null && qty != null) addUsed(o.sectionId(), unit, qty);
                    });
                }
                if (o.productIds() != null) {
                    products.computeIfAbsent(o.sectionId(), k -> new HashSet<>()).addAll(o.productIds());
                }
            }
        }

        private void addUsed(UUID sectionId, String unit, BigDecimal quantity) {
            used.computeIfAbsent(sectionId, k -> new HashMap<>())
                    .merge(unit.trim().toUpperCase(Locale.ROOT), quantity, BigDecimal::add);
        }

        BigDecimal used(UUID sectionId, String unitCode) {
            if (unitCode == null) return BigDecimal.ZERO;
            return used.getOrDefault(sectionId, Map.of())
                    .getOrDefault(unitCode.trim().toUpperCase(Locale.ROOT), BigDecimal.ZERO);
        }

        boolean holds(UUID sectionId, UUID productId) {
            return productId != null && products.getOrDefault(sectionId, Set.of()).contains(productId);
        }

        void add(UUID sectionId, String unitCode, BigDecimal quantity, UUID productId) {
            addUsed(sectionId, unitCode, quantity);
            if (productId != null) products.computeIfAbsent(sectionId, k -> new HashSet<>()).add(productId);
        }
    }
}