#!/bin/bash

# Get a list of all interfaces starting with can, vcan, or slcan
interfaces=$(ip -o link show | awk -F': ' '{print $2}' | grep -E '^(can|vcan|slcan)[0-9]+')

# Loop through and process each interface
for iface in $interfaces; do
    echo "Bringing down interface: $iface"
    sudo ip link set "$iface" down

    # If the interface is a vcan, also delete it
    if [[ "$iface" =~ ^vcan[0-9]+$ ]]; then
        echo "Deleting vcan interface: $iface"
        sudo ip link delete "$iface" type vcan
    fi
done
