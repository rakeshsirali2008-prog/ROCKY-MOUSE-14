@echo off
schtasks /delete /tn "RemotePad" /f
echo Auto-start removed.
pause
