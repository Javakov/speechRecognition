package org.javakov.service;

/**
 * Сервис для определения языка текста и оценки качества распознавания.
 * <p>
 * Основные функции:
 * <ul>
 *   <li>Определение языка текста на основе алфавита (кириллица/латиница)</li>
 *   <li>Оценка качества распознавания текста</li>
 *   <li>Определение транслитерации английских слов кириллицей</li>
 * </ul>
 * <p>
 * Используется для выбора правильного сегмента при объединении результатов
 * от русской и английской моделей распознавания речи.
 */
public class LanguageDetector {

    /**
     * Определяет язык текста на основе наличия кириллических и латинских символов.
     * <p>
     * Алгоритм:
     * <ol>
     *   <li>Подсчитывает количество кириллических и латинских букв в тексте</li>
     *   <li>Вычисляет соотношение каждого алфавита к общему количеству букв</li>
     *   <li>Если > 60% кириллицы → "ru" (русский)</li>
     *   <li>Если > 60% латиницы → "en" (английский)</li>
     *   <li>Иначе → "mixed" (смешанный)</li>
     * </ol>
     *
     * @param text текст для анализа
     * @return "ru" для русского, "en" для английского, "mixed" для смешанного
     */
    public String detectLanguage(String text) {
        if (text == null || text.trim().isEmpty()) {
            return "en"; // по умолчанию английский
        }

        // Подсчитываем буквы каждого алфавита
        int cyrillicCount = 0;
        int latinCount = 0;
        int totalLetters = 0;

        for (char c : text.toCharArray()) {
            if (Character.isLetter(c)) {
                totalLetters++;
                if (isCyrillic(c)) {
                    cyrillicCount++;
                } else if (isLatin(c)) {
                    latinCount++;
                }
            }
        }

        // Если нет букв, возвращаем английский по умолчанию
        if (totalLetters == 0) {
            return "en";
        }

        // Вычисляем соотношение каждого алфавита
        double cyrillicRatio = (double) cyrillicCount / totalLetters;
        double latinRatio = (double) latinCount / totalLetters;

        // Если больше 60% кириллицы - это русский текст
        if (cyrillicRatio > 0.6) {
            return "ru";
        }
        // Если больше 60% латиницы - это английский текст
        if (latinRatio > 0.6) {
            return "en";
        }
        // Иначе смешанный текст (содержит оба алфавита)
        return "mixed";
    }

    /**
     * Проверяет, является ли символ кириллическим.
     * <p>
     * Проверяет диапазоны Unicode:
     * <ul>
     *   <li>Основная кириллица: U+0400 - U+04FF</li>
     *   <li>Расширенная кириллица: U+0500 - U+052F</li>
     * </ul>
     *
     * @param c символ для проверки
     * @return true если символ кириллический
     */
    private boolean isCyrillic(char c) {
        return (c >= 'Ѐ' && c <= 'ӿ') || // Кириллица (U+0400 - U+04FF)
               (c >= 'Ԁ' && c <= 'ԯ');   // Расширенная кириллица (U+0500 - U+052F)
    }

    /**
     * Проверяет, является ли символ латинским.
     * <p>
     * Проверяет диапазоны:
     * <ul>
     *   <li>Заглавные латинские буквы: A-Z</li>
     *   <li>Строчные латинские буквы: a-z</li>
     * </ul>
     *
     * @param c символ для проверки
     * @return true если символ латинский
     */
    private boolean isLatin(char c) {
        return (c >= 'A' && c <= 'Z') || (c >= 'a' && c <= 'z');
    }

