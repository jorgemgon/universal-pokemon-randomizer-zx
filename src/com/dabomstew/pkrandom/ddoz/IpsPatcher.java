package com.dabomstew.pkrandom.ddoz;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Minimal IPS / IPS32 applier used to put the Faster FireRed base patch on the player's own ROM
 * before randomization. The patch is a diff; no ROM data is bundled with this project.
 */
public final class IpsPatcher {

    private IpsPatcher() {
    }

    public static byte[] apply(byte[] source, byte[] patch) {
        if (source == null || source.length == 0) {
            throw new IllegalArgumentException("Source ROM is empty");
        }
        if (!startsWith(patch, "PATCH") && !startsWith(patch, "IPS32")) {
            throw new IllegalArgumentException("Not an IPS patch (missing PATCH/IPS32 header)");
        }
        boolean ips32 = startsWith(patch, "IPS32");
        byte[] out = source.clone();
        int pos = 5;
        int length = patch.length;

        while (pos + 3 <= length) {
            if (startsWith(patch, pos, "EEOF")) {
                if (pos + 7 <= length) {
                    int finalSize = ((patch[pos + 3] & 0xff) << 24) | ((patch[pos + 4] & 0xff) << 16)
                            | ((patch[pos + 5] & 0xff) << 8) | (patch[pos + 6] & 0xff);
                    if (finalSize > out.length) {
                        throw new IllegalArgumentException("IPS truncation larger than the source ROM");
                    }
                    if (finalSize < out.length) {
                        byte[] truncated = new byte[finalSize];
                        System.arraycopy(out, 0, truncated, 0, finalSize);
                        out = truncated;
                    }
                }
                return out;
            }
            if (startsWith(patch, pos, "EOF")) {
                return out;
            }

            int offset;
            if (ips32) {
                if (pos + 6 > length) {
                    throw new IllegalArgumentException("Truncated IPS32 record");
                }
                offset = ((patch[pos] & 0xff) << 24) | ((patch[pos + 1] & 0xff) << 16)
                        | ((patch[pos + 2] & 0xff) << 8) | (patch[pos + 3] & 0xff);
                pos += 4;
            } else {
                if (pos + 5 > length) {
                    throw new IllegalArgumentException("Truncated IPS record");
                }
                offset = ((patch[pos] & 0xff) << 16) | ((patch[pos + 1] & 0xff) << 8) | (patch[pos + 2] & 0xff);
                pos += 3;
            }

            int size = ((patch[pos] & 0xff) << 8) | (patch[pos + 1] & 0xff);
            pos += 2;

            if (size == 0) {
                if (pos + 3 > length) {
                    throw new IllegalArgumentException("Truncated IPS RLE record");
                }
                int rleSize = ((patch[pos] & 0xff) << 8) | (patch[pos + 1] & 0xff);
                int value = patch[pos + 2] & 0xff;
                pos += 3;
                requireInBounds(offset, rleSize, out.length);
                for (int i = 0; i < rleSize; i++) {
                    out[offset + i] = (byte) value;
                }
            } else {
                if (pos + size > length) {
                    throw new IllegalArgumentException("Truncated IPS data record");
                }
                requireInBounds(offset, size, out.length);
                System.arraycopy(patch, pos, out, offset, size);
                pos += size;
            }
        }
        throw new IllegalArgumentException("IPS patch has no EOF marker");
    }

    public static void applyToFile(Path source, Path patchFile, Path target) throws IOException {
        byte[] sourceBytes = Files.readAllBytes(source);
        byte[] patchBytes = Files.readAllBytes(patchFile);
        Files.write(target, apply(sourceBytes, patchBytes));
    }

    private static void requireInBounds(int offset, int size, int length) {
        if (offset < 0 || size < 0 || offset + size > length) {
            throw new IllegalArgumentException(
                    String.format("IPS write out of bounds: offset=%d size=%d rom=%d", offset, size, length));
        }
    }

    private static boolean startsWith(byte[] data, String text) {
        return startsWith(data, 0, text);
    }

    private static boolean startsWith(byte[] data, int offset, String text) {
        if (data == null || offset + text.length() > data.length) {
            return false;
        }
        for (int i = 0; i < text.length(); i++) {
            if (data[offset + i] != text.charAt(i)) {
                return false;
            }
        }
        return true;
    }
}
