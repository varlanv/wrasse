#!/bin/sh
set -e
cd "$(dirname "$0")"
mkdir -p build/wrasse/app
printf 'timestamp=%s000\nformatting=true\n' "$(date +%s)" > build/wrasse/app/format-request
./kotlin build
./kotlin do wrasseApply
