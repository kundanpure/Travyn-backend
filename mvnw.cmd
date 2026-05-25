@REM Maven Wrapper — Travyn Backend
@echo off
setlocal

@REM Use locally cached Maven 3.9.10
set "MVN_HOME=%USERPROFILE%\.m2\wrapper\dists\apache-maven-3.9.10\a38810a491b03367137adfdfbe7d14c4"

if exist "%MVN_HOME%\bin\mvn.cmd" (
    "%MVN_HOME%\bin\mvn.cmd" %*
    exit /B %ERRORLEVEL%
)

@REM Fallback: try any 3.9.x found under .m2
for /D %%d in ("%USERPROFILE%\.m2\wrapper\dists\apache-maven-3.9*") do (
    for /D %%e in ("%%d\*") do (
        if exist "%%e\bin\mvn.cmd" (
            "%%e\bin\mvn.cmd" %*
            exit /B %ERRORLEVEL%
        )
    )
)

echo ERROR: Maven not found. Please install Maven 3.9+ or run:
echo   Invoke-WebRequest -Uri "https://repo.maven.apache.org/maven2/org/apache/maven/apache-maven/3.9.10/apache-maven-3.9.10-bin.zip" -OutFile maven.zip
echo   Expand-Archive maven.zip -DestinationPath "%USERPROFILE%\.m2\wrapper\dists"
exit /B 1
