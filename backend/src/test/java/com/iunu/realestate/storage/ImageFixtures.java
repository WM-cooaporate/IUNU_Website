package com.iunu.realestate.storage;

import org.springframework.mock.web.MockMultipartFile;

import java.nio.charset.StandardCharsets;

/** Byte-level fixtures: each one starts with the signature its format really has. */
final class ImageFixtures {

    private ImageFixtures() {}

    static byte[] jpeg(int seed) {
        return withPrefix(new byte[]{(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, (byte) 0xE0}, seed);
    }

    static byte[] png(int seed) {
        return withPrefix(new byte[]{(byte) 0x89, 'P', 'N', 'G', '\r', '\n', 0x1A, '\n'}, seed);
    }

    static byte[] webp(int seed) {
        return withPrefix(new byte[]{'R', 'I', 'F', 'F', 0, 0, 0, 0, 'W', 'E', 'B', 'P'}, seed);
    }

    /** What an iPhone writes: a box size, "ftyp", then the "heic" brand. */
    static byte[] heic() {
        return withPrefix(new byte[]{0, 0, 0, 0x18, 'f', 't', 'y', 'p', 'h', 'e', 'i', 'c'}, 0);
    }

    static MockMultipartFile file(String name, String contentType, byte[] content) {
        return new MockMultipartFile("files", name, contentType, content);
    }

    static MockMultipartFile ascii(String name, String contentType, String content) {
        return file(name, contentType, content.getBytes(StandardCharsets.US_ASCII));
    }

    private static byte[] withPrefix(byte[] signature, int seed) {
        byte[] content = new byte[signature.length + 32];
        System.arraycopy(signature, 0, content, 0, signature.length);
        for (int i = signature.length; i < content.length; i++) content[i] = (byte) (seed + i);
        return content;
    }
}
