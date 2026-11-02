@echo off
REM Respaldo de erpdb en Windows (equipo local). Programar con el Programador de tareas.
REM Requiere C:\erp\backup.cnf (ver backup.cnf.example) y ajustar MYSQLDUMP a la version instalada.
REM Si MySQL solo existe dentro de Docker, usar: docker exec <contenedor> mysqldump ... ^> archivo.sql
setlocal
set MYSQLDUMP="C:\Program Files\MySQL\MySQL Server 8.0\bin\mysqldump.exe"
set CNF=C:\erp\backup.cnf
set DIR=C:\erp\backups
if not exist %DIR% mkdir %DIR%
for /f %%i in ('powershell -NoProfile -Command "Get-Date -Format yyyyMMdd_HHmmss"') do set TS=%%i
%MYSQLDUMP% --defaults-extra-file=%CNF% --single-transaction --quick --routines --triggers --events --hex-blob --set-gtid-purged=OFF --default-character-set=utf8mb4 --result-file=%DIR%\erpdb_%TS%.sql erpdb
if errorlevel 1 (echo ERROR en mysqldump & exit /b 1)
powershell -NoProfile -Command "Compress-Archive -Path '%DIR%\erpdb_%TS%.sql' -DestinationPath '%DIR%\erpdb_%TS%.zip'" && del %DIR%\erpdb_%TS%.sql
REM Conserva 14 dias
forfiles /p %DIR% /m erpdb_*.zip /d -14 /c "cmd /c del @path" 2>nul
echo OK %DIR%\erpdb_%TS%.zip
