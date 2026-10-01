@echo off
chcp 65001 >nul
setlocal enabledelayedexpansion

REM ============================================================
REM  像素播放器 一键打包
REM  用法：
REM    build-apk.bat          直接双击 / 命令行运行，打 full + lite
REM    build-apk.bat clean    先 clean 再打包（清掉 build/ 里的旧 APK，
REM                           避免换版本号后旧产物被一起汇总进 dist）
REM  产物：dist\ 目录下 6 个 APK（full / lite 各 3 个 ABI）+ 同名 .sha256
REM ============================================================

REM 定位项目目录：脚本放在项目根目录时直接用脚本所在目录；
REM 放到桌面等其它位置时，自动回退到下面这个默认路径（换项目目录就改这一行）
set "PROJECT_DIR=%~dp0"
if not exist "%PROJECT_DIR%gradlew.bat" set "PROJECT_DIR=E:\PixelPlayer-master\"

if not exist "%PROJECT_DIR%gradlew.bat" (
    echo [错误] 未找到 gradlew.bat。
    echo        请把本脚本放到项目根目录下，或修改脚本里的 PROJECT_DIR 变量。
    echo        当前尝试的路径：%PROJECT_DIR%
    echo.
    pause
    exit /b 1
)

cd /d "%PROJECT_DIR%"
set "DIST=%PROJECT_DIR%dist"
set "CLEAN="
if /i "%~1"=="clean" set "CLEAN=clean"

echo ============================================================
echo   像素播放器 一键打包
echo   full + lite  x  arm64 / arm32 / x86_64  =  6 个 APK
if defined CLEAN echo   模式：clean 后全量构建（耗时更久）
echo ============================================================
echo.

REM ---- 1/2 清理上一轮汇总出来的产物，避免新旧版本混在 dist 里 ----
if exist "%DIST%" (
    echo [1/2] 清理 dist 目录中的旧产物 ...
    del /q "%DIST%\*.apk"    >nul 2>nul
    del /q "%DIST%\*.sha256" >nul 2>nul
) else (
    echo [1/2] dist 目录不存在，构建完成后会自动创建。
)

echo.
echo [2/2] 正在构建，首次构建或依赖有变动时耗时较久，请耐心等待 ...
echo.

REM 任务名必须含 Release，ABI 分包靠它开启（见 app/build.gradle.kts 的 isAbiSplitEnabled）
call gradlew.bat %CLEAN% :app:assembleDistRelease
if errorlevel 1 (
    echo.
    echo ============================================================
    echo   [失败] 构建未通过，请查看上方的 Gradle 日志。
    echo ============================================================
    echo.
    pause
    exit /b 1
)

echo.
echo ============================================================
echo   构建成功，产物已汇总到： %DIST%
echo ============================================================
echo.
for %%F in ("%DIST%\*.apk") do (
    set /a "SIZE_MB=%%~zF/1048576"
    echo   %%~nxF    !SIZE_MB! MB
)
echo.
pause
exit /b 0
