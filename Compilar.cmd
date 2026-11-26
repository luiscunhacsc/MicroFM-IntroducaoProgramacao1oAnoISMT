@echo off
setlocal
chcp 65001 >nul
cd /d "%~dp0"
where javac >nul 2>nul
if errorlevel 1 (
    echo Para compilar, instala um JDK 17 ou superior.
    echo Para jogar, basta o Java e o MicroFM.jar incluido.
    pause
    exit /b 1
)
if not exist "build\classes" mkdir "build\classes"
javac -encoding UTF-8 --release 17 -d build/classes MicroFM.java src/microfm/model/*.java src/microfm/match/*.java src/microfm/ui/*.java
if errorlevel 1 exit /b 1
jar --create --file MicroFM.jar --main-class MicroFM -C build/classes MicroFM.class -C build/classes microfm -C src microfm/data
if errorlevel 1 exit /b 1
echo MicroFM.jar compilado. Abre Jogar.cmd para jogar.
endlocal
