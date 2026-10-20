@echo off
cd /d "%~dp0"
if not exist seeds mkdir seeds
java -jar alundra-randomizer.jar --permalink --outputrom=seeds/ --pause
