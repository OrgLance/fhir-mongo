package com.fhir.util;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

/**
 * Utility for compressing large FHIR resources.
 * Achieves 60-80% compression ratio for JSON content.
 */
public final class CompressionUtil {

    private static final Logger logger = LoggerFactory.getLogger(CompressionUtil.class);

    // Threshold for compression (resources larger than this will be compressed)
    public static final int COMPRESSION_THRESHOLD_BYTES = 10_000; // 10KB

    private CompressionUtil() {
        // Utility class
    }

    /**
     * Compress a string using GZIP.
     *
     * @param data The string to compress
     * @return Compressed bytes
     */
    public static byte[] compress(String data) {
        if (data == null || data.isEmpty()) {
            return new byte[0];
        }

        try (ByteArrayOutputStream bos = new ByteArrayOutputStream();
             GZIPOutputStream gzip = new GZIPOutputStream(bos)) {
            gzip.write(data.getBytes(StandardCharsets.UTF_8));
            gzip.finish();
            return bos.toByteArray();
        } catch (IOException e) {
            logger.error("Failed to compress data: {}", e.getMessage());
            throw new RuntimeException("Compression failed", e);
        }
    }

    /**
     * Decompress GZIP bytes to string.
     *
     * @param compressed The compressed bytes
     * @return Decompressed string
     */
    public static String decompress(byte[] compressed) {
        if (compressed == null || compressed.length == 0) {
            return "";
        }

        try (ByteArrayInputStream bis = new ByteArrayInputStream(compressed);
             GZIPInputStream gis = new GZIPInputStream(bis);
             ByteArrayOutputStream bos = new ByteArrayOutputStream()) {

            byte[] buffer = new byte[8192];
            int len;
            while ((len = gis.read(buffer)) != -1) {
                bos.write(buffer, 0, len);
            }
            return bos.toString(StandardCharsets.UTF_8);
        } catch (IOException e) {
            logger.error("Failed to decompress data: {}", e.getMessage());
            throw new RuntimeException("Decompression failed", e);
        }
    }

    /**
     * Check if compression should be used for the given data.
     *
     * @param data The data to check
     * @return true if compression is recommended
     */
    public static boolean shouldCompress(String data) {
        return data != null && data.length() > COMPRESSION_THRESHOLD_BYTES;
    }

    /**
     * Calculate compression ratio.
     *
     * @param originalSize   Original size in bytes
     * @param compressedSize Compressed size in bytes
     * @return Compression ratio (e.g., 0.3 means 70% reduction)
     */
    public static double compressionRatio(int originalSize, int compressedSize) {
        if (originalSize == 0) return 0;
        return (double) compressedSize / originalSize;
    }

    /**
     * Get size savings as percentage.
     *
     * @param originalSize   Original size in bytes
     * @param compressedSize Compressed size in bytes
     * @return Savings percentage (e.g., 70 means 70% smaller)
     */
    public static int savingsPercent(int originalSize, int compressedSize) {
        if (originalSize == 0) return 0;
        return 100 - (int) ((compressedSize * 100.0) / originalSize);
    }
}
