package com.fhir.model;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("CursorPage Tests")
class CursorPageTest {

    @Nested
    @DisplayName("Builder Tests")
    class BuilderTests {

        @Test
        @DisplayName("Should create cursor page using builder")
        void shouldCreateCursorPageUsingBuilder() {
            List<String> content = List.of("item1", "item2", "item3");

            CursorPage<String> page = CursorPage.<String>builder()
                    .content(content)
                    .hasNext(true)
                    .hasPrevious(false)
                    .nextCursor("cursor123")
                    .previousCursor(null)
                    .size(3)
                    .estimatedTotal(100L)
                    .build();

            assertEquals(3, page.getContent().size());
            assertTrue(page.isHasNext());
            assertFalse(page.isHasPrevious());
            assertEquals("cursor123", page.getNextCursor());
            assertNull(page.getPreviousCursor());
            assertEquals(3, page.getSize());
            assertEquals(100L, page.getEstimatedTotal());
        }

        @Test
        @DisplayName("Should create cursor page using no-args constructor")
        void shouldCreateCursorPageUsingNoArgsConstructor() {
            CursorPage<String> page = new CursorPage<>();

            assertNull(page.getContent());
            assertFalse(page.isHasNext());
            assertFalse(page.isHasPrevious());
        }

        @Test
        @DisplayName("Should create cursor page using all-args constructor")
        void shouldCreateCursorPageUsingAllArgsConstructor() {
            List<String> content = List.of("item1");

            CursorPage<String> page = new CursorPage<>(
                    content,
                    true,
                    true,
                    "next",
                    "prev",
                    1,
                    50L
            );

            assertEquals(1, page.getContent().size());
            assertTrue(page.isHasNext());
            assertTrue(page.isHasPrevious());
            assertEquals("next", page.getNextCursor());
            assertEquals("prev", page.getPreviousCursor());
            assertEquals(50L, page.getEstimatedTotal());
        }
    }

    @Nested
    @DisplayName("Static Factory Tests")
    class StaticFactoryTests {

        @Test
        @DisplayName("Should create page with simple factory method")
        void shouldCreatePageWithSimpleFactoryMethod() {
            List<String> content = List.of("item1", "item2");

            CursorPage<String> page = CursorPage.of(content, true, "nextCursor");

            assertEquals(2, page.getContent().size());
            assertTrue(page.isHasNext());
            assertFalse(page.isHasPrevious());
            assertEquals("nextCursor", page.getNextCursor());
            assertEquals(2, page.getSize());
        }

        @Test
        @DisplayName("Should create page with full factory method")
        void shouldCreatePageWithFullFactoryMethod() {
            List<String> content = List.of("item1");

            CursorPage<String> page = CursorPage.of(
                    content,
                    true,
                    true,
                    "next",
                    "prev",
                    100L
            );

            assertEquals(1, page.getContent().size());
            assertTrue(page.isHasNext());
            assertTrue(page.isHasPrevious());
            assertEquals("next", page.getNextCursor());
            assertEquals("prev", page.getPreviousCursor());
            assertEquals(100L, page.getEstimatedTotal());
        }
    }

    @Nested
    @DisplayName("Content Tests")
    class ContentTests {

        @Test
        @DisplayName("Should return true for hasContent when content exists")
        void shouldReturnTrueForHasContentWhenContentExists() {
            CursorPage<String> page = CursorPage.of(List.of("item"), false, null);

            assertTrue(page.hasContent());
        }

        @Test
        @DisplayName("Should return false for hasContent when content is null")
        void shouldReturnFalseForHasContentWhenContentIsNull() {
            CursorPage<String> page = new CursorPage<>();

            assertFalse(page.hasContent());
        }

        @Test
        @DisplayName("Should return false for hasContent when content is empty")
        void shouldReturnFalseForHasContentWhenContentIsEmpty() {
            CursorPage<String> page = CursorPage.of(Collections.emptyList(), false, null);

            assertFalse(page.hasContent());
        }
    }

    @Nested
    @DisplayName("Navigation Tests")
    class NavigationTests {

        @Test
        @DisplayName("Should return true for isFirst when no previous page")
        void shouldReturnTrueForIsFirstWhenNoPreviousPage() {
            CursorPage<String> page = CursorPage.<String>builder()
                    .hasPrevious(false)
                    .build();

            assertTrue(page.isFirst());
        }

        @Test
        @DisplayName("Should return false for isFirst when has previous page")
        void shouldReturnFalseForIsFirstWhenHasPreviousPage() {
            CursorPage<String> page = CursorPage.<String>builder()
                    .hasPrevious(true)
                    .build();

            assertFalse(page.isFirst());
        }

        @Test
        @DisplayName("Should return true for isLast when no next page")
        void shouldReturnTrueForIsLastWhenNoNextPage() {
            CursorPage<String> page = CursorPage.<String>builder()
                    .hasNext(false)
                    .build();

            assertTrue(page.isLast());
        }

        @Test
        @DisplayName("Should return false for isLast when has next page")
        void shouldReturnFalseForIsLastWhenHasNextPage() {
            CursorPage<String> page = CursorPage.<String>builder()
                    .hasNext(true)
                    .build();

            assertFalse(page.isLast());
        }
    }

    @Nested
    @DisplayName("Equality Tests")
    class EqualityTests {

        @Test
        @DisplayName("Should implement equals correctly")
        void shouldImplementEqualsCorrectly() {
            CursorPage<String> page1 = CursorPage.<String>builder()
                    .content(List.of("a", "b"))
                    .hasNext(true)
                    .size(2)
                    .build();

            CursorPage<String> page2 = CursorPage.<String>builder()
                    .content(List.of("a", "b"))
                    .hasNext(true)
                    .size(2)
                    .build();

            assertEquals(page1, page2);
        }

        @Test
        @DisplayName("Should implement hashCode correctly")
        void shouldImplementHashCodeCorrectly() {
            CursorPage<String> page1 = CursorPage.<String>builder()
                    .content(List.of("a"))
                    .hasNext(false)
                    .build();

            CursorPage<String> page2 = CursorPage.<String>builder()
                    .content(List.of("a"))
                    .hasNext(false)
                    .build();

            assertEquals(page1.hashCode(), page2.hashCode());
        }

        @Test
        @DisplayName("Should implement toString")
        void shouldImplementToString() {
            CursorPage<String> page = CursorPage.<String>builder()
                    .content(List.of("item"))
                    .hasNext(true)
                    .build();

            String toString = page.toString();

            assertNotNull(toString);
            assertTrue(toString.contains("item") || toString.contains("CursorPage"));
        }
    }

    @Test
    @DisplayName("Should support setters")
    void shouldSupportSetters() {
        CursorPage<String> page = new CursorPage<>();

        page.setContent(List.of("test"));
        page.setHasNext(true);
        page.setHasPrevious(true);
        page.setNextCursor("next");
        page.setPreviousCursor("prev");
        page.setSize(1);
        page.setEstimatedTotal(10L);

        assertEquals(List.of("test"), page.getContent());
        assertTrue(page.isHasNext());
        assertTrue(page.isHasPrevious());
        assertEquals("next", page.getNextCursor());
        assertEquals("prev", page.getPreviousCursor());
        assertEquals(1, page.getSize());
        assertEquals(10L, page.getEstimatedTotal());
    }
}
