sudo apt install python3-virtualenv python3-pip
virtualenv nabu
source nabu/bin/activate
pip3 install flask
mvn package -Dmaven.test.skip=true
