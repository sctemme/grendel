#!/bin/bash
if [ $# -ne 2 ]; then
  echo "Show a document:"
  echo "    $0 <id> <document name>"
  exit -1
fi
curl -v -u $1 "http://127.0.0.1:8080/users/$1/documents/$2" && echo