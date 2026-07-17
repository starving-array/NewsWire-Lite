@REM ----------------------------------------------------------------------------
@REM Maven Start Up Batch script
@REM
@REM Required ENV vars:
@REM JAVA_HOME - location of a JDK home dir
@REM
@REM Optional ENV vars
@REM M2_HOME - location of maven2's installed home dir
@REM MAVEN_BATCH_ECHO - set to 'on' to enable the echoing of the batch commands
@REM MAVEN_BATCH_PAUSE - set to 'on' to wait for a key press before ending
@REM MAVEN_OPTS - parameters passed to the Java VM when running Maven
@REM MAVEN_SKIP_RC - flag to disable loading of mavenrc files
@REM ----------------------------------------------------------------------------

@REM Begin all REM lines with '@' in case MAVEN_BATCH_ECHO is 'on'
@echo off
@REM set title of command window
title %0
@REM enable echoing by setting MAVEN_BATCH_ECHO to 'on'
@if "%MAVEN_BATCH_ECHO%" == "on"  echo %MAVEN_BATCH_ECHO%

@REM set %HOME% to equivalent of $HOME
if "%HOME%" == "" (set "HOME=%HOMEDRIVE%%HOMEPATH%")

@REM Execute a user defined script before this one
if not "%MAVEN_SKIP_RC%" == "" goto skipRcPre
if exist "%USERPROFILE%\mavenrc_pre.bat" call "%USERPROFILE%\mavenrc_pre.bat"
:skipRcPre

set ERROR_CODE=0

@REM set local scope for the variables with windows NT shell
if "%OS%"=="Windows_NT" @setlocal
if "%OS%"=="WINNT" @setlocal

@REM ==== START VALIDATION ====
if not "%JAVA_HOME%" == "" goto OkJHome
for %%i in (java.exe) do set "JAVACMD=%%~$PATH:i"
goto checkJCmd

:OkJHome
set JAVACMD=%JAVA_HOME%\bin\java.exe
if exist "%JAVACMD%" goto checkJCmd
echo.
echo ERROR: JAVA_HOME is set to an invalid directory: %JAVA_HOME%
echo.
echo Please set the JAVA_HOME variable in your environment to match the
echo location of your Java installation.
goto error

:checkJCmd
if exist "%JAVACMD%" goto chkMHome

echo.
echo ERROR: JAVA_HOME is not set and no 'java' command could be found in your PATH.
echo.
echo Please set the JAVA_HOME variable in your environment to match the
echo location of your Java installation.
goto error

:chkMHome
set MAVEN_HOME=%~dp0..
if "%MAVEN_HOME%" == "" set "MAVEN_HOME=%USERPROFILE%\.m2\wrapper\dists\apache-maven-3.9.6-bin\"
if not "%MAVEN_SKIP_RC%" == "" goto skipRcPost

@REM check for Maven home
@REM if not exist "%MAVEN_HOME%\bin\mvn.cmd" goto error

:skipRcPost
@REM remove quotes around MAVEN_HOME
if not "%MAVEN_HOME%"=="" set MAVEN_HOME=%MAVEN_HOME:"=%

@REM set APP_HOME to script directory
for %%i in ("%APP_HOME%") do if not "%APP_HOME%"=="" goto setAppHome
set APP_HOME=%~dp0
if "%APP_HOME%"=="" set APP_HOME=.
:setAppHome
if "%APP_HOME:~-1%"=="\" set APP_HOME=%APP_HOME:~0,-1%

@REM find MAVEN_JAR
set MAVEN_JAR="%APP_HOME%\.mvn\wrapper\maven-wrapper.jar"
if exist "%MAVEN_JAR%" goto init
echo.
echo ERROR: Maven wrapper JAR not found at: %MAVEN_JAR%
echo.
goto error

:init
@REM Get command-line arguments, handling Windows variants
set MAVEN_CMD_LINE_ARGS=%*
if not "%OS%" == "Windows_NT" goto win9xME_args

:win9xME_args
@REM Handle options
set CMD_LINE_ARGS=%MAVEN_CMD_LINE_ARGS%

@REM Execute Maven
set CLASSPATH=%MAVEN_JAR%
set MAVEN_PROJECTBASEDIR=%APP_HOME%
"%JAVACMD%" ^
  -classpath "%CLASSPATH%" ^
  -Dmaven.multiModuleProjectDirectory="%MAVEN_PROJECTBASEDIR%" ^
  org.apache.maven.wrapper.MavenWrapperMain %CMD_LINE_ARGS%
if ERRORLEVEL 1 goto error
goto end

:error
set ERROR_CODE=1

:end
@REM End local scope for the variables with windows NT shell
if "%OS%"=="Windows_NT" endlocal
set ERROR_CODE=%ERROR_CODE%
if not "%MAVEN_SKIP_RC%"=="" goto skipRcPost
if exist "%USERPROFILE%\mavenrc_post.bat" call "%USERPROFILE%\mavenrc_post.bat"
:skipRcPost
exit /b %ERROR_CODE%
