package org.javakov.service;

import org.javakov.model.TranscriptionSegment;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;

/**
 * Сервис для объединения сегментов из разных моделей распознавания речи.
 * <p>
 * Основная задача: выбрать лучший сегмент для каждого временного интервала
 * из результатов русской и английской моделей распознавания.
 * <p>
 * Алгоритм работы:
 * <ol>
 *   <li>Создает временную шкалу из всех сегментов обеих моделей</li>
 *   <li>Для каждого временного интервала находит перекрывающиеся сегменты</li>
 *   <li>Выбирает лучший сегмент на основе языка, качества и транслитерации</li>
 *   <li>Объединяет соседние сегменты с одинаковым текстом</li>
 * </ol>
 * <p>
 * Критерии выбора лучшего сегмента:
 * <ul>
 *   <li>Соответствие алфавита модели (кириллица для русской, латиница для английской)</li>
 *   <li>Отсутствие транслитерации (русская модель не должна транслитерировать английскую речь)</li>
 *   <li>Качество распознавания (больше слов, однородный алфавит)</li>
 * </ul>
 */
public class SegmentMerger {
    private static final Logger logger = LoggerFactory.getLogger(SegmentMerger.class);
    private final LanguageDetector languageDetector;

    public SegmentMerger() {
        this.languageDetector = new LanguageDetector();
    }

    /**
     * Объединяет сегменты из двух моделей, выбирая лучший результат для каждого временного интервала.
     * <p>
     * Алгоритм:
     * <ol>
     *   <li>Собирает все временные точки (начало и конец) из всех сегментов обеих моделей</li>
     *   <li>Сортирует временные точки по возрастанию</li>
     *   <li>Создает временные интервалы между соседними точками</li>
     *   <li>Для каждого интервала находит лучший перекрывающийся сегмент из каждой модели</li>
     *   <li>Выбирает лучший сегмент на основе языка, качества и транслитерации</li>
     *   <li>Объединяет соседние сегменты с одинаковым текстом</li>
     * </ol>
     *
     * @param segmentsRu сегменты от русской модели распознавания
     * @param segmentsEn сегменты от английской модели распознавания
     * @return объединенный список сегментов с автоматическим выбором лучшего результата
     */
    public List<TranscriptionSegment> mergeSegments(List<TranscriptionSegment> segmentsRu,
                                                     List<TranscriptionSegment> segmentsEn) {
        logger.info("Объединение сегментов: RU={}, EN={}", segmentsRu.size(), segmentsEn.size());

        if (segmentsRu.isEmpty() && segmentsEn.isEmpty()) {
            return new ArrayList<>();
        }

        // Шаг 1: Создаем список всех временных точек из всех сегментов
        // Это нужно для создания единой временной шкалы
        List<Double> timePoints = new ArrayList<>();
        for (TranscriptionSegment seg : segmentsRu) {
            timePoints.add(seg.startTime());
            timePoints.add(seg.endTime());
        }
        for (TranscriptionSegment seg : segmentsEn) {
            timePoints.add(seg.startTime());
            timePoints.add(seg.endTime());
        }
        timePoints.sort(Double::compareTo);

        // Шаг 2: Создаем временные интервалы и выбираем лучший сегмент для каждого
        List<TranscriptionSegment> merged = new ArrayList<>();
        for (int i = 0; i < timePoints.size() - 1; i++) {
            double startTime = timePoints.get(i);
            double endTime = timePoints.get(i + 1);

            // Пропускаем слишком короткие интервалы (< 0.1 сек) - это незначительные перекрытия
            if (endTime - startTime < 0.1) {
                continue;
            }

            // Находим сегменты, которые перекрываются с этим интервалом
            // Выбираем сегмент с наибольшим перекрытием из каждой модели
            TranscriptionSegment ruSegment = findBestOverlappingSegment(segmentsRu, startTime, endTime);
            TranscriptionSegment enSegment = findBestOverlappingSegment(segmentsEn, startTime, endTime);

            // Выбираем лучший сегмент на основе языка, качества и транслитерации
            TranscriptionSegment bestSegment = selectBestSegment(ruSegment, enSegment, startTime, endTime);

            if (bestSegment != null) {
                // Создаем новый сегмент с временными метками интервала
                // Используем текст из лучшего сегмента, но временные метки берем из интервала
                merged.add(new TranscriptionSegment(startTime, endTime, bestSegment.text()));
            }
        }

        // Шаг 3: Объединяем соседние сегменты с одинаковым текстом
        // Это нужно для устранения дубликатов, которые могли возникнуть при разбиении на интервалы
        merged = mergeAdjacentSegments(merged);

        logger.info("Объединено сегментов: {}", merged.size());
        return merged;
    }

