#!/bin/sh
# Generates the exact seed described by a permalink (asked interactively) into the "seeds" folder
cd "$(dirname "$0")" && mkdir -p seeds && exec java -jar alundra-randomizer.jar --permalink --outputrom=seeds/ "$@"
