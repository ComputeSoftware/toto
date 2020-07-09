#!/usr/bin/env bash

cd server || exit
mvn deploy:deploy-file -Dfile=toto-thin.jar -DpomFile=pom.xml -DrepositoryId=clojars -Durl=https://clojars.org/repo/