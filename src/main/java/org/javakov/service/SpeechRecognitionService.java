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
 * Сервис для распознавания речи с использованием библиотеки Vosk.
 * <p>
 * Основные функции:
 * <ul>
 *   <li>Загрузка модели распознавания речи (русской или английской)</li>
 *   <li>Распознавание речи из аудио файла</li>
 *   <li>Группировка слов в сегменты на основе пауз между словами</li>
 * </ul>
 * <p>
 * Алгоритм распознавания:
 * <ol>
 *   <li>Загружает аудио файл в формате WAV (16kHz, моно)</li>
 *   <li>Обрабатывает аудио порциями через Vosk Recognizer</li>
 *   <li>Накапливает все слова из всех порций результатов</li>
 *   <li>Создает сегменты на основе пауз между словами (пауза > 0.5 сек = граница сегмента)</li>
 * </ol>
 * <p>
 * Важно: Сегменты создаются на основе пауз, а не по фиксированному количеству слов.
 * Это позволяет правильно разделять предложения на разных языках.
 */
public class SpeechRecognitionService {
    private static final Logger logger = LoggerFactory.getLogger(SpeechRecognitionService.class);
    
    /** Частота дискретизации аудио для Vosk (16kHz) */
    private static final int SAMPLE_RATE = 16000;
    
    /** Размер буфера для чтения аудио данных */
    private static final int BUFFER_SIZE = 4000;

    /** Модель распознавания речи Vosk */
    private final Model model;

    public SpeechRecognitionService(Path modelPath) throws IOException {
        LibVosk.setLogLevel(LogLevel.WARNINGS);
        logger.info("Загрузка модели из: {}", modelPath);
        this.model = new Model(modelPath.toString());
        logger.info("Модель успешно загружена");
    }

    /**
     * Распознает речь из аудио файла и возвращает список сегментов с временными метками.
     * <p>
     * Процесс распознавания:
     * <ol>
     *   <li>Открывает аудио файл в формате WAV (16kHz, моно)</li>
     *   <li>Создает Vosk Recognizer с загруженной моделью</li>
     *   <li>Читает аудио порциями и передает в recognizer</li>
     *   <li>Накапливает все слова из всех порций результатов</li>
     *   <li>Создает сегменты на основе пауз между словами</li>
     * </ol>
     * <p>
     * Важно: Все слова накапливаются в один список перед созданием сегментов.
     * Это необходимо для правильного определения пауз между словами из разных порций.
     *
     * @param audioFile путь к WAV файлу (16kHz, моно)
     * @return список сегментов транскрипции с временными метками
     * @throws Exception если произошла ошибка при чтении файла или распознавании
     */
    public List<TranscriptionSegment> recognizeSpeech(Path audioFile) throws Exception {
        logger.info("Начало распознавания речи из файла: {}", audioFile);

        // Накапливаем все слова из всех порций результатов
        // Это важно: слова приходят порциями, но нам нужен полный список для определения пауз
        List<WordInfo> allWords = new ArrayList<>();

        try (AudioInputStream ais = AudioSystem.getAudioInputStream(audioFile.toFile())) {
            // Создаем распознаватель с загруженной моделью
            Recognizer recognizer = new Recognizer(model, SAMPLE_RATE);
            recognizer.setWords(true); // Включаем вывод информации о словах с временными метками

            byte[] buffer = new byte[BUFFER_SIZE];
            int bytesRead;

            // Читаем аудио порциями и передаем в распознаватель
            while ((bytesRead = ais.read(buffer)) != -1) {
                // acceptWaveForm возвращает true, когда распознаватель готов выдать результат
                if (recognizer.acceptWaveForm(buffer, bytesRead)) {
                    String result = recognizer.getResult();
                    // Извлекаем слова из JSON результата и добавляем в список
                    extractWords(result, allWords);
                }
            }

            // Обработка последнего фрагмента (который еще не был обработан)
            // getFinalResult возвращает финальный результат, даже если recognizer еще не готов
            String finalResult = recognizer.getFinalResult();
            extractWords(finalResult, allWords);

            recognizer.close();
        }

        // Создаем сегменты на основе пауз между словами
        // Пауза > 0.5 сек означает границу нового сегмента (новое предложение)
        List<TranscriptionSegment> segments = createSegmentsByPauses(allWords);
        logger.info("Распознавание завершено. Найдено {} слов, создано {} сегментов", 
                    allWords.size(), segments.size());
        return segments;
    }

