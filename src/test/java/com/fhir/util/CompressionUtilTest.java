package com.fhir.util;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("CompressionUtil Tests")
class CompressionUtilTest {

    @Nested
    @DisplayName("Compression Tests")
    class CompressionTests {

        @Test
        @DisplayName("Should compress and decompress string correctly")
        void shouldCompressAndDecompressCorrectly() {
            String original = "This is a test string that should be compressed and decompressed correctly.";

            byte[] compressed = CompressionUtil.compress(original);
            String decompressed = CompressionUtil.decompress(compressed);

            assertEquals(original, decompressed);
        }

        @Test
        @DisplayName("Should compress large JSON string")
        void shouldCompressLargeJsonString() {
            StringBuilder sb = new StringBuilder();
            sb.append("{\"resourceType\":\"Patient\",\"data\":[");
            for (int i = 0; i < 1000; i++) {
                if (i > 0) sb.append(",");
                sb.append("{\"id\":\"").append(i).append("\",\"name\":\"Patient").append(i).append("\"}");
            }
            sb.append("]}");
            String largeJson = sb.toString();

            byte[] compressed = CompressionUtil.compress(largeJson);
            String decompressed = CompressionUtil.decompress(compressed);

            assertEquals(largeJson, decompressed);
            assertTrue(compressed.length < largeJson.getBytes().length, "Compressed size should be smaller");
        }

        @Test
        @DisplayName("Should handle empty string")
        void shouldHandleEmptyString() {
            String original = "";

            byte[] compressed = CompressionUtil.compress(original);
            String decompressed = CompressionUtil.decompress(compressed);

            assertEquals(0, compressed.length);
            assertEquals("", decompressed);
        }

        @Test
        @DisplayName("Should handle special characters")
        void shouldHandleSpecialCharacters() {
            String original = "Test with special chars: éàü日本語 <>&\"'";

            byte[] compressed = CompressionUtil.compress(original);
            String decompressed = CompressionUtil.decompress(compressed);

            assertEquals(original, decompressed);
        }

        @Test
        @DisplayName("Should return empty array for null input on compress")
        void shouldReturnEmptyArrayForNullInputOnCompress() {
            byte[] result = CompressionUtil.compress(null);
            assertEquals(0, result.length);
        }

        @Test
        @DisplayName("Should return empty string for null input on decompress")
        void shouldReturnEmptyStringForNullInputOnDecompress() {
            String result = CompressionUtil.decompress(null);
            assertEquals("", result);
        }

        @Test
        @DisplayName("Should return empty string for empty array on decompress")
        void shouldReturnEmptyStringForEmptyArrayOnDecompress() {
            String result = CompressionUtil.decompress(new byte[0]);
            assertEquals("", result);
        }
    }

    @Nested
    @DisplayName("shouldCompress Tests")
    class ShouldCompressTests {

        @Test
        @DisplayName("Should return false for small strings")
        void shouldReturnFalseForSmallStrings() {
            String smallString = "Small";
            assertFalse(CompressionUtil.shouldCompress(smallString));
        }

        @Test
        @DisplayName("Should return true for large strings over threshold")
        void shouldReturnTrueForLargeStrings() {
            StringBuilder sb = new StringBuilder();
            // COMPRESSION_THRESHOLD_BYTES is 10,000
            for (int i = 0; i < 15000; i++) {
                sb.append("x");
            }
            assertTrue(CompressionUtil.shouldCompress(sb.toString()));
        }

        @Test
        @DisplayName("Should return false for null")
        void shouldReturnFalseForNull() {
            assertFalse(CompressionUtil.shouldCompress(null));
        }

        @Test
        @DisplayName("Should return false for strings at threshold")
        void shouldReturnFalseForStringsAtThreshold() {
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < CompressionUtil.COMPRESSION_THRESHOLD_BYTES; i++) {
                sb.append("x");
            }
            assertFalse(CompressionUtil.shouldCompress(sb.toString()));
        }

        @Test
        @DisplayName("Should return true for strings above threshold")
        void shouldReturnTrueForStringsAboveThreshold() {
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < CompressionUtil.COMPRESSION_THRESHOLD_BYTES + 1; i++) {
                sb.append("x");
            }
            assertTrue(CompressionUtil.shouldCompress(sb.toString()));
        }
    }

    @Nested
    @DisplayName("savingsPercent Tests")
    class SavingsPercentTests {

        @Test
        @DisplayName("Should calculate savings percentage correctly")
        void shouldCalculateSavingsPercentageCorrectly() {
            int originalSize = 1000;
            int compressedSize = 200;

            int savings = CompressionUtil.savingsPercent(originalSize, compressedSize);

            assertEquals(80, savings);
        }

        @Test
        @DisplayName("Should return 0 for same size")
        void shouldReturnZeroForSameSize() {
            int size = 1000;

            int savings = CompressionUtil.savingsPercent(size, size);

            assertEquals(0, savings);
        }

        @Test
        @DisplayName("Should handle zero original size")
        void shouldHandleZeroOriginalSize() {
            int savings = CompressionUtil.savingsPercent(0, 0);
            assertEquals(0, savings);
        }

        @Test
        @DisplayName("Should handle negative savings (compression increased size)")
        void shouldHandleNegativeSavings() {
            int originalSize = 100;
            int compressedSize = 150;

            int savings = CompressionUtil.savingsPercent(originalSize, compressedSize);

            assertEquals(-50, savings);
        }
    }

    @Nested
    @DisplayName("compressionRatio Tests")
    class CompressionRatioTests {

        @Test
        @DisplayName("Should calculate compression ratio correctly")
        void shouldCalculateCompressionRatioCorrectly() {
            int originalSize = 1000;
            int compressedSize = 300;

            double ratio = CompressionUtil.compressionRatio(originalSize, compressedSize);

            assertEquals(0.3, ratio, 0.01);
        }

        @Test
        @DisplayName("Should return 0 for zero original size")
        void shouldReturnZeroForZeroOriginalSize() {
            double ratio = CompressionUtil.compressionRatio(0, 0);
            assertEquals(0, ratio);
        }

        @Test
        @DisplayName("Should return 1 for same size")
        void shouldReturnOneForSameSize() {
            double ratio = CompressionUtil.compressionRatio(1000, 1000);
            assertEquals(1.0, ratio, 0.01);
        }
    }
}
