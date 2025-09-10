*Create the virtual environment

python3 -m venv venv

*Activate the virtual environment

source venv/bin/activate


python telethon_message_collector.py

render-uvicorn app:app --host 0.0.0.0 --port 8000
local-uvicorn app:app --host 0.0.0.0 --port 8000 --reload
