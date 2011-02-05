#!/bin/bash
if [ $# -ne 1 ]; then
  echo "Show a user's documents:"
  echo "    $0 <id>"
  exit -1
fi
curl -v -u $1 "http://127.0.0.1:8080/users/$1/documents/" && echo