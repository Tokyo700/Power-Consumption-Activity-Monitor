#!/bin/bash

echo "enter a number :"
read num

if [ -z "$num" ]
then
    echo "please enter a number :"
    exit 1
fi

if [ $((num%2)) -eq 0 ]
then
    echo "$num is even"
else
    echo "$num is odd"
fi
