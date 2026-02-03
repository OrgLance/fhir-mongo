package com.fhir.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * Cursor-based pagination response.
 * Provides O(1) pagination performance regardless of page depth.
 *
 * @param <T> The type of content
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CursorPage<T> {

    /**
     * The content of this page.
     */
    private List<T> content;

    /**
     * Whether there are more results after this page.
     */
    private boolean hasNext;

    /**
     * Whether there are results before this page.
     */
    private boolean hasPrevious;

    /**
     * Cursor to fetch the next page (typically the last ID in content).
     */
    private String nextCursor;

    /**
     * Cursor to fetch the previous page (typically the first ID in content).
     */
    private String previousCursor;

    /**
     * Number of items in this page.
     */
    private int size;

    /**
     * Estimated total count (may not be exact for performance reasons).
     */
    private Long estimatedTotal;

    /**
     * Create a cursor page from a list of documents.
     */
    public static <T> CursorPage<T> of(List<T> content, boolean hasNext, String nextCursor) {
        return CursorPage.<T>builder()
                .content(content)
                .hasNext(hasNext)
                .hasPrevious(false)
                .nextCursor(nextCursor)
                .size(content.size())
                .build();
    }

    /**
     * Create a cursor page with full navigation.
     */
    public static <T> CursorPage<T> of(List<T> content, boolean hasNext, boolean hasPrevious,
                                        String nextCursor, String previousCursor, Long estimatedTotal) {
        return CursorPage.<T>builder()
                .content(content)
                .hasNext(hasNext)
                .hasPrevious(hasPrevious)
                .nextCursor(nextCursor)
                .previousCursor(previousCursor)
                .size(content.size())
                .estimatedTotal(estimatedTotal)
                .build();
    }

    /**
     * Check if this page has content.
     */
    public boolean hasContent() {
        return content != null && !content.isEmpty();
    }

    /**
     * Check if this is the first page.
     */
    public boolean isFirst() {
        return !hasPrevious;
    }

    /**
     * Check if this is the last page.
     */
    public boolean isLast() {
        return !hasNext;
    }
}
