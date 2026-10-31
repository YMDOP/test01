# InterviewForge 一键构建脚本
# 用法：右键「使用 PowerShell 运行」，或在终端执行  powershell -ExecutionPolicy Bypass -File build.ps1
$ErrorActionPreference = 'Stop'

$root = Split-Path -Parent $MyInvocation.MyCommand.Path
Set-Location $root

Write-Host '==> 1/3 安装前端依赖' -ForegroundColor Cyan
pnpm --dir frontend install --store-dir .cache/pnpm-store
if ($LASTEXITCODE -ne 0) { throw '前端依赖安装失败' }

Write-Host '==> 2/3 打包（Maven 会自动编译前端并打进 jar）' -ForegroundColor Cyan
$mvnArgs = @('-B', '-DskipTests', '-f', 'InterviewForge/pom.xml', 'clean', 'package')
$settings = Join-Path $root '.cache/maven-settings.xml'
if (Test-Path $settings) {
    $mvnArgs = @('-s', $settings) + $mvnArgs
}
& mvn @mvnArgs
if ($LASTEXITCODE -ne 0) { throw 'Maven 打包失败' }

$jar = Join-Path $root 'InterviewForge/target/InterviewForge-0.0.1-SNAPSHOT.jar'
if (-not (Test-Path $jar)) { throw "没有找到产物：$jar" }

$sizeMb = [math]::Round((Get-Item $jar).Length / 1MB, 1)
Write-Host "==> 3/3 构建完成" -ForegroundColor Green
Write-Host "    jar : $jar"
Write-Host "    大小: $sizeMb MB"
Write-Host '    下一步: 双击 start.bat 启动'
