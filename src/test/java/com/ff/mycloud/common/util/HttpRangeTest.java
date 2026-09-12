package com.ff.mycloud.common.util;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class HttpRangeTest {
    @Test
    void parsesClosedRange() {
        assertEquals(new HttpRange(100, 199), HttpRange.parse("bytes=100-199", 1000));
    }

    @Test
    void parsesOpenEndedRange() {
        assertEquals(new HttpRange(900, 999), HttpRange.parse("bytes=900-", 1000));
    }

    @Test
    void parsesSuffixRange() {
        assertEquals(new HttpRange(900, 999), HttpRange.parse("bytes=-100", 1000));
    }

    @Test
    void clampsEndToObjectSize() {
        assertEquals(new HttpRange(900, 999), HttpRange.parse("bytes=900-2000", 1000));
    }

    @Test
    void rejectsOutOfBoundsAndMultipleRanges() {
        assertThrows(IllegalArgumentException.class, () -> HttpRange.parse("bytes=1000-", 1000));
        assertThrows(IllegalArgumentException.class, () -> HttpRange.parse("bytes=0-1,4-5", 1000));
    }
}
