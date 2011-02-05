#! /bin/bash

if [ $# -ne 1 ]; then
  RUNS=10
fi

if [ $# -eq 1 ]; then
  RUNS=$1
fi

FILE="grendel_test_`echo $(date +"%Y%m%d")`.csv"
echo "GrendelTest using file: $FILE"

GRENDEL_URL=https://127.0.0.1:8080

RANDOM=$$
VAULT_USER_ID=$RANDOM
VAULT_PASSWORD=h9873h19h13rh3791rh3198rh39r18

echo "Testing with user_id=$VAULT_USER_ID"

for (( i = 0 ;  i < $RUNS ; i++ ))
do
	START=$(date +%s%N)
	./examples/users/create.sh $VAULT_USER_ID $VAULT_PASSWORD $GRENDEL_URL
	END=$(date +%s%N)
	DIVIDEND=`echo $END - $START | bc`
	DIVISOR=1000000000
	DIFF=`echo "$DIVIDEND / $DIVISOR" | bc -l`
	echo "create,$DIFF" >> $FILE
	
	echo "delete user with id=#{user.id}"
	./examples/users/delete.sh $VAULT_USER_ID $VAULT_PASSWORD $GRENDEL_URL
done

echo "Finished grendel timings"
