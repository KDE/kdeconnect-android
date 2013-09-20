#!/usr/bin/env bash

mkdir locale
a2po export --android KdeConnect/src/main/res/ --gettext locale
mv locale/template.pot $podir/kdeconnect-android.pot
rm -rf locale
