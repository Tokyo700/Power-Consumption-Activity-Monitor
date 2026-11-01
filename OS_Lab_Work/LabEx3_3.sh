#!/bin/bash

read -p "Enter a number: " n

if [ "$n" -lt 0 ]; then
    echo "Error: Factorial is not defined for negative numbers!"
    exit 1
fi

factorial=1
i=1

while [ "$i" -le "$n" ]; do
    factorial=$((factorial * i))
    i=$((i + 1))
done

echo "Factorial of $n is: $factorial"