    /**
     * Находит лучший сегмент, который перекрывается с заданным временным интервалом.
     * <p>
     * Выбирает сегмент с наибольшим перекрытием (overlap) с интервалом.
     * <p>
     * Алгоритм:
     * <ol>
     *   <li>Проверяет каждый сегмент на перекрытие с интервалом [startTime, endTime]</li>
     *   <li>Вычисляет длину перекрытия для каждого сегмента</li>
     *   <li>Возвращает сегмент с максимальным перекрытием</li>
     * </ol>
     * <p>
     * Перекрытие вычисляется как пересечение интервалов:
     * overlap = min(segment.endTime, endTime) - max(segment.startTime, startTime)
     *
     * @param segments список сегментов для поиска
     * @param startTime начало временного интервала
     * @param endTime конец временного интервала
     * @return сегмент с наибольшим перекрытием или null, если перекрытий нет
     */
    private TranscriptionSegment findBestOverlappingSegment(List<TranscriptionSegment> segments,
                                                           double startTime, double endTime) {
        TranscriptionSegment bestSegment = null;
        double maxOverlap = 0;

        for (TranscriptionSegment segment : segments) {
            // Проверяем перекрытие: сегмент перекрывается с интервалом, если
            // segment.startTime < endTime И segment.endTime > startTime
            if (segment.startTime() < endTime && segment.endTime() > startTime) {
                // Вычисляем длину перекрытия как пересечение интервалов
                double overlapStart = Math.max(segment.startTime(), startTime);
                double overlapEnd = Math.min(segment.endTime(), endTime);
                double overlap = overlapEnd - overlapStart;

                // Выбираем сегмент с наибольшим перекрытием
                if (overlap > maxOverlap) {
                    maxOverlap = overlap;
                    bestSegment = segment;
                }
            }
        }

        return bestSegment;
    }

    /**
     * Объединяет соседние сегменты с одинаковым текстом.
     * <p>
     * Это нужно для устранения дубликатов, которые могли возникнуть при разбиении
     * временной шкалы на интервалы. Если два соседних сегмента имеют одинаковый текст
     * и находятся близко друг к другу (разница < 0.5 сек), они объединяются в один.
     * <p>
     * Пример:
     * <ul>
     *   <li>До: [0-2] "hello", [2-4] "hello"</li>
     *   <li>После: [0-4] "hello"</li>
     * </ul>
     *
     * @param segments список сегментов для объединения
     * @return список сегментов с объединенными дубликатами
     */
    private List<TranscriptionSegment> mergeAdjacentSegments(List<TranscriptionSegment> segments) {
        if (segments.isEmpty()) {
            return segments;
        }

        List<TranscriptionSegment> merged = new ArrayList<>();
        TranscriptionSegment current = segments.getFirst();

        for (int i = 1; i < segments.size(); i++) {
            TranscriptionSegment next = segments.get(i);

            // Если тексты совпадают и сегменты соседние (разница < 0.5 сек), объединяем
            // Это означает, что один и тот же текст был разбит на несколько сегментов
            if (current.text().equals(next.text()) && 
                Math.abs(current.endTime() - next.startTime()) < 0.5) {
                // Объединяем: берем начало первого и конец второго
                current = new TranscriptionSegment(
                    current.startTime(),
                    next.endTime(),
                    current.text()
                );
            } else {
                // Тексты разные или сегменты далеко друг от друга - сохраняем текущий
                merged.add(current);
                current = next;
            }
        }

        // Добавляем последний сегмент
        merged.add(current);
        return merged;
    }

