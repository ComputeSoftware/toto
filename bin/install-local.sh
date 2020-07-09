#!/usr/bin/env bash

cd server || exit
mvn install:install-file -Dfile=toto-thin.jar -DpomFile=pom.xml