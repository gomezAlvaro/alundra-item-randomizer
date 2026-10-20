#!/bin/sh
# Builds target/alundra-randomizer.jar with the JDK only (no Maven needed). Use "mvn package" to also run the tests.
set -e
cd "$(dirname "$0")"
rm -rf target/classes && mkdir -p target/classes
javac --release 17 -d target/classes $(find src/main/java -name "*.java")
cp -r src/main/resources/* target/classes/
printf 'Main-Class: com.alundra.randomizer.Main\n' > target/manifest.txt
jar cfm target/alundra-randomizer.jar target/manifest.txt -C target/classes .
echo "Built target/alundra-randomizer.jar"
