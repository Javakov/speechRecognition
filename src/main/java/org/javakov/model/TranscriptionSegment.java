package org.javakov.model;

import org.jetbrains.annotations.NotNull;

import java.util.Locale;

/**
 * Сегмент транскрипции с временными метками
 */
public record TranscriptionSegment(
        double startTime,
        double endTime, 
        String text
) {
    @NotNull
    @Override
    public String toString() {
        return String.format(Locale.US, "[%.2f - %.2f] %s", startTime, endTime, text);
    }
}

