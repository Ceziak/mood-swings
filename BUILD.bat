@echo off
setlocal
cd /d "%~dp0"

set "GRADLE_VERSION=8.14.3"
set "GRADLE_HOME=%CD%\.gradle-bin\gradle-%GRADLE_VERSION%"
set "GRADLE_ZIP=%CD%\.gradle-bin\gradle-%GRADLE_VERSION%-bin.zip"
set "OUTPUT_JAR=mood-swings-1.0.0.jar"

where java >nul 2>nul
if errorlevel 1 (
    echo [ERROR] Java was not found. Install Java 21 and run this file again.
    pause
    exit /b 1
)

if not exist "%GRADLE_HOME%\bin\gradle.bat" (
    echo Downloading Gradle %GRADLE_VERSION%...
    if not exist "%CD%\.gradle-bin" mkdir "%CD%\.gradle-bin"
    powershell -NoProfile -ExecutionPolicy Bypass -Command ^
      "$ErrorActionPreference='Stop'; Invoke-WebRequest -UseBasicParsing 'https://services.gradle.org/distributions/gradle-%GRADLE_VERSION%-bin.zip' -OutFile '%GRADLE_ZIP%'; Expand-Archive -Force '%GRADLE_ZIP%' '%CD%\.gradle-bin'; Remove-Item '%GRADLE_ZIP%'"
    if errorlevel 1 (
        echo [ERROR] Gradle download failed.
        pause
        exit /b 1
    )
)

echo.
echo Building Mood Swings...
call "%GRADLE_HOME%\bin\gradle.bat" clean build --stacktrace
if errorlevel 1 (
    echo.
    echo [ERROR] Build failed. Copy the full output into a text file and send it back.
    pause
    exit /b 1
)

if not exist "%CD%\dist" mkdir "%CD%\dist"
copy /Y "%CD%\build\libs\%OUTPUT_JAR%" "%CD%\dist\%OUTPUT_JAR%" >nul

echo.
echo ==============================================
echo BUILD COMPLETE
echo %CD%\dist\%OUTPUT_JAR%
echo ==============================================
echo.
pause
