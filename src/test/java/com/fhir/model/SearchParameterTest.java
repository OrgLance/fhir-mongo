package com.fhir.model;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("SearchParameter Tests")
class SearchParameterTest {

    @Test
    @DisplayName("Should create search parameter using builder")
    void shouldCreateSearchParameterUsingBuilder() {
        SearchParameter param = SearchParameter.builder()
                .name("family")
                .value("Smith")
                .modifier("exact")
                .prefix("eq")
                .build();

        assertEquals("family", param.getName());
        assertEquals("Smith", param.getValue());
        assertEquals("exact", param.getModifier());
        assertEquals("eq", param.getPrefix());
    }

    @Test
    @DisplayName("Should create search parameter using no-args constructor")
    void shouldCreateSearchParameterUsingNoArgsConstructor() {
        SearchParameter param = new SearchParameter();

        assertNull(param.getName());
        assertNull(param.getValue());
        assertNull(param.getModifier());
        assertNull(param.getPrefix());
    }

    @Test
    @DisplayName("Should create search parameter using all-args constructor")
    void shouldCreateSearchParameterUsingAllArgsConstructor() {
        SearchParameter param = new SearchParameter("name", "value", "modifier", "prefix");

        assertEquals("name", param.getName());
        assertEquals("value", param.getValue());
        assertEquals("modifier", param.getModifier());
        assertEquals("prefix", param.getPrefix());
    }

    @Test
    @DisplayName("Should support setters")
    void shouldSupportSetters() {
        SearchParameter param = new SearchParameter();

        param.setName("gender");
        param.setValue("male");
        param.setModifier("not");
        param.setPrefix(null);

        assertEquals("gender", param.getName());
        assertEquals("male", param.getValue());
        assertEquals("not", param.getModifier());
        assertNull(param.getPrefix());
    }

    @Test
    @DisplayName("Should implement equals correctly")
    void shouldImplementEqualsCorrectly() {
        SearchParameter param1 = SearchParameter.builder()
                .name("name")
                .value("test")
                .build();

        SearchParameter param2 = SearchParameter.builder()
                .name("name")
                .value("test")
                .build();

        SearchParameter param3 = SearchParameter.builder()
                .name("different")
                .value("test")
                .build();

        assertEquals(param1, param2);
        assertNotEquals(param1, param3);
    }

    @Test
    @DisplayName("Should implement hashCode correctly")
    void shouldImplementHashCodeCorrectly() {
        SearchParameter param1 = SearchParameter.builder()
                .name("name")
                .value("test")
                .build();

        SearchParameter param2 = SearchParameter.builder()
                .name("name")
                .value("test")
                .build();

        assertEquals(param1.hashCode(), param2.hashCode());
    }

    @Test
    @DisplayName("Should implement toString")
    void shouldImplementToString() {
        SearchParameter param = SearchParameter.builder()
                .name("family")
                .value("Smith")
                .build();

        String toString = param.toString();

        assertNotNull(toString);
        assertTrue(toString.contains("family"));
        assertTrue(toString.contains("Smith"));
    }
}
