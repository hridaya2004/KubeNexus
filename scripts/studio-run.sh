#!/usr/bin/env bash
# Android Studio "play" entry point.
#
# Runs from the repository root so the Makefile stays the single build entry
# point: it builds the Go and Ghostty native libraries, installs the debug app
# and launches it on the connected device.
set -euo pipefail

cd "$(dirname "${BASH_SOURCE[0]}")/.."

exec make run
