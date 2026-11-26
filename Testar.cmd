@echo off
setlocal
chcp 65001 >nul
cd /d "%~dp0"
call Compilar.cmd
if errorlevel 1 exit /b 1
if not exist "build\test-classes" mkdir "build\test-classes"
javac -encoding UTF-8 --release 17 -d build/test-classes MicroFM.java src/microfm/model/*.java src/microfm/match/*.java src/microfm/ui/*.java tests/TestCareer.java tests/TestMatch.java tests/TestSwing.java tests/BenchmarkRender.java
if errorlevel 1 exit /b 1
java -Dfile.encoding=UTF-8 -Dstdout.encoding=UTF-8 -Dstderr.encoding=UTF-8 -Djava.awt.headless=true -cp "MicroFM.jar;build/test-classes" TestCareer
if errorlevel 1 exit /b 1
java -Dfile.encoding=UTF-8 -Dstdout.encoding=UTF-8 -Dstderr.encoding=UTF-8 -Djava.awt.headless=true -cp "MicroFM.jar;build/test-classes" TestMatch
if errorlevel 1 exit /b 1
java -Dfile.encoding=UTF-8 -Dstdout.encoding=UTF-8 -Dstderr.encoding=UTF-8 -Djava.awt.headless=true -cp "MicroFM.jar;build/test-classes" microfm.ui.TestSwing
if errorlevel 1 exit /b 1
if /i "%~1"=="--benchmark" java -Dfile.encoding=UTF-8 -Dstdout.encoding=UTF-8 -Dstderr.encoding=UTF-8 -Djava.awt.headless=true -cp "MicroFM.jar;build/test-classes" BenchmarkRender
if errorlevel 1 exit /b 1
echo Todas as verificacoes passaram.
endlocal
