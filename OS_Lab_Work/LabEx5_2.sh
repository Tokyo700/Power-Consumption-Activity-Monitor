#!/bin/bash

# Declare an empty indexed array in the main scope
declare -a my_array

# Function 1: Add 5 elements using a loop
add_five_elements() {
    echo "Enter 5 elements to add to the array:"
    for (( i=1; i<=5; i++ )); do
        read -p "Element $i: " val
        my_array+=("$val")
    done
    echo "5 elements added successfully."
}

# Function 2: Display all elements along with their index positions
display_array() {
    if [ ${#my_array[@]} -eq 0 ]; then
        echo "Array is currently empty."
        return
    fi

    echo "--- Array Contents ---"
    for i in "${!my_array[@]}"; do
        echo "Index [$i] contains: ${my_array[$i]}"
    done
}

# Continuous Menu
while true; do
    echo "=================================="
    echo "      ARRAY OPERATIONS MENU       "
    echo "=================================="
    echo "1. Add 5 Elements"
    echo "2. Display Array Elements"
    echo "3. Exit"
    read -p "Enter choice (1-3): " choice

    case $choice in
        1) add_five_elements ;;
        2) display_array ;;
        3) echo "Exiting program..."; exit 0 ;;
        *) echo "Invalid selection. Please try again." ;;
    esac
    echo ""
done
