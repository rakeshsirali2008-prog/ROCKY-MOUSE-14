@echo off
rem Right-click this file -> "Run as administrator" (only needed once)
netsh advfirewall firewall add rule name="RemotePad" dir=in action=allow protocol=UDP localport=5005 profile=any
echo.
echo Done. Firewall now allows RemotePad (UDP port 5005).
pause
