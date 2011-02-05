#!/bin/bash
if [ $# -ne 3 ]; then
  echo "Delete a user:"
  echo "    $0 <id> <password> <host:port>"
  exit -1
fi
curl -k -v -u $1:$2 -X DELETE "$3/users/$1" && echo