package org.javakov.service;

import org.javakov.model.TranscriptionSegment;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

/**
 * Генератор субтитров в формате SRT
 */
public class SubtitleGenerator {
    private static final Logger logger = LoggerFactory.getLogger(SubtitleGenerator.class);

    /**
     * Генерирует файл субтитров в формате SRT
     *
     * @param segments       список сегментов транскрипции
     * @param outputPath     путь для сохранения файла
     */
    public void generateSRT(List<TranscriptionSegment> segments, Path outputPath)
            throws IOException {
        logger.info("Генерация SRT файла: {}", outputPath);

        StringBuilder srt = new StringBuilder();

        for (int i = 0; i < segments.size(); i++) {
            TranscriptionSegment segment = segments.get(i);

            // Номер субтитра
            srt.append(i + 1).append("\n");

            // Временные метки
            srt.append(formatTime(segment.startTime()))
                    .append(" --> ")
                    .append(formatTime(segment.endTime()))
                    .append("\n");

            // Текст
            String text = segment.text();
            srt.append(text).append("\n");

            // Пустая строка между субтитрами
            srt.append("\n");
        }

        Files.writeString(outputPath, srt.toString(), StandardCharsets.UTF_8);
        logger.info("SRT файл сохранен: {}", outputPath);
    }

    /**
     * Форматирует время в формат SRT: HH:MM:SS,mmm
     *
     * @param seconds время в секундах
     * @return отформатированная строка времени
     */
    private String formatTime(double seconds) {
        int hours = (int) (seconds / 3600);
        int minutes = (int) ((seconds % 3600) / 60);
        int secs = (int) (seconds % 60);
        int millis = (int) ((seconds - Math.floor(seconds)) * 1000);

        return String.format("%02d:%02d:%02d,%03d", hours, minutes, secs, millis);
    }
}


