package ru.warehouse.warehouse.placement;

import org.junit.jupiter.api.Test;
import ru.warehouse.warehouse.config.PlacementProperties;
import ru.warehouse.warehouse.entity.SectionStatus;
import ru.warehouse.warehouse.placement.PlacementEngine.RuleView;
import ru.warehouse.warehouse.placement.PlacementEngine.SectionView;
import ru.warehouse.warehouse.placement.PlacementModels.*;
import ru.warehouse.warehouse.placement.RuleConditions.NumericCondition;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.*;

import static org.assertj.core.api.Assertions.assertThat;

class PlacementEngineTest {
    private final PlacementProperties props = new PlacementProperties(null, null, null); // значения по умолчанию
    private final PlacementEngine engine = new PlacementEngine(new UnitConverter(props), props.lengthTolerance());
    private final OffsetDateTime now = OffsetDateTime.now();

    private SectionView sec(String code, int priority, SectionStatus status, boolean zone,
                            Set<String> types, BigDecimal capacity) {
        return new SectionView(UUID.randomUUID(), code, priority, zone, status, types, Set.of(),
                null, null, null, null, null, null, capacity, capacity == null ? null : "PCS");
    }

    private SectionView zone() {
        return sec("UNASSIGNED", 0, SectionStatus.ACTIVE, true, Set.of(), null);
    }

    /** Правило ARM_2000: арматура, металл, длина 2000 мм. */
    private RuleView rule(String name, int priority, SectionView target) {
        var conditions = new RuleConditions(List.of("ARMATURE"), List.of("METAL"),
                Map.of("LENGTH", new NumericCondition(new BigDecimal("2000"), null, null, "MM")));
        return new RuleView(UUID.randomUUID(), 1, name, priority, false, conditions, target.id());
    }

    private PlacementItem item(String line, String type, String lengthMm, int quantity) {
        return new PlacementItem(line, UUID.randomUUID(), type, "METAL",
                Map.of("LENGTH", new NumericValue(new BigDecimal(lengthMm), "MM")),
                new BigDecimal(quantity), "PCS");
    }

    private PlacementResult one(List<SectionView> sections, List<RuleView> rules, PlacementItem item) {
        return engine.calculate(sections, rules, List.of(item), List.of(), now).get(0);
    }

    @Test
    void tzExample_armature2000Metal_goesToRuleSection_evenIfOtherSectionHasHigherPriority() {
        SectionView ma2 = sec("MA-2", 5, SectionStatus.ACTIVE, false, Set.of(), null);
        SectionView mb1 = sec("MB-1", 10, SectionStatus.ACTIVE, false, Set.of(), null);
        RuleView arm2000 = rule("ARM_2000", 100, ma2);

        PlacementResult r = one(List.of(ma2, mb1, zone()), List.of(arm2000), item("1", "ARMATURE", "2000", 10));

        assertThat(r.status()).isEqualTo(PlacementStatus.ASSIGNED);
        assertThat(r.strategy()).isEqualTo(PlacementStrategy.RULE);
        assertThat(r.sectionCode()).isEqualTo("MA-2");
        assertThat(r.ruleName()).isEqualTo("ARM_2000");
        assertThat(r.ruleVersion()).isEqualTo(1);
        assertThat(r.calculatedAt()).isEqualTo(now); // основание выбора: правило, версия, время расчёта
    }

    @Test
    void lengthIsComparedWithTolerance() {
        SectionView ma2 = sec("MA-2", 5, SectionStatus.ACTIVE, false, Set.of(), null);
        SectionView mb1 = sec("MB-1", 10, SectionStatus.ACTIVE, false, Set.of(), null);
        List<RuleView> rules = List.of(rule("ARM_2000", 100, ma2));

        // 2000.3 мм в пределах погрешности 0.5 мм: правило сработало
        assertThat(one(List.of(ma2, mb1, zone()), rules, item("1", "ARMATURE", "2000.3", 1)).sectionCode())
                .isEqualTo("MA-2");
        // 2001 мм вне погрешности: правило не подошло, выбрана секция с высшим приоритетом
        PlacementResult r = one(List.of(ma2, mb1, zone()), rules, item("2", "ARMATURE", "2001", 1));
        assertThat(r.strategy()).isEqualTo(PlacementStrategy.SECTION_PRIORITY);
        assertThat(r.sectionCode()).isEqualTo("MB-1");
    }

    @Test
    void blockedRuleTarget_fallsBackToBestSection_andExplainsWhy() {
        SectionView ma2 = sec("MA-2", 5, SectionStatus.BLOCKED, false, Set.of(), null);
        SectionView mb1 = sec("MB-1", 1, SectionStatus.ACTIVE, false, Set.of(), null);

        PlacementResult r = one(List.of(ma2, mb1, zone()), List.of(rule("ARM_2000", 100, ma2)),
                item("1", "ARMATURE", "2000", 1));

        assertThat(r.sectionCode()).isEqualTo("MB-1");
        assertThat(r.strategy()).isEqualTo(PlacementStrategy.SECTION_PRIORITY);
        assertThat(r.notes()).anyMatch(n -> n.contains("ARM_2000") && n.contains("BLOCKED"));
    }

    @Test
    void noSuitableSection_goesToUnassignedZone_withStorekeeperAction() {
        SectionView pipes = sec("S-PIPE", 5, SectionStatus.ACTIVE, false, Set.of("PIPE"), null);

        PlacementResult r = one(List.of(pipes, zone()), List.of(), item("1", "ARMATURE", "2000", 1));

        assertThat(r.status()).isEqualTo(PlacementStatus.UNASSIGNED);
        assertThat(r.sectionCode()).isEqualTo("UNASSIGNED");
        assertThat(r.requiresStorekeeperAction()).isTrue();
    }

    @Test
    void capacityIsConsumedWithinOneRequest() {
        SectionView s1 = sec("S1", 10, SectionStatus.ACTIVE, false, Set.of(), BigDecimal.TEN);
        SectionView s2 = sec("S2", 1, SectionStatus.ACTIVE, false, Set.of(), null);

        List<PlacementResult> r = engine.calculate(List.of(s1, s2, zone()), List.of(),
                List.of(item("a", "PIPE", "100", 6), item("b", "PIPE", "100", 6)), List.of(), now);

        assertThat(r.get(0).sectionCode()).isEqualTo("S1");
        assertThat(r.get(1).sectionCode()).isEqualTo("S2"); // в S1 осталось место только на 4
    }
}