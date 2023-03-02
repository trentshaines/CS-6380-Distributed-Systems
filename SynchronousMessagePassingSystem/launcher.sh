#!/bin/bash

# Change this to your netid
netid=tsh160230

# Root directory of your project
PROJECT_DIR=$HOME/6380-p1

# Directory where the config file is located on your local system
CONFIG_LOCAL=$HOME/6380-launch/config.txt

# Directory your java classes are in
BINARY_DIR=$HOME/6380-p1/bin

# Your main project class
PROGRAM=Process

n=0

cat $CONFIG_LOCAL | sed -e "s/#.*//" | sed -e "/^\s*$/d" |
(
    read i
    echo $i
    while [[ $n < $i ]]
    do
    	read line
    	p=$( echo $line | awk '{ print $1 }' )
      host=$( echo $line | awk '{ print $2 }' )
      port=$( echo $line | awk '{ print $3 }' )
	
	    gnome-terminal -e "ssh -o UserKnownHostsFile=/dev/null -o StrictHostKeyChecking=no $netid@$host java -cp $BINARY_DIR $PROGRAM $p $host $port; exec bash" &

      n=$(( n + 1 ))
    done
)
