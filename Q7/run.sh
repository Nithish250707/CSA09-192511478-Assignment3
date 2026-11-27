#!/bin/bash
# data/ (10,000 generated files) is created at run time and not committed.
cd "$(dirname "$0")" && mkdir -p out && javac -d out ResultProcessor.java Q7Main.java && java -Xss512k -cp out Q7Main > output.txt 2>&1; cat output.txt
