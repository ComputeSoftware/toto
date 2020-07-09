#!/usr/bin/env bash

cd server || exit
clojure -Spom
clojure -A:jar