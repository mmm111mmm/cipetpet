#!/bin/bash
echo -e "Content-type: text/plain\n";
echo "Redeploying cipetpet"

docker ps | grep cipetpet | gawk '{print $1}' | xargs docker stop 
docker ps -a | grep cipetpet | gawk '{print $1}' | xargs docker rm 
docker run -p 8765:8901 -d cipetpet

echo "Redelployed?"
