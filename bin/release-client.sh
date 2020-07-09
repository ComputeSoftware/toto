#!/usr/bin/env bash

cd client || exit
npx shadow-cljs release app
