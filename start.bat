@echo off
setlocal enabledelayedexpansion
title InterviewForge

cd /d "%~dp0"

set "JAR=InterviewForge\target\InterviewForge-0.0.1-SNAPSHOT.jar"

if not exist "%JAR%" (
  echo [错误] 没有找到 jar 包
  echo        请先运行 build.ps1 构建
  pause
  exit /b 1
)

where java >nul 2>nul
if errorlevel 1 (
  echo [错误] 未检测到 Java
  echo        请安装 JDK 17 或更高版本，并把 bin 目录加入 PATH
  pause
  exit /b 1
)

for /f "tokens=3" %%v in ('java -version 2^>^&1 ^| findstr /i "version"') do set "JVER=%%v"
set "JVER=%JVER:"=%"
for /f "tokens=1 delims=." %%m in ("%JVER%") do set "JMAJOR=%%m"

if not defined JMAJOR (
  echo [错误] 无法识别 Java 版本
  pause
  exit /b 1
)

if %JMAJOR% LSS 17 (
  echo [错误] Java 版本过低，本程序需要 17 或更高版本
  echo        当前版本: %JVER%
  pause
  exit /b 1
)

netstat -ano | findstr ":8081 " | findstr LISTENING >nul
if not errorlevel 1 (
  echo [错误] 端口 8081 已被占用，请先关闭占用进程
  pause
  exit /b 1
)

echo 正在启动 InterviewForge
echo   Java 版本: %JVER%
start "InterviewForge Server" cmd /c "chcp 65001 >nul && java -Dfile.encoding=UTF-8 -jar "%JAR%" --spring.profiles.active=prod"

echo 等待服务就绪...
for /l %%i in (1,1,60) do (
  ping -n 2 127.0.0.1 >nul
  curl -s -o nul http://127.0.0.1:8081/api/health
  if not errorlevel 1 goto ready
)

echo [错误] 启动超时，请查看服务窗口里的日志
pause
exit /b 1

:ready
start "" http://127.0.0.1:8081
echo.
echo 已启动，浏览器会自动打开
echo   地址: http://127.0.0.1:8081
echo   数据: %~dp0data
echo 服务运行在单独窗口，关闭该窗口即停止服务
ping -n 6 127.0.0.1 >nul
exit /b 0