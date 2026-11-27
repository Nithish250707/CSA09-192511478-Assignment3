#!/bin/bash
# Compiles and runs every task; each task's run.sh writes its own output.txt
cd "$(dirname "$0")"
for d in Q*/; do echo "== $d"; (cd "$d" && bash run.sh); done
