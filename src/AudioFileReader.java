import java.io.*;

public class AudioFileReader {
    /**
     * Audio File Format Detector
     *
     * This program reads an audio file and identifies its format by examining internal
     * binary signatures (magic bytes), not the file extension.
     *
     * Supported formats: WAV, FLAC, OGG, MP3, AAC, WMA.
     *
     * The detection logic follows a container/chunk-walk approach:
     * - First 10 KB of the file is read into a buffer.
     * - Signature bytes are checked sequentially using a chain of if-else conditions.
     * - Each format has a unique magic bytes pattern at the start of the file:
     *   - WAV: "RIFF" at offset 0 + "WAVE" at offset 8
     *   - FLAC: "fLaC" at offset 0
     *   - OGG: "OggS" at offset 0
     *   - MP3: "ID3" (ID3v2 tag) or MPEG sync word 0xFFEx
     *   - AAC: "ADIF" or ADTS sync word 0xFFFx
     *   - WMA: ASF Header Object GUID (16 bytes)
     *
     * Once detected, the program prints "Format: [name]" and terminates.
     * No additional header details (sample rate, channels, etc.) are displayed.
     */
    public static void main(String[] args) {

        String filename = "/home/dwieb/Downloads/image/sample1.wma";

        try (FileInputStream fis = new FileInputStream(filename)) {
            byte[] buffer = new byte[10240];
            int bytesRead = fis.read(buffer);
            if (bytesRead < 4) {
                System.out.println("File too small");
                return;
            }
            byte[] header = buffer;


            if (isWav(header)) {
                System.out.println("Format: WAV");
            } else if (isFlac(header)) {
                System.out.println("Format: FLAC");
            } else if (isOgg(header)) {
                System.out.println("Format: OGG");
            } else if (isMp3(header)) {
                System.out.println("Format: MP3");
            } else if (isAac(header)) {
                System.out.println("Format: AAC");
            } else if (isWma(header)) {
                System.out.println("Format: WMA");
            } else {
                System.out.println("Format: Unknown");
            }

        } catch (IOException e) {
            System.err.println("Error reading file: " + e.getMessage());
        }
    }


    private static boolean isWav(byte[] b) {
        if (b.length < 12) return false;
        return b[0]=='R' && b[1]=='I' && b[2]=='F' && b[3]=='F'
                && b[8]=='W' && b[9]=='A' && b[10]=='V' && b[11]=='E';
    }

    private static boolean isFlac(byte[] b) {
        if (b.length < 4) return false;
        return b[0]=='f' && b[1]=='L' && b[2]=='a' && b[3]=='C';
    }

    private static boolean isOgg(byte[] b) {
        if (b.length < 4) return false;
        return b[0]=='O' && b[1]=='g' && b[2]=='g' && b[3]=='S';
    }

    private static boolean isMp3(byte[] b) {
        if (b.length < 4) return false;

        if (b[0]=='I' && b[1]=='D' && b[2]=='3') {
            int tagSize = synchSafeInt(b[6], b[7], b[8], b[9]) + 10;
            if (tagSize + 2 < b.length) {
                for (int i = tagSize; i < b.length - 1; i++) {
                    if ((b[i] & 0xFF) == 0xFF && (b[i+1] & 0xE0) == 0xE0)
                        return true;
                }
            }
            return false;
        }

        return (b[0] & 0xFF) == 0xFF && (b[1] & 0xE0) == 0xE0;
    }

    private static boolean isAac(byte[] b) {
        if (b.length < 2) return false;
        if (b.length >= 4 && b[0]=='A' && b[1]=='D' && b[2]=='I' && b[3]=='F')
            return true;
        return (b[0] & 0xFF) == 0xFF && (b[1] & 0xF0) == 0xF0;
    }

    private static boolean isWma(byte[] b) {
        if (b.length < 16) return false;
        byte[] asfGuid = {0x30,0x26,(byte)0xB2,0x75,(byte)0x8E,0x66,(byte)0xCF,0x11,
                (byte)0xA6,(byte)0xD9,0x00,(byte)0xAA,0x00,0x62,(byte)0xCE,0x6C};
        for (int i=0; i<16; i++) {
            if (b[i] != asfGuid[i]) return false;
        }
        return true;
    }

    private static int synchSafeInt(byte b1, byte b2, byte b3, byte b4) {
        return ((b1 & 0xFF) << 21) | ((b2 & 0xFF) << 14) | ((b3 & 0xFF) << 7) | (b4 & 0xFF);
    }
}