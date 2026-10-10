#!/bin/bash
device=$(getprop ro.product.device)
nexmon=$(strings /vendor/firmware/*bcm* 2>/dev/null | grep nexmon)
if [ -z "$nexmon" ]; then
  CON_MODE_PATH=$(find /sys/module/*/parameters/con_mode 2>/dev/null | head -n 1)
  if [ -f "$CON_MODE_PATH" ]; then
    echo -e "QCACLD-3.0 driver detected"
    CURRENT_MODE=$(cat "$CON_MODE_PATH")
    if [ "$CURRENT_MODE" = "0" ]; then
      echo -e "\033[31mMonitor mode disabled already\033[0m. Exiting.."
    else
      echo -e "Disabling monitor mode via con_mode.."
      ip link set wlan0 down
      echo 0 > "$CON_MODE_PATH"
      ip link set wlan0 up
      svc wifi enable
      echo -e "\033[32mDone!\033[0m Exiting.."
    fi
  else
    echo -e "\033[31mYour device does not support monitor mode!\033[0m Exiting.."
  fi
else
  echo -e "Nexmon firmware detected"
  echo -e "Disabling monitor mode via nexutil.."
  case "$device" in
    rover|rubyfish)
      nexutil -m0
      ;;
    *)
      nexutil -m0
      svc wifi enable
      ;;
  esac
  echo -e "\033[32mDone!\033[0m Exiting.."
fi
