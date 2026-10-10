@echo off
setlocal
cd /d "%~dp0"

set "ADB=%LOCALAPPDATA%\Android\Sdk\platform-tools\adb.exe"
if not exist "%ADB%" set "ADB=%~dp0.successo-build-tools\android-sdk\platform-tools\adb.exe"

if not exist "%ADB%" (
    echo ADB non trovato. Compila prima l'app con il builder v23.
    pause
    exit /b 1
)

for /f "delims=" %%F in ('dir /b /o-d "SuccessoPlayer_Android_v3.5_build*.apk" 2^>nul') do (
    set "APK=%%F"
    goto :found
)

echo APK v3.5 non trovato.
pause
exit /b 1

:found
echo Installo/aggiorno: %APK%
"%ADB%" install -r "%~dp0%APK%"

echo.
pause
