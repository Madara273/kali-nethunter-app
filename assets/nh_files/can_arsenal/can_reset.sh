#!/bin/bash

# Get a list of all interfaces starting with can, vcan, or slcan
interfaces=$(ip -o link show | awk -F': ' '{print $2}' | grep -E '^(can|vcan|slcan)[0-9]+')

# Loop through and process each interface
for iface in $interfaces; do
    echo "Processing interface: $iface"

    # Bring down the interface
    sudo ip link set $iface down

    # Detect the interface type (can, vcan, etc.)
    type=$(ip -details link show $iface | grep -oP 'link/\K\w+')

    case "$type" in
        vcan)
            echo "Deleting vcan interface: $iface"
            sudo ip link delete $iface type vcan
            ;;
        can)
            echo "Interface $iface is a real CAN device — just brought it down."
            ;;
        *)
            echo "Interface $iface is of unknown type ($type) — no deletion performed."
            ;;
    esac
done
