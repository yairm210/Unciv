#!/bin/sh
# Launches the headless MCP agent for an LLM client - see docs/Developers/MCP-agent.md.
# Gradle can't configure the :android module on JDK 24+, so prefer a 17 if one is installed.
set -e
cd "$(dirname "$0")/.."
if [ -z "$JAVA_HOME" ] && [ -x /usr/libexec/java_home ]; then
    home17="$(/usr/libexec/java_home -v 17 2>/dev/null || true)"
    if [ -n "$home17" ]; then export JAVA_HOME="$home17"; fi
fi
exec ./gradlew -q :desktop:runMcpAgent
