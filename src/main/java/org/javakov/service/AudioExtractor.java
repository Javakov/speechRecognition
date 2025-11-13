package org.javakov.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import ws.schild.jave.Encoder;
import ws.schild.jave.EncoderException;
import ws.schild.jave.MultimediaObject;
import ws.schild.jave.encode.AudioAttributes;
import ws.schild.jave.encode.EncodingAttributes;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Сервис для извлечения и преобразования аудио из медиафайлов.
 * Использует Jave2 (встроенный FFmpeg).
 */
public class AudioExtractor {
    private static final Logger logger = LoggerFactory.getLogger(AudioExtractor.class);

    private static final int SAMPLE_RATE = 16000; // Vosk требует 16kHz
    private static final int CHANNELS = 1; // Моно
    private static final int BITRATE = 256000; // 256 kbps

    /**
     * Извлекает аудио из видео/аудио файла и конвертирует в формат для распознавания
     *
     * @param inputFile входной файл (видео или аудио)
     * @return путь к обработанному WAV файлу
     */
    public Path extractAudio(Path inputFile) throws IOException {
        logger.info("Извлечение аудио из файла: {}", inputFile);

        try {
            Path outputFile = Files.createTempFile("audio_", ".wav");

            // Настройка аудио атрибутов
            AudioAttributes audio = new AudioAttributes();
            audio.setCodec("pcm_s16le");
            audio.setChannels(CHANNELS);
            audio.setSamplingRate(SAMPLE_RATE);
            audio.setBitRate(BITRATE);

            // Настройка кодирования
            EncodingAttributes attrs = new EncodingAttributes();
            attrs.setOutputFormat("wav");
            attrs.setAudioAttributes(audio);

            // Конвертация
            Encoder encoder = new Encoder();
            MultimediaObject source = new MultimediaObject(inputFile.toFile());
            encoder.encode(source, outputFile.toFile(), attrs);

            logger.info("Аудио извлечено и сохранено: {}", outputFile);
            return outputFile;

        } catch (EncoderException e) {
            logger.error("Ошибка при извлечении аудио", e);
            throw new IOException("Не удалось извлечь аудио из файла: " + e.getMessage(), e);
        }
    }
}

