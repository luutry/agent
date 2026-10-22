package com.luutry.agent.common.model.dto;

import com.luutry.agent.common.constant.AgentConstants;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for PageRequest
 */
@DisplayName("PageRequest Tests")
class PageRequestTest {

    @Test
    @DisplayName("Default values should be set correctly")
    void testDefaultValues() {
        PageRequest request = new PageRequest();

        assertEquals(1, request.getPageNum());
        assertEquals(AgentConstants.DEFAULT_PAGE_SIZE, request.getPageSize());
    }

    @Test
    @DisplayName("getOffset should calculate correctly for first page")
    void testOffsetFirstPage() {
        PageRequest request = new PageRequest();
        request.setPageNum(1);
        request.setPageSize(20);

        assertEquals(0, request.getOffset());
    }

    @Test
    @DisplayName("getOffset should calculate correctly for third page")
    void testOffsetThirdPage() {
        PageRequest request = new PageRequest();
        request.setPageNum(3);
        request.setPageSize(10);

        assertEquals(20, request.getOffset());
    }

    @Test
    @DisplayName("getOffset should handle large page numbers")
    void testOffsetLargePageNumber() {
        PageRequest request = new PageRequest();
        request.setPageNum(100);
        request.setPageSize(50);

        assertEquals(4950, request.getOffset());
    }
}
