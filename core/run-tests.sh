#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/.."
JAVA_HOME=${JAVA_HOME:-/usr/lib/jvm/java-17-amazon-corretto}
shopt -s nullglob
junit=("$HOME"/.gradle/caches/modules-2/files-2.1/junit/junit/4.13.2/*/junit-4.13.2.jar)
hamcrest=("$HOME"/.gradle/caches/modules-2/files-2.1/org.hamcrest/hamcrest-core/1.3/*/hamcrest-core-1.3.jar)
[[ ${#junit[@]} -gt 0 && ${#hamcrest[@]} -gt 0 ]]
cp="${junit[0]}:${hamcrest[0]}"
mkdir -p core/build/direct-tests
sources=(core/src/main/java/com/saebm/btcnfcseller/core/*.java core/src/test/java/com/saebm/btcnfcseller/core/*.java)
"$JAVA_HOME/bin/javac" -encoding UTF-8 -cp "$cp" -d core/build/direct-tests "${sources[@]}"
tests=()
for source in core/src/test/java/com/saebm/btcnfcseller/core/*Test.java; do
    name=${source##*/}; tests+=("com.saebm.btcnfcseller.core.${name%.java}")
done
"$JAVA_HOME/bin/java" -cp "core/build/direct-tests:$cp" org.junit.runner.JUnitCore "${tests[@]}"
