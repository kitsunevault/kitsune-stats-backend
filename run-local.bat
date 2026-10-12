@echo off
setlocal
rem Arranca stats-service en local. Lee DB_PASSWORD del registro (nivel Usuario), igual que
rem run-service.bat de KitsuneShop.
for /f "usebackq delims=" %%p in (`powershell -NoProfile -Command "[System.Environment]::GetEnvironmentVariable('DB_PASSWORD','User')"`) do set DB_PASSWORD=%%p
call "%~dp0gradlew.bat" -p "%~dp0." bootRun --console=plain
