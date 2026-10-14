#!/bin/bash
echo "If you want to use internal wireless interface --- make sure monitor mode is enabled before selecting ---"
echo "Please choose an interface:"
  options=("Auto" "Nexmon" "QCACLD3.0" "Quit")
  select opt in "${options[@]}"
  do
    case $opt in
      "Auto")
        echo -e "Running wifite as it is.."
        wifite
        break
        ;;
      "Nexmon")
        echo -e "Running wifite with Nexmon lib.."
        LD_PRELOAD=/lib/libnexmonkali.so wifite -i wlan0
        break
        ;;
      "QCACLD3.0")
        echo -e "Running wifite with QCACLD-3.0.."
        wifite -i wlan0
        break
        ;;
      "Quit")
        break
        ;;
        *) echo "Invalid option $REPLY";;
    esac
  done


