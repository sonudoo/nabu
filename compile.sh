sudo pip3 install virtualenv
virtualenv nabu
source nabu/bin/activate
pip3 install flask
mvn package -Dmaven.test.skip=true
