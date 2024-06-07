import subprocess
import sys
from flask import Flask

proc = None

app = Flask(__name__)

@app.route('/start')
def start():
    global proc
    if proc is None:
        proc = subprocess.Popen("java -Xms1g -Xmx1g -cp target/nabu-v0.7.7-jar-with-dependencies.jar org.peergos.Client -id {}".format(sys.argv[1]), stdout=subprocess.PIPE, shell=True) 
    return "", 200

@app.route('/stop')
def stop():
    global proc
    if proc is not None:
        proc.kill()
        proc = None
    return "", 200

if __name__ == '__main__':
    app.run(host='0.0.0.0', port=8080)