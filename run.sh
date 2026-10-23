#!/bin/sh
set -eu
cd "$(CDPATH= cd -- "$(dirname -- "$0")" && pwd)"

if [ -d /opt/homebrew/opt/openjdk@21/libexec/openjdk.jdk/Contents/Home ]; then
    JAVA_HOME=/opt/homebrew/opt/openjdk@21/libexec/openjdk.jdk/Contents/Home
elif command -v /usr/libexec/java_home >/dev/null 2>&1; then
    JAVA_HOME=$(/usr/libexec/java_home -v 21)
fi
if [ -n "${JAVA_HOME:-}" ]; then
    export JAVA_HOME
    PATH="$JAVA_HOME/bin:$PATH"
    export PATH
fi

if [ -f .env ]; then
    set -a
    . ./.env
    set +a
fi

exec sh ./mvnw javafx:run "$@"