    /**
     * Оценивает качество распознавания текста.
     * <p>
     * Возвращает оценку от 0 до 1, где 1 - лучшее качество.
     * <p>
     * Факторы, влияющие на оценку:
     * <ul>
     *   <li>Количество слов (больше слов = лучше, до 10 слов)</li>
     *   <li>Количество букв (больше букв = лучше, до 50 букв)</li>
     *   <li>Однородность алфавита (бонус за чистый алфавит, штраф за смешанный)</li>
     *   <li>Количество цифр (штраф если слишком много цифр)</li>
     *   <li>Длина текста (бонус за разумную длину 10-200 символов)</li>
     *   <li>Транслитерация (сильный штраф если текст является транслитерацией)</li>
     * </ul>
     *
     * @param text текст для оценки
     * @return оценка качества (0.0 - 1.0), где 1.0 - лучшее качество
     */
    public double assessQuality(String text) {
        if (text == null || text.trim().isEmpty()) {
            return 0.0;
        }

        String trimmed = text.trim();
        int wordCount = trimmed.split("\\s+").length;
        int letterCount = 0;
        int digitCount = 0;
        int cyrillicCount = 0;
        int latinCount = 0;

        // Подсчитываем буквы и цифры
        for (char c : trimmed.toCharArray()) {
            if (Character.isLetter(c)) {
                letterCount++;
                if (isCyrillic(c)) {
                    cyrillicCount++;
                } else if (isLatin(c)) {
                    latinCount++;
                }
            } else if (Character.isDigit(c)) {
                digitCount++;
            }
        }

        // Базовое качество: больше слов и букв = лучше
        // Нормализуем: 10 слов = максимум (0.4), 50 букв = максимум (0.3)
        double quality = Math.min(1.0, wordCount / 10.0) * 0.4;
        quality += Math.min(1.0, letterCount / 50.0) * 0.3;

        // Бонус за однородность алфавита (если текст содержит только один тип букв)
        // Это признак правильного распознавания - модель правильно определила язык
        if (letterCount > 0) {
            double cyrillicRatio = (double) cyrillicCount / letterCount;
            double latinRatio = (double) latinCount / letterCount;
            
            // Если текст содержит только кириллицу или только латиницу (> 90%) - это хорошо
            // Это означает, что модель правильно распознала язык
            if (cyrillicRatio > 0.9 || latinRatio > 0.9) {
                quality += 0.2; // Бонус за однородность
            } else if (cyrillicRatio > 0.1 && latinRatio > 0.1) {
                // Смешанный алфавит - возможна ошибка распознавания
                // Модель пыталась распознать один язык, но получился смешанный результат
                quality *= 0.8; // Штраф за смешанность
            }
        }

        // Штраф за слишком много цифр (возможно, это не текст, а числа)
        // Если цифр больше 30% от букв - это подозрительно
        if (letterCount > 0 && digitCount > letterCount * 0.3) {
            quality *= 0.7;
        }

        // Бонус за разумную длину (не слишком коротко, не слишком длинно)
        // Слишком короткий текст может быть ошибкой, слишком длинный - тоже подозрителен
        if (trimmed.length() >= 10 && trimmed.length() <= 200) {
            quality += 0.1;
        }

        // КРИТИЧЕСКИ ВАЖНО: Штраф за транслитерацию
        // Если текст является транслитерацией английских слов кириллицей - это плохое качество
        // Это означает, что русская модель пыталась распознать английскую речь
        // и выдала мусор в виде транслитерации
        if (cyrillicCount > 0 && isTransliteration(text)) {
            quality *= 0.3; // Сильный штраф за транслитерацию
        }

        return Math.min(1.0, quality);
    }

