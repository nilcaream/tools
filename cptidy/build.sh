#!/bin/bash
# Builds and tests cptidy, then installs it as ~/.local/opt/cptidy/cptidy.jar with the command ~/.local/bin/cptidy.
set -euo pipefail

cd "$(dirname "$0")"
mvn -B clean verify

install -D -m 644 target/cptidy.jar ~/.local/opt/cptidy/cptidy.jar
mkdir -p ~/.local/bin
printf '#!/bin/sh\nexec java -jar "$HOME/.local/opt/cptidy/cptidy.jar" "$@"\n' > ~/.local/bin/cptidy
chmod 755 ~/.local/bin/cptidy
ls -l ~/.local/opt/cptidy/cptidy.jar ~/.local/bin/cptidy
