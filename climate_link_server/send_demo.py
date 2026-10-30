"""Explicitly send simulated samples so the dashboard can be tried without a board."""
import argparse
import json
import os
import secrets
import time
from urllib.request import Request, urlopen

def main():
    parser=argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--url',default='http://127.0.0.1:8080/api/v1/measurements')
    args=parser.parse_args()
    token=os.environ.get('CLIMATE_API_TOKEN')
    if not token: parser.error('Set CLIMATE_API_TOKEN to the running server token first')
    now=int(time.time())
    body={'schema_version':1,'device_id':'CL-000000000001','session_id':secrets.token_hex(4),
          'firmware':'demo-0.1.0','simulated':True,'battery_mv':None,'dropped_samples':0,
          'samples':[{'sequence':i+1,'timestamp':now-(19-i)*60,
                      'temperature_c':round(23+i*.08+(i%4)*.12,2),
                      'humidity_pct':round(48-i*.15+(i%3)*.4,2)} for i in range(20)]}
    req=Request(args.url,data=json.dumps(body).encode(),
                headers={'Content-Type':'application/json','Authorization':'Bearer '+token})
    with urlopen(req,timeout=10) as response: print(response.read().decode())

if __name__=='__main__': main()
