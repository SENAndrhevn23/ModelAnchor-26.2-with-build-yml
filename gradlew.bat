@echo off
setlocal
set VERSION=9.5.1
set BASE=%USERPROFILE%\.gradle\modelanchor-bootstrap
set DIST=%BASE%\gradle-%VERSION%
set ZIP=%BASE%\gradle-%VERSION%-bin.zip
if not exist "%DIST%\bin\gradle.bat" (
  if not exist "%BASE%" mkdir "%BASE%"
  if not exist "%ZIP%" powershell -NoProfile -ExecutionPolicy Bypass -Command "Invoke-WebRequest 'https://services.gradle.org/distributions/gradle-%VERSION%-bin.zip' -OutFile '%ZIP%'"
  powershell -NoProfile -ExecutionPolicy Bypass -Command "Expand-Archive -Force '%ZIP%' '%BASE%'"
)
call "%DIST%\bin\gradle.bat" %*
