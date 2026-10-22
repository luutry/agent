package com.luutry.agent.common.util;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for JsonUtils
 */
@DisplayName("JsonUtils Tests")
class JsonUtilsTest {

    @Test
    @DisplayName("toJson should serialize object correctly")
    void testToJson() {
        Map<String, String> map = Map.of("key", "value");
        String json = JsonUtils.toJson(map);

        assertNotNull(json);
        assertTrue(json.contains("\"key\""));
        assertTrue(json.contains("\"value\""));
    }

    @Test
    @DisplayName("toJson with null should return 'null' string")
    void testToJsonNull() {
        assertEquals("null", JsonUtils.toJson(null));
    }

    @Test
    @DisplayName("fromJson should deserialize correctly")
    void testFromJson() {
        String json = "{\"name\":\"test\",\"age\":25}";
        TestPojo result = JsonUtils.fromJson(json, TestPojo.class);

        assertNotNull(result);
        assertEquals("test", result.getName());
        assertEquals(25, result.getAge());
    }

    @Test
    @DisplayName("fromJson with null should return null")
    void testFromJsonNull() {
        assertNull(JsonUtils.fromJson(null, TestPojo.class));
        assertNull(JsonUtils.fromJson("", TestPojo.class));
        assertNull(JsonUtils.fromJson("   ", TestPojo.class));
    }

    @Test
    @DisplayName("fromJson with invalid JSON should return null without throwing")
    void testFromJsonInvalid() {
        assertNull(JsonUtils.fromJson("not-a-json", TestPojo.class));
    }

    @Test
    @DisplayName("fromJsonList should deserialize array elements")
    void testFromJsonList() {
        String json = "[{\"name\":\"first\",\"age\":1},{\"name\":\"second\",\"age\":2}]";

        List<TestPojo> result = JsonUtils.fromJsonList(json, TestPojo.class);

        assertEquals(2, result.size());
        assertEquals("second", result.get(1).getName());
        assertEquals(2, result.get(1).getAge());
    }

    @Test
    @DisplayName("fromJsonList should return empty list for blank or invalid input")
    void testFromJsonListEdgeCases() {
        assertTrue(JsonUtils.fromJsonList(null, TestPojo.class).isEmpty());
        assertTrue(JsonUtils.fromJsonList("", TestPojo.class).isEmpty());
        assertTrue(JsonUtils.fromJsonList("not-a-json", TestPojo.class).isEmpty());
    }

    @Test
    @DisplayName("toJson should include null fields")
    void testToJsonIncludesNull() {
        TestPojo pojo = new TestPojo();
        pojo.setName("test");
        // age is null
        String json = JsonUtils.toJson(pojo);

        assertNotNull(json);
        assertTrue(json.contains("\"name\":\"test\""));
        assertTrue(json.contains("\"age\":null"));
    }

    /**
     * Simple POJO for test serialization
     */
    static class TestPojo {
        private String name;
        private Integer age;

        public String getName() { return name; }
        public void setName(String name) { this.name = name; }
        public Integer getAge() { return age; }
        public void setAge(Integer age) { this.age = age; }
    }
}
