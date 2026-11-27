#!/bin/bash
cd "$(dirname "$0")" && mkdir -p out/w out/i && javac -d out/w TokenCounter.java && javac -d out/i ifvariant/TokenCounter.java && javac -d out WalletTransfers.java ProducerConsumerStudy.java && {
  echo "### TokenCounter (Complete the Code, while + notifyAll), 5 runs"
  for i in 1 2 3 4 5; do java -cp out/w TokenCounter; done
  echo; echo "### ifvariant/TokenCounter (while changed to if), 30 runs (5 s timeout each) - outcome counts"
  for i in $(seq 1 30); do perl -e 'alarm 5; exec @ARGV' java -cp out/i TokenCounter 2>&1 | tail -1 || echo "TIMEOUT/killed"; done | sort | uniq -c
  echo; echo "### java WalletTransfers  (Part A)"; java -cp out WalletTransfers
  echo; echo "### java ProducerConsumerStudy  (Part B)"; java -cp out ProducerConsumerStudy
} > output.txt 2>&1; cat output.txt
