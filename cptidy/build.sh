#!/bin/bash
set -e

mvn clean install
cp -v target/cptidy.jar ~/bin
ls -l target/cptidy.jar ~/bin/cptidy.jar
