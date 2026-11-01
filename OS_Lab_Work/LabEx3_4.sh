#!/bin/bash

read -p "Enter a number: " num

if ! [[ $num =~ ^[0-9]+$ ]]; then
    echo "Error: Please enter a valid positive integer!"
    exit 1
fi

sum=0
temp=$num

while [ $temp -gt 0 ]; do
    digit=$((temp % 10))
    sum=$((sum + digit))
    temp=$((temp / 10))
done

echo "Sum of digits of $num is: $sum"
