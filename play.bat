@echo off
cd /d "%~dp0"
java -Dfile.encoding=UTF-8 -jar "dist\MyWorld3D.jar"
if errorlevel 1 pause
