#!/system/bin/sh

export DISPLAY=:1

# Lancer un affichage virtuel
Xvfb :1 -screen 0 1024x768x16 &

# Lancer un environnement de fenêtre simple (optionnel)
sleep 5
fluxbox &

# Lancer le serveur VNC
sleep 2
x11vnc -display :1 -nopw -forever -bg -rfbport 5900

# Lancer ICSIM (tu peux adapter le chemin)
cd /opt/car_hacking/ICSim/builddir
./icsim vcan0 &

# Lancer le proxy noVNC
cd /opt/noVNC
./utils/novnc_proxy --vnc localhost:5900 --listen 6080
