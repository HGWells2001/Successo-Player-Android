@echo off
setlocal
cd /d "%~dp0"
title Successo Player - Compila APK v3.6
powershell.exe -NoLogo -NoProfile -ExecutionPolicy Bypass -File "%~dp0Build_SuccessoPlayer_APK_v24.ps1"
set ERR=%ERRORLEVEL%
if NOT "%ERR%"=="0" echo Compilazione non riuscita: invia build_successo_v24.log.
pause
exit /b %ERR%
