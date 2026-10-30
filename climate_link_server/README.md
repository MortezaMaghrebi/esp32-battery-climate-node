# ClimateLink server

Python 3.11+, no external packages. Run from this folder:

```powershell
$env:CLIMATE_API_TOKEN = 'replace-with-your-random-token-at-least-16-characters'
python server.py --host 0.0.0.0 --port 8080
```

Open http://localhost:8080. For the ESP32 endpoint use the computer LAN address followed by /api/v1/measurements. Enable local HTTP in the Android app for this development setup. Use a valid HTTPS reverse proxy for public deployment. The .env.example file documents the environment variable; .env files are not automatically loaded.

SQLite data defaults to data/climate.sqlite3. Override with --db. The service token is shared by dashboard and devices in v0.1. See the root README and docs/http-api.md for the full setup and protocol.

Run checks from the repository root:

```powershell
python -m unittest discover -s climate_link_server -p test_server.py -v
```
