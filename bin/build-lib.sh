#!/usr/bin/env bash

cd server || exit
clojure -X:jar
