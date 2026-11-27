#!/bin/bash
cd "$(dirname "$0")" && mkdir -p out && javac -g -d out AtmWithdrawal.java AtmErrorCodes.java && {
  t() { echo "---- TEST $1: card=$2 typed='$3'"; printf '%b' "$3" | java -cp out AtmWithdrawal "$2" 2>&1; echo "(exit code $?)"; echo; }
  t T1 tests/card_1001.txt "2000\n"
  t T2 tests/card_1001.txt "abc\n"
  t T3 tests/card_1001.txt "-500\n"
  t T4 tests/card_1002.txt "1000\n"
  t T5 tests/missing_card.txt "500\n"
  t T6 tests/card_corrupt.txt "500\n"
  t T7 tests/card_1001.txt ""
  t T8 tests/card_1001.txt "250\n"
  t T9 tests/card_unknown.txt "500\n"
  echo "---- Broader catch before narrower (javac negative/WrongOrder.java negative/CatchAllFirst.java):"
  javac -d out/neg negative/WrongOrder.java 2>&1; javac -d out/neg negative/CatchAllFirst.java 2>&1
  echo; echo "---- java AtmErrorCodes (alternative + cost of exceptions)"; java -cp out AtmErrorCodes
} > output.txt; cat output.txt
