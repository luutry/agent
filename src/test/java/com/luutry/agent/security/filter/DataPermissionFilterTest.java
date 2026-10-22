package com.luutry.agent.security.filter;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for DataPermissionFilter
 */
@DisplayName("DataPermissionFilter Tests")
class DataPermissionFilterTest {

    private DataPermissionFilter<TestEmployee> filter;

    @BeforeEach
    void setUp() {
        filter = new DataPermissionFilter<>();
    }

    @Test
    @DisplayName("Should filter data by managed org IDs")
    void testFilterByOrgIds() {
        List<String> managedOrgs = List.of("ORG_A", "ORG_B");
        List<TestEmployee> data = List.of(
                new TestEmployee("E1", "ORG_A"),
                new TestEmployee("E2", "ORG_B"),
                new TestEmployee("E3", "ORG_C")
        );

        List<TestEmployee> result = filter.filter(managedOrgs, data);

        assertEquals(2, result.size());
        assertEquals("E1", result.get(0).name());
        assertEquals("E2", result.get(1).name());
    }

    @Test
    @DisplayName("Should return empty list when managed org IDs is empty")
    void testFilterEmptyOrgIds() {
        List<TestEmployee> data = List.of(new TestEmployee("E1", "ORG_A"));

        List<TestEmployee> result = filter.filter(List.of(), data);

        assertTrue(result.isEmpty());
    }

    @Test
    @DisplayName("Should return empty list when managed org IDs is null")
    void testFilterNullOrgIds() {
        List<TestEmployee> data = List.of(new TestEmployee("E1", "ORG_A"));

        List<TestEmployee> result = filter.filter(null, data);

        assertTrue(result.isEmpty());
    }

    @Test
    @DisplayName("Should return empty list when data is empty")
    void testFilterEmptyData() {
        List<String> managedOrgs = List.of("ORG_A");

        List<TestEmployee> result = filter.filter(managedOrgs, List.of());

        assertTrue(result.isEmpty());
    }

    @Test
    @DisplayName("Should return empty list when data is null")
    void testFilterNullData() {
        List<String> managedOrgs = List.of("ORG_A");

        List<TestEmployee> result = filter.filter(managedOrgs, null);

        assertTrue(result.isEmpty());
    }

    @Test
    @DisplayName("isInScope should return true for data within scope")
    void testIsInScopeTrue() {
        assertTrue(filter.isInScope(List.of("ORG_A"), new TestEmployee("E1", "ORG_A")));
    }

    @Test
    @DisplayName("isInScope should return false for data outside scope")
    void testIsInScopeFalse() {
        assertFalse(filter.isInScope(List.of("ORG_A"), new TestEmployee("E1", "ORG_B")));
    }

    @Test
    @DisplayName("isInScope should return false for null inputs")
    void testIsInScopeNull() {
        assertFalse(filter.isInScope(null, new TestEmployee("E1", "ORG_A")));
        assertFalse(filter.isInScope(List.of("ORG_A"), null));
    }

    /**
     * Test data entity implementing DataScopeAware
     */
    record TestEmployee(String name, String orgId) implements DataPermissionFilter.DataScopeAware {
        @Override
        public String getOrgId() {
            return orgId;
        }
    }
}