    /**
     * Извлекает слова из JSON результата распознавания Vosk и добавляет их в список.
     * <p>
     * Vosk возвращает результаты в формате JSON:
     * <pre>
     * {
     *   "result": [
     *     {"word": "hello", "start": 0.5, "end": 0.8},
     *     {"word": "world", "start": 0.9, "end": 1.2}
     *   ]
     * }
     * </pre>
     * <p>
     * Метод парсит JSON и извлекает каждое слово с его временными метками.
     *
     * @param jsonResult JSON строка с результатом распознавания от Vosk
     * @param words список для добавления извлеченных слов
     */
    private void extractWords(String jsonResult, List<WordInfo> words) {
        try {
            JsonObject json = JsonParser.parseString(jsonResult).getAsJsonObject();

            // Проверяем наличие поля "result" в JSON
            if (!json.has("result")) {
                return;
            }

            JsonArray results = json.getAsJsonArray("result");

            // Извлекаем каждое слово из массива результатов
            for (int i = 0; i < results.size(); i++) {
                JsonObject word = results.get(i).getAsJsonObject();
                // Проверяем наличие всех необходимых полей: слово, начало, конец
                if (word.has("word") && word.has("start") && word.has("end")) {
                    words.add(new WordInfo(
                            word.get("word").getAsString(),
                            word.get("start").getAsDouble(),
                            word.get("end").getAsDouble()
                    ));
                }
            }

        } catch (Exception e) {
            logger.error("Ошибка извлечения слов из результата распознавания", e);
        }
    }

    /**
     * Группирует слова в сегменты на основе пауз между словами.
     * <p>
     * Это ключевой метод для правильного разделения речи на сегменты.
     * Вместо фиксированного количества слов, сегменты создаются на основе пауз,
     * что позволяет правильно разделять предложения на разных языках.
     * <p>
     * Алгоритм:
     * <ol>
     *   <li>Проходит по всем словам в хронологическом порядке</li>
     *   <li>Вычисляет паузу между каждыми двумя соседними словами</li>
     *   <li>Если пауза > порога (0.5 сек) - это граница нового сегмента</li>
     *   <li>Если сегмент слишком длинный (> 10 сек) - разбивает его</li>
     *   <li>Создает сегмент из всех слов между границами</li>
     * </ol>
     * <p>
     * Пример:
     * <ul>
     *   <li>Слова: "Hello" (0-0.5), "world" (0.6-1.0), "How" (2.0-2.3), "are" (2.4-2.6)</li>
     *   <li>Пауза между "world" и "How": 2.0 - 1.0 = 1.0 сек > 0.5 сек</li>
     *   <li>Результат: 2 сегмента - ["Hello world"] и ["How are"]</li>
     * </ul>
     *
     * @param words список всех слов с временными метками
     * @return список сегментов, разделенных по паузам
     */
    private List<TranscriptionSegment> createSegmentsByPauses(List<WordInfo> words) {
        if (words.isEmpty()) {
            return new ArrayList<>();
        }

        List<TranscriptionSegment> segments = new ArrayList<>();

        // Пауза в 0.5 секунды означает границу нового сегмента (новое предложение)
        double pauseThreshold = 0.5;
        
        // Максимальная длительность сегмента (на случай очень длинных предложений без пауз)
        double maxSegmentDuration = 10.0;

        // Индекс начала текущего сегмента и его время начала
        int segmentStart = 0;
        double segmentStartTime = words.getFirst().start;

        // Проходим по всем словам и ищем границы сегментов
        for (int i = 1; i < words.size(); i++) {
            WordInfo prevWord = words.get(i - 1);
            WordInfo currentWord = words.get(i);

            // Вычисляем паузу между словами: начало текущего слова минус конец предыдущего
            double pause = currentWord.start - prevWord.end;

            // Проверяем, нужно ли завершить текущий сегмент
            boolean shouldBreakSegment = false;

            // Пауза больше порога - граница нового сегмента
            // Это означает, что между словами была пауза (конец предложения, пауза для дыхания)
            if (pause > pauseThreshold) {
                shouldBreakSegment = true;
                logger.debug("Обнаружена пауза {}с между словами '{}' и '{}' - граница сегмента", 
                            String.format("%.2f", pause), prevWord.word, currentWord.word);
            }

            // Если сегмент слишком длинный (больше maxSegmentDuration), разбиваем его
            // Это защита от очень длинных предложений без пауз
            if (currentWord.start - segmentStartTime > maxSegmentDuration && i > segmentStart) {
                shouldBreakSegment = true;
            }

            if (shouldBreakSegment) {
                // Создаем сегмент из слов от segmentStart до i-1 (предыдущее слово)
                createSegmentFromWords(words, segmentStart, i - 1, segments);
                // Начинаем новый сегмент с текущего слова
                segmentStart = i;
                segmentStartTime = currentWord.start;
            }
        }

        // Создаем последний сегмент (после цикла остались слова, которые не были обработаны)
        if (segmentStart < words.size()) {
            createSegmentFromWords(words, segmentStart, words.size() - 1, segments);
        }

        return segments;
    }

