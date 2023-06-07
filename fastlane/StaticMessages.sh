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
    for lang in $(ls $podir); do
        mkdir -p ./metadata/android/$lang/
        cp ./metadata/android/en-US/title.txt ./metadata/android/$lang/title.txt # we do not translate the app name
        po2txt --fuzzy --progress=names -i $podir/$lang/kdeconnect-android-store-short.po -o ./metadata/android/$lang/short_description.txt
        po2txt --fuzzy --progress=names -i $podir/$lang/kdeconnect-android-store-full.po -o ./metadata/android/$lang/full_description.txt
    done
}

