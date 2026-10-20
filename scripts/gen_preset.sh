#!/bin/sh
# Generates a seed from a preset (asked interactively) into the "seeds" folder
cd "$(dirname "$0")" && mkdir -p seeds && exec java -jar alundra-randomizer.jar --outputrom=seeds/ "$@"