    /**
     * Создает сегмент из диапазона слов.
     * <p>
     * Объединяет слова от startIndex до endIndex в один сегмент:
     * <ul>
     *   <li>Объединяет текст всех слов через пробел</li>
     *   <li>Время начала берется от первого слова</li>
     *   <li>Время конца берется от последнего слова</li>
     * </ul>
     * <p>
     * Пример:
     * <ul>
     *   <li>Слова: ["Hello", "world", "how", "are", "you"]</li>
     *   <li>startIndex = 0, endIndex = 1</li>
     *   <li>Результат: сегмент "Hello world" с временем от начала "Hello" до конца "world"</li>
     * </ul>
     *
     * @param words список всех слов
     * @param startIndex индекс первого слова сегмента (включительно)
     * @param endIndex индекс последнего слова сегмента (включительно)
     * @param segments список для добавления созданного сегмента
     */
    private void createSegmentFromWords(List<WordInfo> words, int startIndex, int endIndex,
                                        List<TranscriptionSegment> segments) {
        // Проверка корректности индексов
        if (startIndex > endIndex || startIndex >= words.size() || endIndex >= words.size()) {
            return;
        }

        // Объединяем слова в текст
        StringBuilder text = new StringBuilder();
        double startTime = words.get(startIndex).start; // Время начала от первого слова
        double endTime = words.get(endIndex).end; // Время конца от последнего слова

        // Собираем текст всех слов через пробел
        for (int i = startIndex; i <= endIndex; i++) {
            if (!text.isEmpty()) {
                text.append(" ");
            }
            text.append(words.get(i).word);
        }

        // Создаем сегмент только если текст не пустой
        if (!text.isEmpty()) {
            segments.add(new TranscriptionSegment(startTime, endTime, text.toString()));
        }
    }

    public void close() {
        if (model != null) {
            model.close();
        }
    }

    /**
     * Вспомогательный класс для хранения информации о слове с временными метками.
     * <p>
     * Содержит:
     * <ul>
     *   <li>word - распознанное слово</li>
     *   <li>start - время начала слова в секундах</li>
     *   <li>end - время конца слова в секундах</li>
     * </ul>
     * <p>
     * Используется для накопления всех слов перед созданием сегментов.
     */
    private record WordInfo(
            String word,      // Распознанное слово
            double start,     // Время начала слова в секундах
            double end        // Время конца слова в секундах
    ) {
    }
}


