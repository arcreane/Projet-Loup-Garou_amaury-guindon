@echo off
REM =====================================================
REM Lance l'environnement de test multijoueur complet :
REM   MariaDB (3307) + API PHP (8000) + N clients JavaFX
REM Usage :  demo.bat [nb_clients]     (defaut : 4)
REM =====================================================
setlocal
set N=%1
if "%N%"=="" set N=4

REM --- MariaDB XAMPP (ne fait rien s'il tourne deja) ---
tasklist /FI "IMAGENAME eq mysqld.exe" | find /I "mysqld.exe" >nul
if errorlevel 1 (
    echo Demarrage de MariaDB...
    start "MariaDB" /MIN C:\xampp\mysql\bin\mysqld.exe --defaults-file=C:\xampp\mysql\bin\my.ini --standalone
    timeout /t 5 >nul
)

REM --- API PHP (echoue sans gravite si le port 8000 est deja pris) ---
REM IMPORTANT : bind explicite 127.0.0.1 (pas "localhost" : PHP peut se lier
REM en IPv6 ::1 seulement, et le client Java tape en IPv4 -> Connection refused)
echo Demarrage de l'API PHP sur 127.0.0.1:8000...
start "API PHP - Loup-Garou" /MIN /D "%~dp0..\backend" C:\xampp\php\php.exe -S 127.0.0.1:8000 -t api
timeout /t 2 >nul

REM --- Clients JavaFX (necessite un JDK 17+, le java du PATH est parfois un vieux Java 8) ---
REM javaw.exe = comme java.exe mais sans console noire
set JAVA_EXE=%USERPROFILE%\.jdks\openjdk-26\bin\javaw.exe
if not exist "%JAVA_EXE%" set JAVA_EXE=%USERPROFILE%\.jdks\jbr-17.0.14\bin\javaw.exe
if not exist "%JAVA_EXE%" set JAVA_EXE=javaw

echo Lancement de %N% clients avec "%JAVA_EXE%"...
for /L %%i in (1,1,%N%) do (
    start "Loup-Garou - client %%i" "%JAVA_EXE%" -jar "%~dp0..\frontend\target\werewolf-frontend-1.0.0.jar"
    timeout /t 1 >nul
)

echo.
echo Comptes de test (mot de passe : test1234) :
echo    amaury, diana, bob, charlie, eve
echo.
pause
