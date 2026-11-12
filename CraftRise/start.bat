@echo off
title MottoSMP
cd /d "%~dp0"

set "JAVA_EXE=%~dp0java\jdk-x64\bin\java.exe"
if not exist "%JAVA_EXE%" (
    echo [HATA] java\jdk-x64\bin\java.exe bulunamadi!
    pause
    exit /b 1
)

set "RAM=2G"
if exist "%~dp0ayarlar.txt" (
    for /f "usebackq tokens=1,2 delims==" %%A in ("%~dp0ayarlar.txt") do (
        if /i "%%A"=="RAM_MIKTARI" set "RAM=%%B"
    )
)

if not "%MOTTO_USER%"=="" goto launch

:sor
set /p "MOTTO_USER=Oyun ici adin: "
if "%MOTTO_USER%"=="" goto sor

:launch
"%JAVA_EXE%" -cp "%~dp0." Launcher "%MOTTO_USER%" "%RAM%"

if not defined MOTTO_LAUNCHER pause
