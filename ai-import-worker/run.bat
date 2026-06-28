@echo off
setlocal EnableExtensions EnableDelayedExpansion
chcp 65001 >nul

REM ===== 1) 定位到 ai-import-worker 目录（本脚本应放在 ai-import-worker/ 根） =====
set "WORKER_DIR=%~dp0"

if not exist "%WORKER_DIR%\main.py" (
  echo [ERROR] 未找到 %WORKER_DIR%\main.py
  echo 请确认脚本放在 ai-import-worker 目录内再运行。
  pause
  exit /b 1
)

cd /d "%WORKER_DIR%"

REM ===== 2) Python 检查 =====
where py >nul 2>nul
if errorlevel 1 (
  where python >nul 2>nul
  if errorlevel 1 (
    echo [ERROR] 未检测到 Python，请先安装 Python 3.10+ 并加入 PATH。
    pause
    exit /b 1
  )
  set "PY_CMD=python"
) else (
  set "PY_CMD=py -3"
)

REM ===== 3) 虚拟环境 =====
if not exist ".venv\Scripts\python.exe" (
  echo [INFO] 正在创建虚拟环境 .venv ...
  %PY_CMD% -m venv .venv
  if errorlevel 1 (
    echo [ERROR] 创建虚拟环境失败。
    pause
    exit /b 1
  )
)

call ".venv\Scripts\activate.bat"
if errorlevel 1 (
  echo [ERROR] 激活虚拟环境失败。
  pause
  exit /b 1
)

REM ===== 4) 安装依赖 =====
echo [INFO] 升级 pip 并安装依赖...
python -m pip install -U pip
if errorlevel 1 (
  echo [ERROR] pip 升级失败。
  pause
  exit /b 1
)

python -m pip install -r requirements.txt
if errorlevel 1 (
  echo [ERROR] 依赖安装失败，请检查网络或镜像源。
  pause
  exit /b 1
)

REM ===== 5) .env 检查 =====
if not exist ".env" (
  echo [WARN] 未找到 .env，正在从 .env.example 复制...
  copy ".env.example" ".env" >nul
  echo [WARN] 已创建 .env，请先填写 MINERU_TOKEN（和 LLM_API_KEY 或 SKIP_LLM=true）后重试。
  pause
  exit /b 1
)

findstr /r /c:"^MINERU_TOKEN=." ".env" >nul
if errorlevel 1 (
  echo [ERROR] .env 中 MINERU_TOKEN 为空，请先填写。
  pause
  exit /b 1
)

REM 如果未设置 SKIP_LLM=true，则要求 LLM_API_KEY 非空
findstr /i /r /c:"^SKIP_LLM=true" ".env" >nul
if errorlevel 1 (
  findstr /r /c:"^LLM_API_KEY=." ".env" >nul
  if errorlevel 1 (
    echo [ERROR] .env 中 LLM_API_KEY 为空。若要跳过 LLM，请设置 SKIP_LLM=true。
    pause
    exit /b 1
  )
)

REM ===== 6) 启动 worker =====
echo [INFO] 启动 ai-import-worker...
python main.py
set "EXIT_CODE=%ERRORLEVEL%"

echo.
echo [INFO] 进程已退出，退出码: %EXIT_CODE%
pause
exit /b %EXIT_CODE%