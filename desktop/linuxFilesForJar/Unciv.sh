#!/bin/sh

CONFIG_DIR="$HOME/.local/share/Unciv"
LOG_FILE=""
ENABLE_LOG=false

USAGE="Unciv [--help | -h | --config-dir PATH | --log [FILE]]

Run the Unciv game.

With '--help' or '-h', show this help info and exit.

With '--config-dir PATH', use/make configuration files in PATH instead
of the default of '$CONFIG_DIR'.

With '--log', enable debug logging. Output goes to the terminal by
default, or to FILE if given.
"

usage() {
    echo "$USAGE"
    exit 0
}

fail() {
    echo "Error: $1"
    usage
    exit 1
}

if [ "$#" -gt "0" ]; then
    case "$1" in
        --help|-h)
            shift
            usage
            ;;
        --config-dir)
            CONFIG_DIR="$2"
            shift 2
            ;;
    --log)
        ENABLE_LOG=true
        shift
        case "$1" in
            --*|"") ;;
            *) LOG_FILE="$1"; shift ;;
        esac
        ;;
    esac
fi
if ! [ "$#" -eq "0" ]; then
    fail "Unknown argument(s): $*"
fi

mkdir -p "$CONFIG_DIR"
cd "$CONFIG_DIR" || fail "Could not 'cd' to '$CONFIG_DIR'"

if [ "$ENABLE_LOG" = true ]; then
    if [ -n "$LOG_FILE" ]; then
        java -ea -jar /usr/share/Unciv/Unciv.jar > "$LOG_FILE" 2>&1
    else
        java -ea -jar /usr/share/Unciv/Unciv.jar
    fi
else
    java -jar /usr/share/Unciv/Unciv.jar
fi
