@echo off
setlocal
chcp 65001 >nul
cd /d "%~dp0"
if not exist "MicroFM.jar" (
    call Compilar.cmd
    if errorlevel 1 exit /b 1
)
where javaw >nul 2>nul
if errorlevel 1 (
    echo Instala Java 17 ou superior e volta a abrir Jogar.cmd.
    pause
    exit /b 1
)
start "" javaw -Dfile.encoding=UTF-8 -jar "%~dp0MicroFM.jar"
endlocal
