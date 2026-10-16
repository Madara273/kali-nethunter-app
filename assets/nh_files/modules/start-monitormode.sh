#!/bin/bash
device=$(getprop ro.product.device)
nexmon=$(strings /vendor/firmware/*bcm* 2>/dev/null | grep nexmon)
if [ -z "$nexmon" ]; then
  CON_MODE_PATH=$(find /sys/module/*/parameters/con_mode 2>/dev/null | head -n 1)
  if [ -f "$CON_MODE_PATH" ]; then
    echo -e "QCACLD-3.0 driver detected"
    CURRENT_MODE=$(cat "$CON_MODE_PATH")
    if [ "$CURRENT_MODE" = "4" ]; then
      echo -e "\033[31mMonitor mode enabled already\033[0m. Exiting.."
    else
      echo -e "Enabling monitor mode via con_mode.."
      case "$device" in
        pyxis)
          echo -e "Xiaomi Mi 9 Lite detected"
          echo -e "Running commands gently to avoid crash.."
          svc wifi disable
          sleep 3
          ip link set wlan0 down
          sleep 3
          echo 4 > "$CON_MODE_PATH"
          sleep 3
          ip link set wlan0 up
          ;;
        *)
          svc wifi disable
          ip link set wlan0 down
          echo 4 > "$CON_MODE_PATH"
          ip link set wlan0 up
          ;;
      esac
      echo -e "\033[32mDone!\033[0m Exiting.."
    fi
  else
    echo -e "\033[31mYour device does not support monitor mode!\033[0m Exiting.."
  fi
else
  echo -e "Nexmon firmware detected"
  echo -e "Enabling monitor mode via nexutil.."
  case "$device" in
    rover|rubyfish)
      echo -e "TicWatch Pro 3 series detected"
      settings put system clockwork_wifi_setting on
      settings put system clockwork_wifi_setting off
      sleep 3
      ifconfig wlan0 up
      sleep 1
      nexutil -m2
      ;;
    beyond0lte|beyond1lte|beyond2lte|beyondx)
      echo -e "Samsung S10 series detected"
      svc wifi disable
      sleep 2
      ifconfig wlan0 up
      nexutil -k1
      nexutil -s0x613 -i -v2
      ;;
    *)
      svc wifi disable
      sleep 2
      ifconfig wlan0 up
      nexutil -k1
      nexutil -m2
      ;;
  esac
  echo -e "\033[32mDone!\033[0m Exiting.."
fi
