import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

public class AudioParser {
    private final String filepath;
    private final long fileSize;

    public AudioParser(String filepath) {
        this.filepath = filepath;
        this.fileSize = new File(filepath).length();
    }

    public Map<String, Object> parse() throws IOException {
        try (RandomAccessFile f = new RandomAccessFile(filepath, "r")) {
            byte[] header = new byte[16];
            f.readFully(header);

            // 1. WAV / RIFF
            if (startsWith(header, "RIFF".getBytes()) && sliceEquals(header, 8, "WAVE".getBytes())) {
                return parseRiff(f);
            }
            // 2. FLAC
            else if (startsWith(header, "fLaC".getBytes())) {
                return parseFlac(f);
            }
            // 3. OGG
            else if (startsWith(header, "OggS".getBytes())) {
                return parseOgg(f);
            }
            // 4. WMA (ASF GUID)
            else if (startsWith(header, new byte[]{(byte) 0x30, 0x26, (byte) 0xB2, 0x75, (byte) 0x8E, 0x66, (byte) 0xCF, 0x11, (byte) 0xA6, (byte) 0xD9, 0x00, (byte) 0xAA, 0x00, 0x62, (byte) 0xCE, 0x6C})) {
                return parseAsf(f);
            }
            // 5. MP3
            else if (startsWith(header, "ID3".getBytes()) || ((header[0] & 0xFF) == 0xFF && (header[1] & 0xE0) == 0xE0)) {
                return parseMp3(f);
            }
            // 6. AAC
            else if (startsWith(header, "ADIF".getBytes()) || ((header[0] & 0xFF) == 0xFF && (header[1] & 0xF0) == 0xF0)) {
                return parseAac(f);
            }
        }
        Map<String, Object> result = new HashMap<>();
        result.put("Format", "Unknown Format");
        return result;
    }

    private Map<String, Object> parseRiff(RandomAccessFile f) throws IOException {
        f.seek(0);
        byte[] riffHeader = new byte[12];
        f.readFully(riffHeader);

        ByteBuffer bb = ByteBuffer.wrap(riffHeader).order(ByteOrder.LITTLE_ENDIAN);
        bb.getInt(); // Skip RIFF
        int size = bb.getInt();

        Map<String, Object> results = new HashMap<>();
        results.put("Format", "WAV");
        results.put("ChunkSize", Integer.toUnsignedLong(size));

        while (f.getFilePointer() < fileSize) {
            byte[] chunkHead = new byte[8];
            if (f.read(chunkHead) < 8) break;

            String cid = new String(Arrays.copyOfRange(chunkHead, 0, 4));
            int csize = ByteBuffer.wrap(chunkHead, 4, 4).order(ByteOrder.LITTLE_ENDIAN).getInt();

            if (cid.equals("fmt ")) {
                byte[] data = new byte[16];
                f.readFully(data);
                ByteBuffer fmtBb = ByteBuffer.wrap(data).order(ByteOrder.LITTLE_ENDIAN);
                results.put("AudioFormat", fmtBb.getShort() & 0xFFFF);
                results.put("Channels", fmtBb.getShort() & 0xFFFF);
                results.put("SampleRate", fmtBb.getInt() & 0xFFFFFFFFL);
                results.put("ByteRate", fmtBb.getInt() & 0xFFFFFFFFL);
                results.put("BlockAlign", fmtBb.getShort() & 0xFFFF);
                results.put("BitsPerSample", fmtBb.getShort() & 0xFFFF);
                if (csize > 16) f.skipBytes(csize - 16);
            } else {
                f.skipBytes(csize);
            }
        }
        return results;
    }

    private Map<String, Object> parseFlac(RandomAccessFile f) throws IOException {
        f.seek(4);
        Map<String, Object> results = new HashMap<>();
        results.put("Format", "FLAC");

        boolean lastBlock = false;
        while (!lastBlock) {
            byte[] header = new byte[4];
            f.readFully(header);
            lastBlock = (header[0] & 0x80) != 0;
            int blockType = header[0] & 0x7F;
            int length = ((header[1] & 0xFF) << 16) | ((header[2] & 0xFF) << 8) | (header[3] & 0xFF);

            if (blockType == 0) { // STREAMINFO
                byte[] data = new byte[length];
                f.readFully(data);
                int srate = ((data[10] & 0xFF) << 12) | ((data[11] & 0xFF) << 4) | ((data[12] & 0xF0) >> 4);
                int channels = ((data[12] & 0x0E) >> 1) + 1;
                results.put("SampleRate", srate);
                results.put("Channels", channels);
                return results;
            }
            f.skipBytes(length);
        }
        return results;
    }

