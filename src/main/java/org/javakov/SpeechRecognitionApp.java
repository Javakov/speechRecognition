package org.javakov;

import org.javakov.model.TranscriptionSegment;
import org.javakov.service.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.List;

/**
 * Главный класс приложения для распознавания речи и генерации субтитров
 */
public class SpeechRecognitionApp {
    private static final Logger logger = LoggerFactory.getLogger(SpeechRecognitionApp.class);

    // Дефолтные значения для запуска из IDE
    private static final String DEFAULT_AUDIO_RESOURCE = "/audio_ru_and_en.mp3";

    // Пути к моделям
    private static final String MODEL_RU = "models/vosk-model-small-ru-0.22";
    private static final String MODEL_EN = "models/vosk-model-small-en-us-0.15";

    public static void main(String[] args) {
        if (args.length < 1) {
            logger.info("Аргументы не переданы, используются дефолтные значения для тестирования");
            args = getDefaultArgs();
        }

        if (args.length < 1) {
            printUsage();
            System.exit(1);
        }

        try {
            String inputFilePath = args[0];

            // Проверяем, является ли это ресурсом
            Path inputFile;
            if (inputFilePath.startsWith("/") || inputFilePath.startsWith("resource:")) {
                // копируем во временную папку
                inputFile = extractResourceToTemp(inputFilePath);
                logger.info("Файл извлечен из ресурсов: {}", inputFile);
            } else {
                inputFile = Paths.get(inputFilePath);
            }

            if (!Files.exists(inputFile)) {
                logger.error("Файл не найден: {}", inputFilePath);
                System.exit(1);
            }

            logger.info("=== Начало обработки файла: {} ===", inputFilePath);

            // Инициализация сервисов
            AudioExtractor audioExtractor = new AudioExtractor();
            SubtitleGenerator subtitleGenerator = new SubtitleGenerator();
            SegmentMerger segmentMerger = new SegmentMerger();

            // Шаг 1: Извлечение аудио
            Path audioFile = extractAudio(audioExtractor, inputFile);

            try {
                // Шаг 2 и 3: Распознавание ОБЕИМИ моделями
                logger.info("Шаг 2/4: Распознавание речи русской моделью");
                List<TranscriptionSegment> segmentsRu = recognizeSpeech(MODEL_RU, audioFile);
                
                logger.info("Шаг 3/4: Распознавание речи английской моделью");
                List<TranscriptionSegment> segmentsEn = recognizeSpeech(MODEL_EN, audioFile);

                // Шаг 4: Объединение сегментов и генерация одного файла субтитров
                generateSubtitles(segmentsRu, segmentsEn, inputFilePath, subtitleGenerator, segmentMerger);

                logger.info("=== Обработка завершена успешно ===");

            } finally {
                // Очистка временного файла
                cleanupTempFile(audioFile);
            }

        } catch (Exception e) {
            logger.error("Критическая ошибка при обработке", e);
            System.exit(1);
        }
    }

    private static Path extractAudio(AudioExtractor extractor, Path inputFile) throws Exception {
        logger.info("Шаг 1/4: Извлечение и преобработка аудио");
        Path audioFile = extractor.extractAudio(inputFile);
        logger.info("Аудио извлечено: {}", audioFile);
        return audioFile;
    }


    private static List<TranscriptionSegment> recognizeSpeech(String modelPath, Path audioFile) throws Exception {
        Path modelDir = Paths.get(modelPath);
        if (!Files.exists(modelDir)) {
            logger.error("Модель не найдена: {}", modelPath);
            logger.error("Скачайте модель с https://alphacephei.com/vosk/models");
            throw new IllegalStateException("Модель не найдена: " + modelPath);
        }

        logger.info("Загрузка модели: {}", modelPath);
        SpeechRecognitionService recognitionService = new SpeechRecognitionService(modelDir);
        try {
            List<TranscriptionSegment> segments = recognitionService.recognizeSpeech(audioFile);
            logger.info("Распознано сегментов: {}", segments.size());
            return segments;
        } finally {
            recognitionService.close();
        }
    }

