@echo off
rem ================================================================
rem  Backend'i YEREL gelistirme icin baslatir. Oncelik sirasi:
rem
rem  1) backend\.env.local  -> Docker'daki yerel Postgres (K-030).
rem     Konteyner kapaliysa once o acilir. Internet yalniz giris
rem     (Supabase Auth) icin gerekir.
rem  2) backend\.env.dev    -> Supabase GELISTIRME veritabani.
rem  3) Hicbiri yoksa canli veritabanina GUVENLI modda baglanir:
rem     Flyway ve icerik yukleme kapali.
rem ================================================================
cd /d "%~dp0..\backend"

if exist ".env.local" (
    echo [YEREL] backend\.env.local kullaniliyor - Docker'daki Postgres.
    call :yerel_db
    if errorlevel 1 exit /b 1
    for /f "usebackq eol=# tokens=1,* delims==" %%A in (".env.local") do set "%%A=%%B"
    call "%~dp0..\backend\gradlew.bat" bootRun
    exit /b
)

set "DEV=0"
if exist ".env.dev" (
    findstr /r /c:"^DB_PASSWORD=..*" ".env.dev" >nul && set "DEV=1"
)

if "%DEV%"=="1" (
    echo [GELISTIRME] backend\.env.dev kullaniliyor - canli veritabanina dokunulmaz.
    for /f "usebackq eol=# tokens=1,* delims==" %%A in (".env.dev") do set "%%A=%%B"
    call "%~dp0..\backend\gradlew.bat" bootRun
) else (
    echo [CANLI - GUVENLI MOD] .env.dev yok veya sifresi bos. Flyway ve icerik yukleme kapali.
    call "%~dp0..\backend\gradlew.bat" bootRun --args="--app.seed.enabled=false --spring.flyway.enabled=false"
)
exit /b

rem ----------------------------------------------------------------
rem  Yerel Postgres konteynerini ayaga kaldirir (yoksa olusturur).
rem ----------------------------------------------------------------
:yerel_db
docker info >nul 2>&1
if errorlevel 1 (
    echo [HATA] Docker calismiyor. Docker Desktop'i acip tekrar dene.
    exit /b 1
)
docker start isd-postgres >nul 2>&1
if errorlevel 1 (
    echo Yerel veritabani ilk kez olusturuluyor...
    for /f "usebackq eol=# tokens=1,* delims==" %%A in (".env.local") do if "%%A"=="DB_PASSWORD" set "PGPASS=%%B"
    docker run -d --name isd-postgres -p 54329:5432 ^
        -e POSTGRES_DB=ichsprechedeutsch -e POSTGRES_USER=isd -e "POSTGRES_PASSWORD=%PGPASS%" ^
        -v isd-pgdata:/var/lib/postgresql/data postgres:16 >nul
    if errorlevel 1 (
        echo [HATA] Konteyner olusturulamadi.
        exit /b 1
    )
)
rem Veritabani baglanti kabul edene kadar bekle.
for /l %%i in (1,1,30) do (
    docker exec isd-postgres pg_isready -U isd -d ichsprechedeutsch >nul 2>&1 && exit /b 0
    timeout /t 1 >nul
)
echo [HATA] Yerel veritabani 30 sn icinde hazir olmadi.
exit /b 1
