# Usage: ./run.sh <node-id>

# NOTE(@millerm) - need to hook up with multiple clients
#java -cp target/nabu-v0.7.7-jar-with-dependencies.jar org.peergos.Client -id $1
java -cp target/nabu-v0.7.7-jar-with-dependencies.jar org.peergos.ClientWithLogExporter
