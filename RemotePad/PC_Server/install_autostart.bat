@echo off
rem Right-click -> "Run as administrator" (one time only)
rem Starts RemotePad server hidden every time you log in to Windows.
set "PYW=pythonw"
where pythonw >nul 2>nul || set "PYW=pyw"
schtasks /create /tn "RemotePad" /tr "\"%PYW%\" \"%~dp0server.py\"" /sc onlogon /rl highest /f
netsh advfirewall firewall add rule name="RemotePad" dir=in action=allow protocol=UDP localport=5005 profile=any >nul
echo.
echo Done. RemotePad will now start by itself after every restart/login.
echo To start it right now:  schtasks /run /tn RemotePad
echo To see the PIN: open pin.txt in this folder.
pause
