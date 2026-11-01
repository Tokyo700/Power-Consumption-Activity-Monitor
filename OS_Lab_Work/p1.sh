#!/bin/bash

echo "Enter first number:"
read a

if [ -z "$a" ]
then
    echo "please enter a number"
    exit 1
fi

echo "Enter second number:"
read b

if [ -z "$b" ]
then
    echo "please enter a number"
    exit 1
fi

echo "Enter third number:"
read c

if [ -z "$c" ]
then
    echo "please enter a number"
    exit 1
fi

if [ $a -gt $b ] && [ $a -gt $c ]
then
    echo "$a is the greatest number"
elif [ $b -gt $a ] && [ $b -gt $c ]
then
    echo "$b is the greatest number"
else
    echo "$c is the greatest number"
fi
