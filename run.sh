# Usage: ./run.sh <node-id>
while :
do
    # TODO(sonudoo): Find reason behind memory leaks.
    java  -XX:OnOutOfMemoryError="kill -9 %p" -Xms1g -Xmx1g -cp target/nabu-v0.7.7-jar-with-dependencies.jar org.peergos.Client -id $1
    sleep 1
done
 