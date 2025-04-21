#!/bin/bash

# Set the screen resolution for Xephyr (can adjust if needed)
SCREEN_RES="1280x720"
DISPLAY_NUM=":1"

# Path to ICSim and noVNC (edit if needed)
ICSIM_DIR="/opt/car_hacking/ICSim/builddir"
NOVNC_DIR="/opt/noVNC"

# Install noVNC if it's not already installed
if [ ! -d "$NOVNC_DIR" ]; then
    echo "Cloning the noVNC repository..."
    git clone https://github.com/novnc/noVNC.git $NOVNC_DIR
fi

# Start Xephyr (nested X server)
echo "Starting Xephyr on display $DISPLAY_NUM..."
Xephyr $DISPLAY_NUM -screen $SCREEN_RES &
XEHPID=$!
sleep 2  # Allow Xephyr to start

# Set the DISPLAY environment to Xephyr's virtual display
export DISPLAY=$DISPLAY_NUM

# Start x11vnc to stream Xephyr window (using the password)
echo "Starting x11vnc to stream Xephyr display..."
x11vnc -display $DISPLAY_NUM -nopw -forever -shared &

# Start noVNC to view ICSim in the browser
echo "Starting noVNC to access ICSim in browser..."
cd $NOVNC_DIR
./utils/novnc_proxy --vnc localhost:5900 &

# Inform the user where to access ICSim
echo "ICSim is now accessible in your browser at http://localhost:6080"
