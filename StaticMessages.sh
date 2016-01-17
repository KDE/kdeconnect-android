#!/usr/bin/env bash

# The name of catalog we create (without the.pot extension), sourced from the scripty scripts
FILENAME="kdeconnect-android"

function export_pot_file # First parameter will be the path of the pot file we have to create, includes $FILENAME
{
	potfile=$1
	mkdir outdir
	ANSI_COLORS_DISABLED=1 a2po export --android res/ --gettext outdir
	mv outdir/template.pot $potfile
	rm -rf outdir
}

function import_po_files # First parameter will be a path that will contain several .po files with the format LANG.po
{
	podir=$1
	ANSI_COLORS_DISABLED=1 a2po import --ignore-fuzzy --android res/ --gettext $podir
	#Android doesn't support languages with an @
	rm -r res/values-*@*
}