    private static void generateSubtitles(List<TranscriptionSegment> segmentsRu,
                                          List<TranscriptionSegment> segmentsEn,
                                          String inputFilePath,
                                          SubtitleGenerator generator,
                                          SegmentMerger merger) throws Exception {
        logger.info("Шаг 4/4: Объединение сегментов и генерация субтитров");

        // Объединяем сегменты из обеих моделей
        List<TranscriptionSegment> mergedSegments = merger.mergeSegments(segmentsRu, segmentsEn);

        // Определяем базовое имя для файла субтитров
        String baseName;
        String fileName;

        // Если это файл из ресурсов, создаем субтитры в текущей директории
        if (inputFilePath.startsWith("/") || inputFilePath.startsWith("resource:")) {
            // Извлекаем только имя файла без пути
            String cleanPath = inputFilePath.startsWith("resource:")
                    ? inputFilePath.substring(9)
                    : inputFilePath;
            fileName = cleanPath.substring(cleanPath.lastIndexOf('/') + 1);
            fileName = fileName.substring(0, fileName.lastIndexOf('.'));
            baseName = "output/" + fileName;

            // Создаем папку output если её нет
            Files.createDirectories(Paths.get("output"));
            logger.info("Субтитры будут сохранены в папку: output/");
        } else {
            baseName = inputFilePath.substring(0, inputFilePath.lastIndexOf('.'));
        }

        // Генерируем один объединенный файл субтитров
        Path subtitlesFile = Paths.get(baseName + ".srt");
        
        generator.generateSRT(mergedSegments, subtitlesFile);
        logger.info("Создан файл субтитров: {}", subtitlesFile.toAbsolutePath());
    }

    private static void cleanupTempFile(Path tempFile) {
        try {
            if (tempFile != null && Files.exists(tempFile)) {
                Files.delete(tempFile);
                logger.info("Удален временный файл: {}", tempFile);
            }
        } catch (Exception e) {
            logger.warn("Не удалось удалить временный файл: {}", tempFile, e);
        }
    }

    /**
     * Возвращает дефолтные аргументы для запуска из IDE
     */
    private static String[] getDefaultArgs() {
        try {
            // Проверяем наличие дефолтного ресурса
            InputStream resourceStream = SpeechRecognitionApp.class.getResourceAsStream(DEFAULT_AUDIO_RESOURCE);
            if (resourceStream != null) {
                resourceStream.close();
                logger.info("Используется аудио из ресурсов: {}", DEFAULT_AUDIO_RESOURCE);
                return new String[]{DEFAULT_AUDIO_RESOURCE};
            }
        } catch (Exception e) {
            logger.warn("Не удалось загрузить дефолтный ресурс", e);
        }

        // Если ресурс не найден, возвращаем пустой массив
        logger.warn("Дефолтный аудио файл не найден в ресурсах");
        return new String[0];
    }

    /**
     * Извлекает файл из ресурсов во временную папку
     */
    private static Path extractResourceToTemp(String resourcePath) throws IOException {
        // Убираем префикс "resource:" если есть
        String cleanPath = resourcePath.startsWith("resource:")
                ? resourcePath.substring(9)
                : resourcePath;

        // Получаем расширение файла
        String extension = cleanPath.substring(cleanPath.lastIndexOf('.'));

        // Создаем временный файл
        Path tempFile = Files.createTempFile("resource_audio_", extension);

        // Копируем ресурс во временный файл
        try (InputStream is = SpeechRecognitionApp.class.getResourceAsStream(cleanPath)) {
            if (is == null) {
                throw new IOException("Ресурс не найден: " + cleanPath);
            }
            Files.copy(is, tempFile, StandardCopyOption.REPLACE_EXISTING);
        }

        // Помечаем на удаление при выходе
        tempFile.toFile().deleteOnExit();

        return tempFile;
    }

    private static void printUsage() {
        System.out.println("Использование: java -jar speech-recognition.jar <входной_файл>");
        System.out.println();
        System.out.println("Параметры:");
        System.out.println("  <входной_файл> - путь к видео или аудио файлу");
        System.out.println();
        System.out.println("Программа автоматически распознает файл ОБЕИМИ моделями (русской и английской)");
        System.out.println("и создаст ОДИН объединенный файл субтитров:");
        System.out.println("  - filename.srt (объединенный результат с автоматическим выбором языка)");
        System.out.println();
        System.out.println("Примеры:");
        System.out.println("  java -jar speech-recognition.jar video.mp4");
        System.out.println("  java -jar speech-recognition.jar audio.mp3");
        System.out.println();
        System.out.println("Для запуска из IDE без аргументов:");
        System.out.println("  Аудио: " + DEFAULT_AUDIO_RESOURCE + " (из ресурсов)");
        System.out.println();
        System.out.println("Требуемые модели:");
        System.out.println("  Русский:     " + MODEL_RU);
        System.out.println("  Английский:  " + MODEL_EN);
        System.out.println("  Скачать: https://alphacephei.com/vosk/models");
        System.out.println();
        System.out.println("ВНИМАНИЕ: FFmpeg НЕ требуется! Все встроено в программу.");
    }
}

