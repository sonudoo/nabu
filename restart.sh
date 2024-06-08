curl http://34.173.18.240:8080/stop --verbose
curl http://34.142.185.29:8080/stop --verbose
curl http://35.228.228.250:8080/stop --verbose
curl http://34.154.223.19:8080/stop --verbose
curl http://34.47.22.47:8080/stop --verbose
curl http://34.131.85.2:8080/stop --verbose
curl http://34.165.57.99:8080/stop --verbose
curl http://34.97.59.206:8080/stop --verbose
curl http://34.162.175.235:8080/stop --verbose

sleep 10

curl http://34.173.18.240:8080/start --verbose
curl http://34.142.185.29:8080/start --verbose
curl http://35.228.228.250:8080/start --verbose
curl http://34.154.223.19:8080/start --verbose
curl http://34.47.22.47:8080/start --verbose
curl http://34.131.85.2:8080/start --verbose
curl http://34.165.57.99:8080/start --verbose
curl http://34.97.59.206:8080/start --verbose
curl http://34.162.175.235:8080/start --verbose