package org.javakov.service;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.javakov.model.TranscriptionSegment;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.vosk.LibVosk;
import org.vosk.LogLevel;
import org.vosk.Model;
import org.vosk.Recognizer;

import javax.sound.sampled.AudioInputStream;
import javax.sound.sampled.AudioSystem;
import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * Сервис для распознавания речи с использованием Vosk
 */
public class SpeechRecognitionService {
    private static final Logger logger = LoggerFactory.getLogger(SpeechRecognitionService.class);
    private static final int SAMPLE_RATE = 16000;
    private static final int BUFFER_SIZE = 4000;

    private final Model model;

    public SpeechRecognitionService(Path modelPath) throws IOException {
        LibVosk.setLogLevel(LogLevel.WARNINGS);
        logger.info("Загрузка модели из: {}", modelPath);
        this.model = new Model(modelPath.toString());
        logger.info("Модель успешно загружена");
    }

    /**
     * Распознает речь из аудио файла
     *
     * @param audioFile путь к WAV файлу (16kHz, моно)
     * @return список сегментов транскрипции с временными метками
     */
    public List<TranscriptionSegment> recognizeSpeech(Path audioFile) throws Exception {
        logger.info("Начало распознавания речи из файла: {}", audioFile);

        List<TranscriptionSegment> segments = new ArrayList<>();

        try (AudioInputStream ais = AudioSystem.getAudioInputStream(audioFile.toFile())) {
            Recognizer recognizer = new Recognizer(model, SAMPLE_RATE);
            recognizer.setWords(true);

            byte[] buffer = new byte[BUFFER_SIZE];
            int bytesRead;

            while ((bytesRead = ais.read(buffer)) != -1) {
                if (recognizer.acceptWaveForm(buffer, bytesRead)) {
                    String result = recognizer.getResult();
                    processResult(result, segments);
                }
            }

            // Обработка последнего фрагмента
            String finalResult = recognizer.getFinalResult();
            processResult(finalResult, segments);

            recognizer.close();
        }

        logger.info("Распознавание завершено. Найдено {} сегментов", segments.size());
        return segments;
    }

    /**
     * Обрабатывает результат распознавания и извлекает сегменты с временными метками
     */
    private void processResult(String jsonResult, List<TranscriptionSegment> segments) {
        try {
            JsonObject json = JsonParser.parseString(jsonResult).getAsJsonObject();

            if (!json.has("result")) {
                return;
            }

            JsonArray results = json.getAsJsonArray("result");

            // Группируем слова в предложения по паузам
            List<WordInfo> words = new ArrayList<>();
            for (int i = 0; i < results.size(); i++) {
                JsonObject word = results.get(i).getAsJsonObject();
                if (word.has("word") && word.has("start") && word.has("end")) {
                    words.add(new WordInfo(
                            word.get("word").getAsString(),
                            word.get("start").getAsDouble(),
                            word.get("end").getAsDouble()
                    ));
                }
            }

            // Создаем сегменты (группируем по 10 слов или по длительности)
            if (!words.isEmpty()) {
                segments.addAll(createSegments(words));
            }

        } catch (Exception e) {
            logger.error("Ошибка обработки результата распознавания", e);
        }
    }

    /**
     * Группирует слова в сегменты для субтитров
     */
    private List<TranscriptionSegment> createSegments(List<WordInfo> words) {
        List<TranscriptionSegment> segments = new ArrayList<>();

        int maxWordsPerSegment = 10;
        double maxSegmentDuration = 7.0; // максимум 7 секунд на сегмент

        int i = 0;
        while (i < words.size()) {
            StringBuilder text = new StringBuilder();
            double startTime = words.get(i).start;
            double endTime = words.get(i).end;
            int wordCount = 0;

            while (i < words.size() && wordCount < maxWordsPerSegment) {
                WordInfo word = words.get(i);

                // Проверяем длительность сегмента
                if (word.end - startTime > maxSegmentDuration && wordCount > 0) {
                    break;
                }

                if (!text.isEmpty()) {
                    text.append(" ");
                }
                text.append(word.word);
                endTime = word.end;
                wordCount++;
                i++;
            }

            if (!text.isEmpty()) {
                segments.add(new TranscriptionSegment(startTime, endTime, text.toString()));
            }
        }

        return segments;
    }

    public void close() {
        if (model != null) {
            model.close();
        }
    }

    /**
     * Вспомогательный класс для хранения информации о слове
     */
    private record WordInfo(
            String word,
            double start,
            double end
    ) {
    }
}


