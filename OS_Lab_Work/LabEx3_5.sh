#!/bin/bash

echo "=========================================="
echo "   PROCESS MONITORING AUTOMATION"
echo "=========================================="
echo "Monitoring processes every 15 seconds (3 times)"
echo "Start time: $(date)"
echo "=========================================="

for i in {1..3}; do
    echo ""
    echo "--- Snapshot $i at $(date +%H:%M:%S) ---"
    echo "=========================================="
    
    ps aux
    
    echo "=========================================="
    
    if [ $i -lt 3 ]; then
        echo "Waiting 15 seconds for next snapshot..."
        sleep 15
    fi
done

echo ""
echo "Monitoring complete! End time: $(date)"
