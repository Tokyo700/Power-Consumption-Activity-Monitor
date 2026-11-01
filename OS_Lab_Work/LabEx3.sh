#!/bin/bash

echo "how many nums"
read n;

if [ $n -le 0 ]; then
	echo "Invalid! Must be greater than 0."
	 exit 1
fi

total=0;

for ((i=0; i<n; i++)); do
	echo "Enter number $((i+1)): "
	read num
	total=$(echo "$total + $num" | bc)

done

average=$(echo "scale=2; $total / $n" | bc -l) #scale=2 = 2 decimal places

echo "The average is: $average"
