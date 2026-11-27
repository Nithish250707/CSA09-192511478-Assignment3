#!/bin/bash
cd "$(dirname "$0")" && mkdir -p out/unsync out/sync && javac -d out/unsync unsync/SeatBooking.java && javac -d out/sync SeatBooking.java && javac -d out RaceStudy.java && {
  echo "### unsync/SeatBooking (TODO 1 WITHOUT synchronized), 10 runs"
  for i in 1 2 3 4 5 6 7 8 9 10; do printf "run %2d: " $i; java -cp out/unsync SeatBooking; done
  echo; echo "### SeatBooking (TODO 1 completed with synchronized), 10 runs"
  for i in 1 2 3 4 5 6 7 8 9 10; do printf "run %2d: " $i; java -cp out/sync SeatBooking; done
  echo; echo "### java RaceStudy"; java -cp out RaceStudy
} > output.txt 2>&1; cat output.txt
