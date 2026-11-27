#!/bin/bash
# Scripted jdb step-through of the manual version (Student A debugging evidence) -> debug_session.txt
cd "$(dirname "$0")" && mkdir -p out && javac -g -d out ReportGenerators.java DebugManual.java && \
{ echo "stop at ReportGenerators:27"; echo "stop at ReportGenerators:35"; echo "catch java.lang.NumberFormatException"; echo "run"; sleep 3
  echo "print path"; sleep 1; echo "cont"; sleep 2; echo "print path"; sleep 1; echo "cont"; sleep 2; echo "print path"; sleep 1; echo "cont"; sleep 2
  echo "where"; sleep 1; echo "up 3"; sleep 1; echo "locals"; sleep 1; echo "cont"; sleep 2; } | jdb -sourcepath . -classpath out DebugManual > debug_session.txt 2>&1
cat debug_session.txt
