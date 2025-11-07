#!/usr/bin/env python3
"""
Generate a fresh SESSION_STRING from scratch
Run this once to authenticate and get a new SESSION_STRING
"""
import os
import asyncio
from telethon import TelegramClient
from telethon.sessions import StringSession
from dotenv import load_dotenv

load_dotenv()

API_ID = int(os.getenv("API_ID", 0))
API_HASH = os.getenv("API_HASH", "")

if not API_ID or not API_HASH:
    print("ERROR: API_ID and API_HASH not found in .env file")
    exit(1)

async def main():
    # Create a new client with empty session (will prompt for auth)
    client = TelegramClient(StringSession(), API_ID, API_HASH)

    print("\n" + "="*80)
    print("GENERATING NEW TELEGRAM SESSION")
    print("="*80)
    print("You will be prompted to enter your phone number and verification code.")
    print("="*80 + "\n")

    await client.start()

    # Get user info to confirm authentication
    me = await client.get_me()
    print(f"\n✓ Successfully authenticated as: {me.first_name}")

    # Export the session string
    session_string = client.session.save()

    print("\n" + "="*80)
    print("YOUR NEW SESSION_STRING:")
    print("="*80)
    print(f"\nSESSION_STRING={session_string}\n")
    print("="*80)
    print("\nCopy the SESSION_STRING above and add it to your .env file")
    print("Then restart the main service.")
    print("="*80 + "\n")

    await client.disconnect()

if __name__ == "__main__":
    asyncio.run(main())
