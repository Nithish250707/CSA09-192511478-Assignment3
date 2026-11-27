#!/bin/bash
cd "$(dirname "$0")" && mkdir -p out && javac -d out ScannerStations.java && {
  echo "### java ScannerStations"; java -cp out ScannerStations
  echo; echo "### java ScannerStations daemon   (main returns while a daemon station is still working)"; java -cp out ScannerStations daemon
  echo "  (JVM exited - no 'daemon station finished' line was printed)"
} > output.txt 2>&1; cat output.txt
