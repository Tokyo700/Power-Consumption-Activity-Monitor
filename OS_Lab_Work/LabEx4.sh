#!/bin/bash

# Function to calculate total cost
calculate_total() {
    local price=$1
    local quantity=$2
    echo $((price * quantity))
}

# Prompt user input
read -p "Enter unit price: " price
read -p "Enter quantity: " quantity

# Call function and store result
total=$(calculate_total $price $quantity)
echo "Total Cost: $total"
