# Usage: ./run-local.sh <node-id>

java -Xms1g -Xmx1g -cp target/nabu-v0.7.7-jar-with-dependencies.jar org.peergos.Client -id $1 -local
