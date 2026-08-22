#!/bin/bash
set -e

# 1. Ensure binary output directory exists
mkdir -p bin

# 2. Compile all source files into bin/
echo "Compiling Java files..."
javac -d bin $(find src -name "*.java")

# 3. Execute the Test driver
echo "Running Flash Sale Engine Benchmark..."
java -cp bin com.flashsale.Test
