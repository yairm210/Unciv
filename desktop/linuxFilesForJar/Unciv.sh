#!/bin/sh

CONFIG_DIR="$HOME/.local/share/Unciv"

USAGE="Unciv [--help | -h | --config-dir PATH]

Run the Unciv game.

With '--help' or '-h', show this help info and exit.

With '--config-dir PATH', use/make configuration files in PATH instead
of the default of '$CONFIG_DIR'.

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
    esac
    shift
fi
if ! [ "$#" -eq "0" ]; then
    fail "Unknown argument(s): $*"
fi

mkdir -p "$CONFIG_DIR"
cd "$CONFIG_DIR" || fail "Could not 'cd' to '$CONFIG_DIR'"
java -jar /usr/share/Unciv/Unciv.jar
