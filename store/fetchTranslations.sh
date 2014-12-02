#!/bin/sh

if [ -e l10n-kde4 ]; then
    cd l10n-kde4
    svn update
    cd ..
else
    svn co svn://anonsvn.kde.org/home/kde/trunk/l10n-kde4/
fi

mkdir -p localized
for subdir in `cat l10n-kde4/subdirs`; do
    if [ -e l10n-kde4/$subdir/messages/playground-base/kdeconnect-android-store.po ]; then
        #echo "Found translation for $subdir"
        cp l10n-kde4/$subdir/messages/playground-base/kdeconnect-android-store.po localized/$subdir.new.po
        if [ -e localized/$subdir.po ]; then
            #echo "Comparing with existing translation"
            if diff localized/$subdir.new.po localized/$subdir.po > /dev/null; then
                #echo "Same translation for $subdir"
                rm localized/$subdir.new.po
            else
                echo "Translation for $subdir changed"
                mv localized/$subdir.new.po localized/$subdir.po
                rm localized/$subdir.txt
                msgexec 0 < localized/$subdir.po | xargs --null -I line echo line >> localized/$subdir.txt
            fi
        else
            echo "New translation for $subdir"
            mv localized/$subdir.new.po localized/$subdir.po
            msgexec 0 < localized/$subdir.po | xargs --null -I line echo line >> localized/$subdir.txt
        fi
    fi
done
