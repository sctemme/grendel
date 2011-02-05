#!/bin/bash
curl -k -v -u dummy:dummy "https://127.0.0.1/users/dummy/documents/" > verify_grendel.log  2>&1

if grep -q "HTTP/1.1 200 OK" verify_grendel.log
then
	echo "Grendel is running fine"
else
	echo "Grendel is not running properly. Going to kill it."
	kill -9 `ps -ef | grep "java" | grep -v "grep" | awk '{printf $2 " "}'`
fi

exit 0;
