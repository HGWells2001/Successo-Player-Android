@echo off
setlocal
cd /d "%~dp0"
title Successo Player - Compila APK v3.5 / builder v23

echo.
echo ============================================================
echo  SUCCESSO PLAYER - BUILDER v23
echo ============================================================
echo.
echo Versione 3.5:
echo - MediaSession Android per player schermata di blocco
echo - titolo puntata e artwork nella schermata di blocco
echo - precedente, play/pausa, successiva e stop
echo - controlli cuffie/Bluetooth supportati
echo - tutte le funzioni v3.4 restano attive
echo.

powershell.exe -NoLogo -NoProfile -ExecutionPolicy Bypass -File "%~dp0Build_SuccessoPlayer_APK_v23.ps1"

set ERR=%ERRORLEVEL%
echo.
if "%ERR%"=="0" (
    echo APK v3.5 pronto.
) else (
    echo Compilazione non riuscita.
    echo Inviami build_successo_v23.log.
)
echo.
pause
exit /b %ERR%
