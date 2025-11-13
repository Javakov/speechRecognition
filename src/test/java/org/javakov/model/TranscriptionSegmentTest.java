package org.javakov.model;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Тесты для модели TranscriptionSegment
 */
class TranscriptionSegmentTest {

    @Test
    void testSegmentCreation() {
        TranscriptionSegment segment = new TranscriptionSegment(1.5, 3.2, "Test text");
        
        assertEquals(1.5, segment.startTime());
        assertEquals(3.2, segment.endTime());
        assertEquals("Test text", segment.text());
    }

    @Test
    void testToString() {
        TranscriptionSegment segment = new TranscriptionSegment(1.0, 2.5, "Test");
        
        String expected = "[1.00 - 2.50] Test";
        assertEquals(expected, segment.toString());
    }
}


