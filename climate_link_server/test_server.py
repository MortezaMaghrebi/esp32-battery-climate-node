"""Integration checks: validation, authentication, persistence and retry deduplication."""
import copy
import json
import tempfile
import threading
import unittest
from urllib.request import Request, urlopen
from urllib.error import HTTPError
from pathlib import Path
from server import create_server, Store

TOKEN="integration-test-token-1234"
BATCH={"schema_version":1,"device_id":"CL-AABBCCDDEEFF","session_id":"1234abcd",
       "firmware":"0.1.0","simulated":True,"battery_mv":None,"dropped_samples":0,
       "samples":[{"sequence":1,"timestamp":None,"temperature_c":23.1,"humidity_pct":45.2}]}

class ApiTests(unittest.TestCase):
    def setUp(self):
        self.temp=tempfile.TemporaryDirectory()
        self.db=Path(self.temp.name)/"test.sqlite3"
        self.server=create_server("127.0.0.1",0,self.db,TOKEN)
        self.worker=threading.Thread(target=self.server.serve_forever,daemon=True);self.worker.start()
        self.base=f"http://127.0.0.1:{self.server.server_port}"
    def tearDown(self):
        self.server.shutdown();self.server.server_close();self.worker.join();self.temp.cleanup()
    def call(self,path,body=None,token=TOKEN,raw=None):
        data=raw if raw is not None else json.dumps(body).encode() if body is not None else None
        headers={"Authorization":"Bearer "+token,"Content-Type":"application/json"}
        try:
            with urlopen(Request(self.base+path,data=data,headers=headers),timeout=3) as r:
                return r.status,json.load(r)
        except HTTPError as e:
            with e: return e.code,json.load(e)
    def test_authentication(self):
        self.assertEqual(self.call("/health",token="")[0],200)
        self.assertEqual(self.call("/api/v1/devices",token="wrong")[0],401)
        self.assertEqual(self.call("/api/v1/measurements",BATCH,token="wrong")[0],401)
    def test_retry_deduplication_and_persistence(self):
        self.assertEqual(self.call("/api/v1/measurements",BATCH)[1]["inserted"],1)
        result=self.call("/api/v1/measurements",BATCH)[1]
        self.assertEqual((result["inserted"],result["duplicates"]),(0,1))
        self.assertEqual(Store(self.db).devices()[0]["sample_count"],1)
        rows=self.call("/api/v1/measurements?device_id=CL-AABBCCDDEEFF")[1]["samples"]
        self.assertIsNone(rows[0]["timestamp"]);self.assertEqual(rows[0]["simulated"],1)
    def test_new_session_is_not_duplicate(self):
        self.call("/api/v1/measurements",BATCH)
        data=copy.deepcopy(BATCH);data["session_id"]="ffffffff"
        self.assertEqual(self.call("/api/v1/measurements",data)[1]["inserted"],1)
    def test_invalid_batch_is_atomic(self):
        data=copy.deepcopy(BATCH);data["samples"].append({"sequence":2,"temperature_c":24,"humidity_pct":101})
        self.assertEqual(self.call("/api/v1/measurements",data)[0],400)
        self.assertEqual(Store(self.db).devices(),[])
    def test_invalid_numeric_types(self):
        for value in (True,"25",float("nan"),float("inf"),10**1000):
            data=copy.deepcopy(BATCH);data["samples"][0]["temperature_c"]=value
            self.assertEqual(self.call("/api/v1/measurements",data)[0],400)
    def test_invalid_identifiers_and_limit(self):
        data=copy.deepcopy(BATCH);data["device_id"]="<script>"
        self.assertEqual(self.call("/api/v1/measurements",data)[0],400)
        self.assertEqual(self.call("/api/v1/measurements?device_id=CL-AABBCCDDEEFF&limit=99999")[0],400)
    def test_body_size_and_malformed_json(self):
        self.assertEqual(self.call("/api/v1/measurements",raw=b"{"*17000)[0],413)
        self.assertEqual(self.call("/api/v1/measurements",raw=b"{invalid}")[0],400)
    def test_sample_and_batch_bounds(self):
        for key,value in (("samples",[]),("schema_version",True),("simulated","true"),("battery_mv",0)):
            data=copy.deepcopy(BATCH);data[key]=value
            self.assertEqual(self.call("/api/v1/measurements",data)[0],400)

if __name__=="__main__":unittest.main()
