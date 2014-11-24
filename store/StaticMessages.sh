#!/usr/bin/env bash

# The name of catalog we create (without the.pot extension), sourced from the scripty scripts
FILENAME="kdeconnect-android-store"

function export_pot_file # First parameter will be the path of the pot file we have to create, includes $FILENAME
{
	potfile=$1
	cp store-texts.po $potfile
}

function import_po_files # First parameter will be a path that will contain several .po files with the format LANG.po
{
	podir=$1
	cp $podir/* translations/
}