    /**
     * Проверяет, является ли текст транслитерацией английских слов кириллицей.
     * <p>
     * Использует статистический анализ без захардкоженных словарей.
     * <p>
     * Транслитерация имеет характерные признаки:
     * <ul>
     *   <li>Много очень коротких слов (1-3 буквы) - как английские артикли, предлоги, местоимения</li>
     *   <li>Низкая средняя длина слова (английские слова короче русских)</li>
     *   <li>Мало длинных слов (7+ букв) - русские слова часто длиннее</li>
     *   <li>Высокое соотношение коротких слов (1-4 буквы)</li>
     * </ul>
     * <p>
     * Примеры транслитерации:
     * <ul>
     *   <li>"хэллоу зыс из отец сентенцию инглиш" (hello this is a test sentence in english)</li>
     *   <li>"завязывать туда и сани набором" (the weather today sunny and warm)</li>
     * </ul>
     * <p>
     * Алгоритм:
     * <ol>
     *   <li>Подсчитывает статистику по длинам слов</li>
     *   <li>Проверяет критерии транслитерации</li>
     *   <li>Если >= 30% длинных слов - это нормальный русский текст</li>
     *   <li>Если 2+ критерия выполнены - это транслитерация</li>
     * </ol>
     *
     * @param text текст для проверки (должен содержать кириллицу)
     * @return true если текст является транслитерацией английских слов кириллицей
     */
    public boolean isTransliteration(String text) {
        if (text == null || text.trim().isEmpty()) {
            return false;
        }

        String trimmed = text.trim().toLowerCase();
        String[] words = trimmed.split("\\s+");

        // Нужно минимум 2 слова для анализа
        if (words.length < 2) {
            return false;
        }

        // Подсчитываем слова по длинам
        int totalWords = 0;
        int veryShortWords = 0; // 1-3 буквы (артикли, предлоги: "из", "ту", "г")
        int shortWords = 0; // 1-4 буквы
        int longWords = 0; // 7+ букв (нормальные русские слова обычно длиннее)
        int totalLetters = 0;

        for (String word : words) {
            // Убираем знаки препинания, оставляем только кириллицу
            String cleanWord = word.replaceAll("[^\\p{IsCyrillic}]", "");
            
            if (cleanWord.isEmpty()) {
                continue; // Пропускаем слова без кириллицы
            }
            
            totalWords++;
            int wordLength = cleanWord.length();
            totalLetters += wordLength;

            // Классифицируем слова по длине
            if (wordLength <= 3) {
                veryShortWords++;
            }
            if (wordLength <= 4) {
                shortWords++;
            }
            if (wordLength >= 7) {
                longWords++;
            }
        }

        if (totalWords == 0) {
            return false;
        }

        // Вычисляем статистику
        double veryShortRatio = (double) veryShortWords / totalWords;
        double shortRatio = (double) shortWords / totalWords;
        double longRatio = (double) longWords / totalWords;
        double avgWordLength = (double) totalLetters / totalWords;

        // Транслитерация английского кириллицей имеет характерные признаки:
        // 1. Много очень коротких слов (как английские артикли "a", "the", предлоги "in", "to")
        // 2. Низкая средняя длина слова (английские слова короче русских)
        // 3. Мало длинных слов (русские слова часто длиннее: "предложение", "заказать")
        
        // Критерии для транслитерации:
        boolean hasManyVeryShortWords = veryShortRatio > 0.25; // Более 25% слов длиной 1-3 буквы
        boolean hasLowAvgLength = avgWordLength < 5.2; // Средняя длина слова меньше 5.2 букв
        boolean hasFewLongWords = longRatio < 0.25; // Меньше 25% длинных слов (7+ букв)
        boolean hasManyShortWords = shortRatio > 0.55; // Более 55% слов длиной 1-4 буквы

        // ВАЖНО: Если есть много длинных слов (>= 30%) - это скорее всего нормальный русский текст
        // Транслитерация редко содержит так много длинных слов
        // Пример: "я бы хотел заказать кофе пожалуйста" - содержит "заказать"(8), "пожалуйста"(9)
        if (longRatio >= 0.3) {
            return false;
        }

        // Если выполняются несколько критериев - вероятно транслитерация
        int criteriaCount = 0;
        if (hasManyVeryShortWords) criteriaCount++;
        if (hasLowAvgLength) criteriaCount++;
        if (hasFewLongWords) criteriaCount++;
        if (hasManyShortWords) criteriaCount++;

        // Если 2 или более критерия выполнены - это транслитерация
        // Это означает, что русская модель пыталась распознать английскую речь
        // и выдала мусор в виде транслитерации
        return criteriaCount >= 2;
    }
}

