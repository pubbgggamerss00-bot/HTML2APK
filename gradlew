#!/bin/sh
# Simple POSIX Gradle wrapper launcher (delegates to gradle-wrapper.jar).
# Works on Linux, macOS and other POSIX shells; requires Java 17 for this project.

APP_HOME=$(cd -P "$(dirname "$0")" > /dev/null && pwd) || exit 1

CLASSPATH=$APP_HOME/gradle/wrapper/gradle-wrapper.jar

if [ -n "$JAVA_HOME" ]; then
    if [ -x "$JAVA_HOME/bin/java" ]; then
        JAVACMD=$JAVA_HOME/bin/java
    else
        echo "ERROR: JAVA_HOME is set to an invalid directory: $JAVA_HOME" >&2
        exit 1
    fi
else
    JAVACMD=java
fi

exec "$JAVACMD" \
    -Dorg.gradle.appname="$(basename "$0")" \
    -classpath "$CLASSPATH" \
    org.gradle.wrapper.GradleWrapperMain "$@"
