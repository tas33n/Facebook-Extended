@echo off
python "%~dp0patch.py" %*
exit /b %errorlevel%