    private Map<String, Object> parseMp3(RandomAccessFile f) throws IOException {
        f.seek(0);
        byte[] header = new byte[10];
        f.readFully(header);
        long offset = 0;

        if (startsWith(header, "ID3".getBytes())) {
            int size = ((header[6] & 0x7F) << 21) | ((header[7] & 0x7F) << 14) | ((header[8] & 0x7F) << 7) | (header[9] & 0x7F);
            offset = size + 10;
        }

        f.seek(offset);
        byte[] frame = new byte[4];
        f.readFully(frame);
        Map<String, Object> results = new HashMap<>();
        if ((frame[0] & 0xFF) == 0xFF && (frame[1] & 0xE0) == 0xE0) {
            int srateIdx = (frame[2] & 0x0C) >> 2;
            int[] rates = {44100, 48000, 32000, 0};
            results.put("Format", "MP3");
            results.put("SampleRate", rates[srateIdx]);
        } else {
            results.put("Format", "MP3 (Header not found)");
        }
        return results;
    }

    private Map<String, Object> parseAsf(RandomAccessFile f) throws IOException {
        f.seek(30);
        Map<String, Object> results = new HashMap<>();
        results.put("Format", "WMA");

        byte[] streamGuid = new byte[]{(byte) 0x91, 0x07, (byte) 0xDC, (byte) 0xB7, (byte) 0xB7, (byte) 0xA9, (byte) 0xCF, 0x11, (byte) 0x8E, (byte) 0xE6, 0x00, (byte) 0xC0, 0x0C, 0x20, 0x53, 0x65};

        while (f.getFilePointer() < fileSize) {
            byte[] guid = new byte[16];
            if (f.read(guid) < 16) break;
            byte[] sizeBytes = new byte[8];
            f.readFully(sizeBytes);
            long objSize = ByteBuffer.wrap(sizeBytes).order(ByteOrder.LITTLE_ENDIAN).getLong();

            if (Arrays.equals(guid, streamGuid)) {
                f.skipBytes(54);
                byte[] srBytes = new byte[4];
                f.readFully(srBytes);
                results.put("SampleRate", ByteBuffer.wrap(srBytes).order(ByteOrder.LITTLE_ENDIAN).getInt() & 0xFFFFFFFFL);
                return results;
            }
            f.skipBytes((int) objSize - 24);
        }
        return results;
    }

    private Map<String, Object> parseOgg(RandomAccessFile f) throws IOException {
        f.seek(26);
        int segments = f.readUnsignedByte();
        f.skipBytes(segments);
        byte[] type = new byte[7];
        f.readFully(type);

        Map<String, Object> results = new HashMap<>();
        if (new String(type).contains("vorbis")) {
            f.skipBytes(4);
            results.put("Format", "OGG (Vorbis)");
            results.put("Channels", f.readUnsignedByte());
            byte[] sr = new byte[4];
            f.readFully(sr);
            results.put("SampleRate", ByteBuffer.wrap(sr).order(ByteOrder.LITTLE_ENDIAN).getInt() & 0xFFFFFFFFL);
        } else {
            results.put("Format", "OGG");
        }
        return results;
    }

    private Map<String, Object> parseAac(RandomAccessFile f) throws IOException {
        f.seek(0);
        byte[] head = new byte[7];
        f.readFully(head);
        Map<String, Object> results = new HashMap<>();

        if (startsWith(head, "ADIF".getBytes())) {
            results.put("Format", "AAC (ADIF)");
        } else if ((head[0] & 0xFF) == 0xFF && (head[1] & 0xF0) == 0xF0) {
            int[] freqs = {96000, 88200, 64000, 48000, 44100, 32000, 24000, 22050, 16000, 12000, 11025, 8000, 7350};
            int idx = (head[2] & 0x3C) >> 2;
            results.put("Format", "AAC (ADTS)");
            results.put("SampleRate", idx < freqs.length ? freqs[idx] : "Unknown");
        }
        return results;
    }

    // Helper methods for byte comparisons
    private boolean startsWith(byte[] source, byte[] match) {
        if (source.length < match.length) return false;
        for (int i = 0; i < match.length; i++) {
            if (source[i] != match[i]) return false;
        }
        return true;
    }

    private boolean sliceEquals(byte[] source, int offset, byte[] match) {
        if (source.length < offset + match.length) return false;
        for (int i = 0; i < match.length; i++) {
            if (source[offset + i] != match[i]) return false;
        }
        return true;
    }

    public static void main(String[] args) {
        String testFile = "/home/dwieb/Downloads/image/sample3.aac";
        try {
            AudioParser parser = new AudioParser(testFile);
            Map<String, Object> info = parser.parse();
            System.out.println("--- Analysis for: " + testFile + " ---");
            info.forEach((k, v) -> System.out.println(k + ": " + v));
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}