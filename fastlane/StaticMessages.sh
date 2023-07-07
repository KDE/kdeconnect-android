EXPORTS_POT_DIR=1
FILE_PREFIX=kdeconnect-android-store

function export_pot_dir # First parameter will be the path of the directory where we have to store the pot files
{
    potdir=$1
    txt2po --no-segmentation --progress=names -P -i ./metadata/android/en-US/short_description.txt -o $potdir/kdeconnect-android-store-short.pot
    txt2po --no-segmentation --progress=names -P -i ./metadata/android/en-US/full_description.txt -o $potdir/kdeconnect-android-store-full.pot
}

function import_po_dirs # First parameter will be a path that will be a directory to the dirs for each lang and then all the .po files inside
{
    podir=$1
    # Some languages don't exist in Google Play or have different codes
    declare -a to_delete=( "bs" "ca@valencia" "sr@ijekavian" "sr@ijekavianlatin" "sr@latin" "ia" "eo" "tg" )
    for lang in "${to_delete[@]}"; do
        if [ -d $podir/$lang ]; then
            rm $podir/$lang/*
            rmdir $podir/$lang
        fi
    done
    declare -A to_rename=( ["az"]="az-AZ" ["cs"]="cs-CZ" ["da"]="da-DK" ["de"]="de-DE" ["el"]="el-GR" ["es"]="es-ES" ["eu"]="eu-ES"
                           ["fi"]="fi-FI" ["fr"]="fr-FR" ["gl"]="gl-ES" ["he"]="iw-IL" ["hu"]="hu-HU" ["is"]="is-IS" ["it"]="it-IT"
                           ["ja"]="ja-JP" ["ka"]="ka-GE" ["ko"]="ko-KR" ["nl"]="nl-NL" ["nn"]="no-NO" ["pl"]="pl-PL" ["pt"]="pt-PT"
                           ["ru"]="ru-RU" ["sv"]="sv-SE" ["ta"]="ta-IN" ["tr"]="tr-TR")
    for lang in "${!to_rename[@]}"; do
        if [ -d $podir/$lang ]; then
            mv $podir/$lang $podir/${to_rename[$lang]}
        fi
    done
    for lang in $(ls $podir); do
        if [ -f $podir/$lang/kdeconnect-android-store-short.po -a -f $podir/$lang/kdeconnect-android-store-full.po ]; then
            mkdir -p ./metadata/android/$lang/
            cp ./metadata/android/en-US/title.txt ./metadata/android/$lang/title.txt # we do not translate the app name
            po2txt --fuzzy --progress=names -i $podir/$lang/kdeconnect-android-store-short.po -o ./metadata/android/$lang/short_description.txt
            po2txt --fuzzy --progress=names -i $podir/$lang/kdeconnect-android-store-full.po -o ./metadata/android/$lang/full_description.txt
        fi
    done
}

