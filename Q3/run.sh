#!/bin/bash
# Part 3 is run under "ulimit -n 64" so the leak hits the OS limit quickly.
cd "$(dirname "$0")" && mkdir -p out && javac -g -d out ReportGenerators.java ReportDemo.java DebugManual.java && {
  echo "### java ReportDemo 200   (run with ulimit -n 64)"
  ( ulimit -n 64; java -cp out ReportDemo 200 ) 2>&1
} > output.txt; cat output.txt
