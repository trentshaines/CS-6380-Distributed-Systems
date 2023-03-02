#!/bin/bash


# Change this to your netid
netid=tsh160230


# Root directory of your project
PROJECT_DIR=$HOME/6380-p1


# Directory where the config file is located on your local system
CONFIG_LOCAL=$HOME/6380-launch/config.txt

n=0

cat $CONFIG_LOCAL | sed -e "s/#.*//" | sed -e "/^\s*$/d" |
(
    read i
    echo $i
    while [[ $n < $i ]]
    do
    	read line
        host=$( echo $line | awk '{ print $2 }' )

        echo $host
        gnome-terminal -e "ssh -o UserKnownHostsFile=/dev/null -o StrictHostKeyChecking=no $netid@$host killall -u $netid" &
        sleep 1

        n=$(( n + 1 ))
    done
   
)


echo "Cleanup complete"