    /**
     * Выбирает лучший сегмент из двух вариантов (русский и английский).
     * <p>
     * Это ключевой метод, который определяет, какой сегмент выбрать для каждого
     * временного интервала. Использует сложную логику для правильного выбора.
     * <p>
     * Приоритеты выбора:
     * <ol>
     *   <li>Соответствие алфавита модели (кириллица для русской, латиница для английской)</li>
     *   <li>Отсутствие транслитерации (русская модель не должна транслитерировать английскую речь)</li>
     *   <li>Качество распознавания (больше слов, однородный алфавит)</li>
     * </ol>
     * <p>
     * Обрабатывает 4 основных случая:
     * <ul>
     *   <li>Случай 1: RU содержит кириллицу, EN содержит только латиницу</li>
     *   <li>Случай 2: RU содержит кириллицу, EN смешанный или не содержит латиницы</li>
     *   <li>Случай 3: EN содержит только латиницу, RU не содержит кириллицы</li>
     *   <li>Случай 4: Оба сегмента смешанные или неопределенные</li>
     * </ul>
     *
     * @param ruSegment сегмент от русской модели
     * @param enSegment сегмент от английской модели
     * @param startTime начало временного интервала (для логирования)
     * @param endTime конец временного интервала (для логирования)
     * @return лучший сегмент или null, если оба сегмента null
     */
    private TranscriptionSegment selectBestSegment(TranscriptionSegment ruSegment,
                                                    TranscriptionSegment enSegment,
                                                    double startTime,
                                                    double endTime) {
        if (ruSegment == null && enSegment == null) {
            return null;
        }

        if (ruSegment == null) {
            return enSegment;
        }

        if (enSegment == null) {
            return ruSegment;
        }

        // Определяем язык каждого сегмента
        String ruLanguage = languageDetector.detectLanguage(ruSegment.text());
        String enLanguage = languageDetector.detectLanguage(enSegment.text());
        
        logger.info("Выбор сегмента для интервала [{}-{}]: RU='{}' (lang={}), EN='{}' (lang={})",
                     String.format("%.2f", startTime), String.format("%.2f", endTime),
                     ruSegment.text(), ruLanguage, enSegment.text(), enLanguage);

        // КРИТИЧЕСКИ ВАЖНО: Приоритет отдаем сегменту, который содержит правильный алфавит
        // Русская модель должна выдавать кириллицу, английская - латиницу
        
        // Если русский сегмент содержит кириллицу - это хороший знак для русской модели
        boolean ruHasCyrillic = "ru".equals(ruLanguage);
        // Если английский сегмент содержит только латиницу - это хороший знак для английской модели
        boolean enHasOnlyLatin = "en".equals(enLanguage);

        // ========================================================================
        // СЛУЧАЙ 1: Русский сегмент содержит кириллицу, английский - только латиницу
        // ========================================================================
        // Это идеальный случай: обе модели распознали свой язык правильно.
        // НО! Нужно проверить, не является ли русский сегмент транслитерацией английского.
        // Если русская модель пыталась распознать английскую речь, она может выдать
        // транслитерацию (например, "хэллоу зыс из" вместо "hello this is").
        if (ruHasCyrillic && enHasOnlyLatin) {
            // Проверяем, является ли русский сегмент транслитерацией
            boolean ruIsTransliteration = languageDetector.isTransliteration(ruSegment.text());
            
            if (ruIsTransliteration) {
                // Русский сегмент - это транслитерация английского, выбираем английский
                logger.info("Русский сегмент является транслитерацией - выбираем английский: EN='{}'", enSegment.text());
                return enSegment;
            }
            
            // Русский сегмент содержит кириллицу и НЕ определяется как транслитерация алгоритмом
            // Английский сегмент содержит только латиницу
            // НО! Если русский сегмент выглядит как транслитерация (много коротких слов, низкая средняя длина),
            // а английский сегмент имеет хорошее качество - выбираем английский
            
            double ruQuality = languageDetector.assessQuality(ruSegment.text());
            double enQuality = languageDetector.assessQuality(enSegment.text());
            
            // Дополнительная проверка: если английский сегмент значительно лучше по качеству
            // (разница > 0.3), выбираем его, даже если русский не определен как транслитерация
            if (enQuality - ruQuality > 0.3) {
                logger.info("Английский сегмент значительно лучше по качеству, выбираем его: EN='{}'", enSegment.text());
                return enSegment;
            }
            
            // Если качество похожее, но русский сегмент имеет признаки транслитерации
            // (много коротких слов, низкая средняя длина), предпочитаем английский
            if (Math.abs(ruQuality - enQuality) < 0.2) {
                // Проверяем статистику русского сегмента
                String[] ruWords = ruSegment.text().toLowerCase().split("\\s+");
                int totalRuLetters = 0;
                int shortRuWords = 0;
                for (String word : ruWords) {
                    String clean = word.replaceAll("[^\\p{IsCyrillic}]", "");
                    if (!clean.isEmpty()) {
                        totalRuLetters += clean.length();
                        if (clean.length() <= 4) {
                            shortRuWords++;
                        }
                    }
                }
                double avgRuLength = ruWords.length > 0 ? (double) totalRuLetters / ruWords.length : 0;
                double shortRuRatio = ruWords.length > 0 ? (double) shortRuWords / ruWords.length : 0;
                
                // Если русский сегмент имеет признаки транслитерации (много коротких слов, низкая средняя длина)
                // и английский сегмент содержит нормальный английский текст - выбираем английский
                if (avgRuLength < 5.5 && shortRuRatio > 0.5 && enQuality > 0.5) {
                    logger.info("Русский сегмент имеет признаки транслитерации при похожем качестве, выбираем английский: EN='{}'", enSegment.text());
                    return enSegment;
                }
                
                // Иначе предпочитаем русский (так как он содержит кириллицу и не транслитерация)
                logger.info("Качество похожее, выбираем русский сегмент: RU='{}'", ruSegment.text());
                return ruSegment;
            }
            
            return ruQuality > enQuality ? ruSegment : enSegment;
        }

        // ========================================================================
        // СЛУЧАЙ 2: Русский сегмент содержит кириллицу, английский - нет (или смешанный)
        // ========================================================================
        // Русская модель правильно распознала русскую речь (выдала кириллицу).
        // Английская модель либо не распознала ничего, либо выдала мусор.
        // НО! Проверяем, не является ли русский сегмент транслитерацией.
        // Если это транслитерация - выбираем английский (даже если он плохой),
        // так как транслитерация - это признак неправильного распознавания.
        if (ruHasCyrillic) {
            boolean ruIsTransliteration = languageDetector.isTransliteration(ruSegment.text());
            
            if (ruIsTransliteration) {
                // Русский сегмент - транслитерация, даже если английский плохой, выбираем английский
                // так как транслитерация - это признак неправильного распознавания
                logger.info("Русский сегмент является транслитерацией, выбираем английский: EN='{}'", enSegment.text());
                return enSegment;
            }
            
            // Русский сегмент содержит кириллицу и НЕ является транслитерацией
            // Это нормальный русский текст - выбираем его
            logger.info("Выбран русский сегмент (содержит кириллицу, не транслитерация): {}", ruSegment.text());
            return ruSegment;
        }

        // ========================================================================
        // СЛУЧАЙ 3: Английский сегмент содержит только латиницу, русский - нет
        // ========================================================================
        // Английская модель правильно распознала английскую речь (выдала только латиницу).
        // Русская модель либо не распознала ничего, либо выдала мусор.
        // Выбираем английский сегмент без дополнительных проверок.
        if (enHasOnlyLatin) {
            logger.info("Выбран английский сегмент (только латиница): {}", enSegment.text());
            return enSegment;
        }

        // ========================================================================
        // СЛУЧАЙ 4: Оба сегмента смешанные или неопределенные
        // ========================================================================
        // Оба сегмента содержат смешанный алфавит или неопределенный язык.
        // Это сложный случай - выбираем по качеству, но с учетом транслитерации.
        // Если русский сегмент является транслитерацией - предпочитаем английский.
        double ruQuality = languageDetector.assessQuality(ruSegment.text());
        double enQuality = languageDetector.assessQuality(enSegment.text());

        // Проверяем транслитерацию
        boolean ruIsTransliteration = languageDetector.isTransliteration(ruSegment.text());
        
        if (ruIsTransliteration) {
            // Русский сегмент - транслитерация, предпочитаем английский
            logger.info("Русский сегмент является транслитерацией, выбираем английский: EN='{}'", enSegment.text());
            return enSegment;
        }

        // Если русский сегмент содержит хотя бы немного кириллицы, а английский - нет,
        // предпочитаем русский (если это не транслитерация)
        if (ruLanguage.equals("mixed")) {
            // Проверяем, есть ли в русском сегменте кириллица
            if (containsCyrillic(ruSegment.text()) && !containsCyrillic(enSegment.text())) {
                return ruSegment;
            }
        }

        // В остальных случаях выбираем по качеству
        return ruQuality >= enQuality ? ruSegment : enSegment;
    }

    /**
     * Проверяет, содержит ли текст кириллические символы
     */
    private boolean containsCyrillic(String text) {
        if (text == null) {
            return false;
        }
        for (char c : text.toCharArray()) {
            if (Character.isLetter(c) && (c >= 'Ѐ' && c <= 'ӿ')) {
                return true;
            }
        }
        return false;
    }

}
