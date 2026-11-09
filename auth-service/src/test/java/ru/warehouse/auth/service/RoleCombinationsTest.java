package ru.warehouse.auth.service;

import org.junit.jupiter.api.Test;
import java.util.List;
import java.util.Set;
import static org.assertj.core.api.Assertions.assertThat;

class RoleCombinationsTest {
    private static final List<String> DEFAULT = List.of("USER+STOREKEEPER", "STOREKEEPER+ADMIN");

    @Test
    void forbiddenPair_isReported() {
        assertThat(RoleCombinations.violation(Set.of("USER", "STOREKEEPER"), DEFAULT)).contains("STOREKEEPER", "USER");
        assertThat(RoleCombinations.violation(Set.of("ADMIN", "STOREKEEPER"), DEFAULT)).isNotNull();
    }

    @Test
    void allowedRoles_passEvenIfOnePartOfPairIsPresent() {
        assertThat(RoleCombinations.violation(Set.of("USER"), DEFAULT)).isNull();
        assertThat(RoleCombinations.violation(Set.of("USER", "WAREHOUSE_WORKER"), DEFAULT)).isNull();
        assertThat(RoleCombinations.violation(Set.of("ADMIN", "USER"), DEFAULT)).isNull();
    }

    @Test
    void caseAndSpaces_areIgnored() {
        assertThat(RoleCombinations.violation(List.of(" user ", "Storekeeper"), List.of(" user + storekeeper "))).isNotNull();
    }

    @Test
    void emptyOrNullConfiguration_meansNoRestrictions() {
        assertThat(RoleCombinations.violation(Set.of("USER", "STOREKEEPER"), null)).isNull();
        assertThat(RoleCombinations.violation(Set.of("USER", "STOREKEEPER"), List.of(" ", ""))).isNull();
    }

    @Test
    void threeRoleCombination_requiresAllThree() {
        List<String> three = List.of("USER+STOREKEEPER+ADMIN");
        assertThat(RoleCombinations.violation(Set.of("USER", "STOREKEEPER"), three)).isNull();
        assertThat(RoleCombinations.violation(Set.of("USER", "STOREKEEPER", "ADMIN"), three)).isNotNull();
    }
}
