package com.examprep.common.util;

import java.nio.ByteBuffer;
import java.security.SecureRandom;
import java.util.UUID;

/**
 * RFC 9562 UUID version 7 generator: a 48-bit Unix-millisecond timestamp followed by
 * 74 random bits.
 *
 * <p>Why v7 instead of random v4: the ids sort roughly by creation time, so B-tree
 * primary-key inserts land on the right-most pages instead of random ones. That cuts
 * page splits and WAL volume on hot tables such as attempts and results. The ids stay
 * globally unique and non-enumerable.
 */
public final class Uuids {

    private static final SecureRandom RANDOM = new SecureRandom();

    private Uuids() {
    }

    public static UUID v7() {
        long timestamp = System.currentTimeMillis();
        byte[] random = new byte[10];
        RANDOM.nextBytes(random);

        // MSB: 48 bits timestamp | 4 bits version (0111) | 12 bits random
        long msb = (timestamp << 16)
                | 0x7000L
                | ((random[0] & 0x0FL) << 8)
                | (random[1] & 0xFFL);

        // LSB: 2 bits variant (10) | 62 bits random
        long lsb = ByteBuffer.wrap(random, 2, 8).getLong();
        lsb = (lsb & 0x3FFF_FFFF_FFFF_FFFFL) | 0x8000_0000_0000_0000L;

        return new UUID(msb, lsb);
    }
}
