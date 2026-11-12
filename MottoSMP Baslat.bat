@echo off
title MottoSMP Baslatici
cd /d "%~dp0"
python launcher_web.py
if errorlevel 1 (
    color 0C
    echo.
    echo  Python bulunamadi veya hata olustu.
    echo  Python 3.x kurulu olmali: python.org
    pause
)
