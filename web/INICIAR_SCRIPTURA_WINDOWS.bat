@echo off
cd /d "%~dp0"
py -3 launcher.py
if errorlevel 1 echo Comprueba que Python 3.10 o posterior este instalado.
pause
