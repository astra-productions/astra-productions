@echo off
setlocal
cd /d "%~dp0"

set "JAVA_HOME=C:\Program Files\Android\Android Studio\jbr"
set "ADB=%LOCALAPPDATA%\Android\Sdk\platform-tools\adb.exe"
set "APK=%CD%\app\build\outputs\apk\debug\app-debug.apk"

echo.
echo ASTRA PULSE - BUILD UND HANDY UPDATE
echo ====================================
echo.

if not exist "%JAVA_HOME%\bin\java.exe" (
  echo FEHLER: Android-Studio-Java wurde nicht gefunden:
  echo %JAVA_HOME%\bin\java.exe
  pause
  exit /b 1
)

if not exist "%ADB%" (
  echo FEHLER: adb wurde nicht gefunden:
  echo %ADB%
  echo Starte Android Studio einmal komplett oder installiere Android SDK Platform-Tools.
  pause
  exit /b 1
)

echo Baue APK...
call gradlew.bat assembleDebug
if errorlevel 1 (
  echo.
  echo FEHLER: APK konnte nicht gebaut werden.
  pause
  exit /b 1
)

if not exist "%APK%" (
  echo.
  echo FEHLER: APK wurde nach dem Build nicht gefunden:
  echo %APK%
  pause
  exit /b 1
)

echo.
echo Suche verbundenes Android-Geraet...
"%ADB%" devices

echo.
echo WICHTIG:
echo - Handy per USB anschliessen
echo - USB-Debugging muss aktiv sein
echo - Falls am Handy eine Nachfrage kommt: Zulassen druecken
echo.
pause

echo Installiere Update auf dem Handy...
"%ADB%" install -r "%APK%"
if errorlevel 1 (
  echo.
  echo FEHLER: Installation fehlgeschlagen.
  echo Pruefe USB-Debugging und ob das Handy die Verbindung erlaubt hat.
  pause
  exit /b 1
)

echo.
echo FERTIG: Astra Pulse wurde auf dem Handy aktualisiert.
pause
