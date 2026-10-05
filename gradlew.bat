@echo off
setlocal
set "GRADLE_VERSION=8.2"
if not defined GRADLE_USER_HOME set "GRADLE_USER_HOME=%USERPROFILE%\.gradle"
set "GRADLE_HOME=%GRADLE_USER_HOME%\wrapper\dists\gradle-%GRADLE_VERSION%-bin"
set "GRADLE_DIR=%GRADLE_HOME%\gradle-%GRADLE_VERSION%"
set "GRADLE_ZIP=%GRADLE_HOME%\gradle-%GRADLE_VERSION%-bin.zip"

if not exist "%GRADLE_DIR%\bin\gradle.bat" (
  if not exist "%GRADLE_HOME%" mkdir "%GRADLE_HOME%"
  if not exist "%GRADLE_ZIP%" (
    powershell -NoProfile -ExecutionPolicy Bypass -Command "Invoke-WebRequest -UseBasicParsing -Uri 'https://services.gradle.org/distributions/gradle-%GRADLE_VERSION%-bin.zip' -OutFile '%GRADLE_ZIP%'"
  )
  if exist "%GRADLE_DIR%" rmdir /s /q "%GRADLE_DIR%"
  powershell -NoProfile -ExecutionPolicy Bypass -Command "Expand-Archive -Force '%GRADLE_ZIP%' '%GRADLE_HOME%'"
)

call "%GRADLE_DIR%\bin\gradle.bat" %*
exit /b %ERRORLEVEL%
