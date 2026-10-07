@echo off
setlocal
cd /d "%~dp0"
set "JAVA_CMD=java"
for /d %%D in ("%ProgramFiles%\Java\jdk-*") do if exist "%%~D\bin\java.exe" set "JAVA_CMD=%%~D\bin\java.exe"
if exist "%JAVA_HOME%\bin\java.exe" set "JAVA_CMD=%JAVA_HOME%\bin\java.exe"
if "%~1"=="" (
    "%JAVA_CMD%" HLInt.java
) else (
    "%JAVA_CMD%" HLInt.java "%~1"
)
set "result=%errorlevel%"
echo.
pause
exit /b %result%
