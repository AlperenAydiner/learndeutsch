@echo off
chcp 65001 >nul
title Ich spreche Deutsch - yerel site

rem ================================================================
rem  Siteyi yerelde acar: backend (8080) + on yuz (5500), sonra
rem  tarayicida gosterir. Iki sunucu ayri pencerelerde calisir;
rem  kapatmak icin o pencereleri kapat.
rem ================================================================

where python >nul 2>nul
if errorlevel 1 (
    echo Python bulunamadi. https://www.python.org adresinden kurup tekrar dene.
    pause
    exit /b 1
)

start "Ich spreche Deutsch - BACKEND (kapatmak icin bu pencereyi kapat)" ^
    cmd /k ""%~dp0tools\backend.cmd""

start "Ich spreche Deutsch - ON YUZ (kapatmak icin bu pencereyi kapat)" ^
    python -X utf8 "%~dp0tools\devserver.py" 5500 "%~dp0frontend"

rem On yuz hemen acilir; backend 30-60 sn surer. Bu surede sitede
rem "Sunucu uyaniyor" bandi gorunur, backend hazir olunca kendiliginden gecer.
timeout /t 2 /nobreak >nul
start "" http://localhost:5500/
