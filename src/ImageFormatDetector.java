import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.Arrays;
/**
 * Image Format Detector
 *
 * This program reads an image file and identifies its format by examining internal
 * binary signatures (magic bytes) and structural markers, not the file extension.
 *
 * Supported formats: PNG, JPEG, GIF, BMP, TIFF.
 *
 * Detection approach:
 * - Reads the first 32 bytes of the file header into a buffer.
 * - Each format is checked by a dedicated method that verifies:
 *   1. Magic bytes / signature at the start of the file.
 *   2. Additional structural integrity checks (e.g., IHDR for PNG, EOI marker for JPEG).
 * - If no known signature matches, the format is reported as "RAW".
 *
 * Why these checks matter:
 * - File extensions can be misleading or missing.
 * - Binary signatures uniquely identify the true format regardless of filename.
 * - Extra validations (size, offsets, version numbers) reduce false positives.
 */
public class ImageFormatDetector {

    public static void main(String[] args) {
//        String testPath = "/home/dwieb/Downloads/image/ChatGPT Image Jan 17, 2026, 10_32_40 PM.png";
//        String testPath = "/home/dwieb/Downloads/file_example_TIFF_10MB.tiff";
//        String testPath = "/home/dwieb/Downloads/larchick-notes-1136234.png";
//        String testPath = "/home/dwieb/Downloads/WhatsApp Image 2026-04-06 at 10.41.35 PM.jpeg";
        String testPath = "/home/dwieb/Downloads/bmp-large-2000px.bmp";
        try {
            String format = detectImageFormat(testPath);
            System.out.println("Format detected: " + format);
        } catch (IOException e) {
            System.err.println("Error reading file: " + e.getMessage());
        }
    }

    public static String detectImageFormat(String filepath) throws IOException {
        File file = new File(filepath);
        if (!file.exists()) {
            return "File not found";
        }

        long fileSize = file.length();
        if (fileSize < 16) {
            return "RAW";
        }

        try (RandomAccessFile raf = new RandomAccessFile(file, "r")) {
            byte[] header = new byte[32];
            raf.readFully(header);

            if (isPng(header)) return "PNG";
            if (isJpeg(header, raf)) return "JPEG";
            if (isGif(header)) return "GIF";
            if (isBmp(header, fileSize)) return "BMP";
            if (isTiff(header, fileSize)) return "TIFF";
        }

        return "RAW";
    }

    private static boolean isPng(byte[] header) {
        // PNG Signature: 89 50 4E 47 0D 0A 1A 0A
        byte[] pngSig = {(byte) 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A};
        if (startsWith(header, pngSig)) {
            // Check IHDR: Length 13 (Big Endian) and "IHDR" type
            ByteBuffer bb = ByteBuffer.wrap(header, 8, 8).order(ByteOrder.BIG_ENDIAN);
            int len = bb.getInt();
            byte[] type = new byte[4];
            bb.get(type);
            return len == 13 && Arrays.equals(type, "IHDR".getBytes());
        }
        return false;
    }

    private static boolean isJpeg(byte[] header, RandomAccessFile raf) throws IOException {
        // JPEG Starts with FF D8 FF
        if ((header[0] & 0xFF) == 0xFF && (header[1] & 0xFF) == 0xD8 && (header[2] & 0xFF) == 0xFF) {
            // Check for EOI marker (FF D9) at the very end
            raf.seek(raf.length() - 2);
            byte[] footer = new byte[2];
            raf.readFully(footer);
            return (footer[0] & 0xFF) == 0xFF && (footer[1] & 0xFF) == 0xD9;
        }
        return false;
    }

    private static boolean isGif(byte[] header) {
        String sig = new String(header, 0, 6);
        if (sig.equals("GIF87a") || sig.equals("GIF89a")) {
            // Width and Height are Little Endian shorts at offset 6
            ByteBuffer bb = ByteBuffer.wrap(header, 6, 4).order(ByteOrder.LITTLE_ENDIAN);
            short w = bb.getShort();
            short h = bb.getShort();
            return w > 0 && h > 0;
        }
        return false;
    }

    private static boolean isBmp(byte[] header, long fileSize) {
        if (header[0] == 'B' && header[1] == 'M') {
            ByteBuffer bb = ByteBuffer.wrap(header, 2, 12).order(ByteOrder.LITTLE_ENDIAN);
            int bmpSize = bb.getInt(); // Offset 2
            bb.position(8); // Move to offset 10 (2 + 8)
            int offset = bb.getInt();

            return Math.abs(bmpSize - fileSize) < 100 && offset < fileSize;
        }
        return false;
    }

    private static boolean isTiff(byte[] header, long fileSize) {
        if ((header[0] == 'I' && header[1] == 'I') || (header[0] == 'M' && header[1] == 'M')) {
            ByteOrder order = (header[0] == 'I') ? ByteOrder.LITTLE_ENDIAN : ByteOrder.BIG_ENDIAN;
            ByteBuffer bb = ByteBuffer.wrap(header, 2, 6).order(order);
            short version = bb.getShort();
            if (version == 42) {
                int ifdOffset = bb.getInt();
                return ifdOffset < fileSize;
            }
        }
        return false;
    }

    private static boolean startsWith(byte[] source, byte[] target) {
        if (source.length < target.length) return false;
        for (int i = 0; i < target.length; i++) {
            if (source[i] != target[i]) return false;
        }
        return true;
    }
}