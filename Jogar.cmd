@echo off
setlocal
chcp 65001 >nul
cd /d "%~dp0"
title MicroFM - O teu clube. As tuas decisoes.
java -Dstdout.encoding=UTF-8 -Dstderr.encoding=UTF-8 MicroFM.java
if errorlevel 1 (
    echo.
    echo O MicroFM precisa de Java 25 ou superior.
    echo Se o Java ja esta instalado, consulta a mensagem acima e o GUIA.txt.
    pause
)
endlocal
