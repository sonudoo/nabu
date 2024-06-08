curl http://10.128.0.2:8080/stop --verbose
curl http://10.148.0.2:8080/stop --verbose
curl http://10.166.0.2:8080/stop --verbose
curl http://10.198.0.2:8080/stop --verbose
curl http://10.162.0.2:8080/stop --verbose
curl http://10.190.0.2:8080/stop --verbose
curl http://10.208.0.3:8080/stop --verbose
curl http://10.174.0.2:8080/stop --verbose
curl http://10.202.0.2:8080/stop --verbose

sleep 10

curl http://10.128.0.2:8080/start --verbose
curl http://10.148.0.2:8080/start --verbose
curl http://10.166.0.2:8080/start --verbose
curl http://10.198.0.2:8080/start --verbose
curl http://10.162.0.2:8080/start --verbose
curl http://10.190.0.2:8080/start --verbose
curl http://10.208.0.3:8080/start --verbose
curl http://10.174.0.2:8080/start --verbose
curl http://10.202.0.2:8080/start --verbose