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
	# Android doesn't support languages with an @
	find "$podir" -type f -name "*@*.po" -delete
	# drop obsolete messages, as Babel cannot parse them -- see:
	# https://github.com/python-babel/babel/issues/206
	# https://github.com/python-babel/babel/issues/566
	find "$podir" -name '*.po' -exec msgattrib --no-obsolete -o {} {} \;
	ANSI_COLORS_DISABLED=1 a2po import --ignore-fuzzy --android res/ --gettext $podir

	# Generate the locales_config.xml
	pushd res
	echo '<?xml version="1.0" encoding="utf-8"?>' > xml/locales_config.xml
	echo '<locale-config xmlns:android="http://schemas.android.com/apk/res/android">' >> xml/locales_config.xml
	transform_locale_regex='(\w+)-r(\w+)'
	# Add en-US as the first locale so that is the fallback, and also because it won't be handled in the following loop
	echo -e '\t<locale android:name="en-US"/>' >> xml/locales_config.xml
	for i in values-*; do
		if [ -d "${i}" ]; then
			if [ -e "${i}/strings.xml" ]; then
				locale="${i:7}"
				if [[ "${locale}" =~ $transform_locale_regex ]]; then
					# Special case to turn locales like "en-rUS", "en-rGB" into "en-US" and "en-GB"
					transformed_locale="${BASH_REMATCH[1]}-${BASH_REMATCH[2]}"
					echo -e "\t<locale android:name=\"${transformed_locale}\"/>" >> xml/locales_config.xml
				else
					echo -e "\t<locale android:name=\"${locale}\"/>" >> xml/locales_config.xml
				fi
			fi
		fi
	done

	echo "</locale-config>" >> xml/locales_config.xml
	popd
}

