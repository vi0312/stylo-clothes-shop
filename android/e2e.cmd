@echo off
setlocal EnableExtensions

rem Android E2E entrypoint: preflight, adb reverse, mobilecli daemon stop, Gradle run.
rem Docs: docs/testing.md. Usage: e2e.cmd [-Class OrdersJourneyTest] [-LeaveApks]

rem shift renumbers %0..%9, so the script dir must be captured before argument parsing.
set "SCRIPT_DIR=%~dp0"

set "ADB_PORT=%ANDROID_ADB_SERVER_PORT%"
if not defined ADB_PORT set "ADB_PORT=5359"
set "ANDROID_ADB_SERVER_PORT=%ADB_PORT%"
set "API_URL=http://127.0.0.1:8787"

set "CLASS_NAME="
set "LEAVE_APKS="

:parse
if "%~1"=="" goto parsed
if /I "%~1"=="-Class" (
  if "%~2"=="" (
    echo ERROR: -Class needs a journey class name.
    exit /b 2
  )
  set "CLASS_NAME=%~2"
  shift
  shift
  goto parse
)
if /I "%~1"=="-LeaveApks" (
  set "LEAVE_APKS=-Pandroid.injected.androidTest.leaveApksInstalledAfterRun=true"
  shift
  goto parse
)
echo Unknown argument: %~1
exit /b 2
:parsed

where adb >nul 2>nul
if errorlevel 1 (
  echo ERROR: adb is not on PATH.
  exit /b 1
)

rem A `for /f` over a command lets adb's child process eat this script's stdin.
set "DEVICE_LIST=%TEMP%\stylo-e2e-devices.txt"
adb -P %ADB_PORT% devices > "%DEVICE_LIST%" 2>nul <nul
set /a DEVICES=0
for /f "usebackq skip=1 tokens=1,2" %%a in ("%DEVICE_LIST%") do if "%%b"=="device" set /a DEVICES+=1
if %DEVICES%==0 (
  echo ERROR: no device on adb port %ADB_PORT%. Start stylo_api35 with ANDROID_ADB_SERVER_PORT=%ADB_PORT%.
  exit /b 1
)
if %DEVICES% GTR 1 (
  echo ERROR: %DEVICES% devices on adb port %ADB_PORT%; connect exactly one.
  exit /b 1
)

curl.exe -sf %API_URL%/health >nul 2>nul <nul
if errorlevel 1 (
  echo ERROR: API not answering %API_URL%/health. Start it from server/ with "npm run dev".
  exit /b 1
)

adb -P %ADB_PORT% reverse tcp:8787 tcp:8787 >nul <nul
if errorlevel 1 (
  echo ERROR: adb reverse tcp:8787 failed.
  exit /b 1
)

rem The mobilecli daemon owns the device's UiAutomation connection; instrumentation needs it.
where mobilecli >nul 2>nul
if not errorlevel 1 call mobilecli daemon stop >nul 2>nul <nul

set "RUN_ARGS="
if not defined CLASS_NAME goto run
if not "%CLASS_NAME%"=="%CLASS_NAME:.=%" (
  set "RUN_ARGS=-Pandroid.testInstrumentationRunnerArguments.class=%CLASS_NAME%"
) else (
  set "RUN_ARGS=-Pandroid.testInstrumentationRunnerArguments.class=com.stylo.shop.e2e.%CLASS_NAME%"
)
:run

pushd "%SCRIPT_DIR%"
rem --no-daemon: a reused daemon keeps its first client's env and may target adb 5037.
call gradlew.bat :app:connectedDebugAndroidTest --no-daemon %RUN_ARGS% %LEAVE_APKS%
set "RESULT=%ERRORLEVEL%"
popd

echo.
set "REPORT=%SCRIPT_DIR%app\build\reports\androidTests\connected\debug\index.html"
if "%RESULT%"=="0" (
  echo E2E passed. Report: %REPORT%
) else (
  echo E2E failed. Report: %REPORT%
  echo Triage: rerun with -LeaveApks, replay the flow with mobilecli, screenshot into .scratch/stylo-app/assets/^<ticket^>/
)
exit /b %RESULT%
