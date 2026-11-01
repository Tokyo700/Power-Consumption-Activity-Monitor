#!/bin/bash

# Function for String Length Retrieval
get_string_length() {
    read -p "Enter a string: " input_str
    echo "Length of string: ${#input_str}"
}

# Function for Substring Extraction
extract_substring() {
    read -p "Enter a string: " input_str
    read -p "Enter starting index (offset): " offset
    read -p "Enter length (press Enter to extract till end): " len
    
    if [ -z "$len" ]; then
        echo "Extracted substring: ${input_str:$offset}"
    else
        echo "Extracted substring: ${input_str:$offset:$len}"
    fi
}

# Function for String Replacement
replace_string() {
    read -p "Enter a string: " input_str
    read -p "Enter pattern to search for: " pattern
    read -p "Enter replacement text: " replacement
    echo "1. First occurrence only"
    echo "2. Global occurrence (all matches)"
    read -p "Choice (1/2): " type_choice

    case $type_choice in
        1) echo "Result: ${input_str/$pattern/$replacement}" ;;
        2) echo "Result: ${input_str//$pattern/$replacement}" ;;
        *) echo "Invalid choice." ;;
    esac
}

# Main Menu
while true; do
    echo "=================================="
    echo "     STRING OPERATIONS MENU       "
    echo "=================================="
    echo "1. Get String Length"
    echo "2. Extract Substring"
    echo "3. Replace String Pattern"
    echo "4. Exit"
    read -p "Select an option (1-4): " choice

    case $choice in
        1) get_string_length ;;
        2) extract_substring ;;
        3) replace_string ;;
        4) echo "Exiting program..."; exit 0 ;;
        *) echo "Invalid option. Try again." ;;
    esac
    echo ""
done
