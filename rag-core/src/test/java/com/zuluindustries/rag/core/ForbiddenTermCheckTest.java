package com.zuluindustries.rag.core;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;
import java.util.regex.Pattern;

import org.junit.jupiter.api.Test;

class ForbiddenTermCheckTest {

    @Test
    void reportsEveryForbiddenTerm() {
        ForbiddenTermCheck check = new ForbiddenTermCheck(
                Pattern.compile("Wasserhindernis\\w*|Regel \\d+-\\d+", Pattern.CASE_INSENSITIVE), "Alter Begriff");

        List<String> problems = check.problems("Im wasserhindernis gilt Regel 26-1, sonst Regel 17.1d.", List.of());

        assertEquals(List.of("Alter Begriff: „wasserhindernis“", "Alter Begriff: „Regel 26-1“"), problems);
    }
}
