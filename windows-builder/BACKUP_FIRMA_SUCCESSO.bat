@echo off
setlocal
set "SRC=%LOCALAPPDATA%\SuccessoPlayerBuild\successo_update.keystore"
set "DST=%~dp0BACKUP_FIRMA_SUCCESSO"

if not exist "%SRC%" (
    echo La chiave non esiste ancora.
    echo Esegui prima almeno una volta COMPILA_SUCCESSO_PLAYER_v8.bat
    pause
    exit /b 1
)

if not exist "%DST%" mkdir "%DST%"
copy /Y "%SRC%" "%DST%\successo_update.keystore" >nul

echo.
echo Backup creato:
echo %DST%\successo_update.keystore
echo.
echo Conserva questo file: serve per firmare i futuri aggiornamenti.
pause
