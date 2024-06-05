#!/usr/bin/env sh

mkdir -p config

sed -e '/externalBuilders:/r ./external_builders/core_yaml_change.yaml' ../config/core.yaml | sed -e "s|_working_dir_|$PWD|g" > ./config/core.yaml

