#!/bin/bash
cd "$(dirname "$0")" && mkdir -p out/complete out/nocause out/portal && {
  echo "### complete/StorageDemo.java (Complete the Code)"; javac -d out/complete complete/StorageDemo.java && java -cp out/complete StorageDemo
  echo; echo "### complete_nocause/StorageDemo.java (experiment: IOException NOT passed to the constructor)"; javac -g -d out/nocause complete_nocause/StorageDemo.java && java -cp out/nocause StorageDemo
  echo; echo "### portal/PortalDemo (three-layer portal)"; javac -g -d out/portal portal/*.java && java -cp out/portal PortalDemo
} > output.txt 2>&1; cat output.txt
