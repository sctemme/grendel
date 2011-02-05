#!/bin/bash
if [ $# -ne 3 ]; then
  echo "Create a new user:"
  echo "    $0 <id> <password> <host:port>"
  exit -1
fi
curl -k -v -H "Content-Type: application/json" --data-binary "{\"id\":\"$1\",\"password\":\"$2\"}" $3/users/ && echo