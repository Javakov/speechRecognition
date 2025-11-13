@echo off
REM Скрипт для скачивания моделей Vosk (Windows)

echo === Скачивание моделей Vosk ===
echo.
echo Выберите модель для скачивания:
echo.
echo Русский язык:
echo   1. vosk-model-small-ru-0.22 (45 MB, быстрая)
echo   2. vosk-model-ru-0.42 (1.5 GB, точная)
echo.
echo Английский язык:
echo   3. vosk-model-small-en-us-0.15 (40 MB, быстрая)
echo   4. vosk-model-en-us-0.22 (1.8 GB, точная)
echo.
set /p choice="Введите номер (1-4): "

if "%choice%"=="1" (
    set MODEL_NAME=vosk-model-small-ru-0.22
    set MODEL_URL=https://alphacephei.com/vosk/models/vosk-model-small-ru-0.22.zip
) else if "%choice%"=="2" (
    set MODEL_NAME=vosk-model-ru-0.42
    set MODEL_URL=https://alphacephei.com/vosk/models/vosk-model-ru-0.42.zip
) else if "%choice%"=="3" (
    set MODEL_NAME=vosk-model-small-en-us-0.15
    set MODEL_URL=https://alphacephei.com/vosk/models/vosk-model-small-en-us-0.15.zip
) else if "%choice%"=="4" (
    set MODEL_NAME=vosk-model-en-us-0.22
    set MODEL_URL=https://alphacephei.com/vosk/models/vosk-model-en-us-0.22.zip
) else (
    echo Неверный выбор!
    exit /b 1
)

echo.
echo Скачивание %MODEL_NAME%...
echo URL: %MODEL_URL%
echo.
echo ПРИМЕЧАНИЕ: Скачайте модель вручную по ссылке выше
echo и распакуйте в папку models\%MODEL_NAME%\
echo.
echo Или используйте curl:
echo curl -L -o %MODEL_NAME%.zip %MODEL_URL%
echo.

REM Создаем папку models если её нет
if not exist "models" mkdir models

echo Откройте ссылку в браузере для скачивания:
echo %MODEL_URL%
echo.
start %MODEL_URL%

pause


