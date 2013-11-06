package org.kde.kdeconnect.Helpers;

import android.content.res.Configuration;
import android.content.res.Resources;
import android.os.Build;

import java.util.HashMap;

public class DeviceHelper {

    //from https://github.com/meetup/android-device-names
    //Converted to java using:
    //cat android_models.properties | awk -F'=' '{sub(/ *$/, "", $1)} sub(/^ */, "", $2) { if ($2 != "") print "humanReadableNames.put(\""$1"\",\"" $2 "\");"}'
    private static HashMap<String,String> humanReadableNames = new HashMap<String,String>();
    static {
        humanReadableNames.put("5860E","Coolpad Quattro 4G");
        humanReadableNames.put("ADR6300","HTC Droid Incredible");
        humanReadableNames.put("ADR6330VW","HTC Rhyme");
        humanReadableNames.put("ADR6350","HTC Droid Incredible 2");
        humanReadableNames.put("ADR6400L","HTC Thunderbolt");
        humanReadableNames.put("ADR6410LVW","HTC Droid Incredible 4G");
        humanReadableNames.put("ADR6425LVW","HTC Rezound 4G");
        humanReadableNames.put("ASUS_Transformer_Pad_TF300T","Asus Transformer Pad");
        humanReadableNames.put("C5155","Kyocera Rise");
        humanReadableNames.put("C5170","Kyocera Hydro");
        humanReadableNames.put("C6603","Sony Xperia Z");
        humanReadableNames.put("Desire_HD","HTC Desire HD");
        humanReadableNames.put("DROID2_GLOBAL","Motorola Droid 2 Global");
        humanReadableNames.put("DROID2","Motorola Droid 2");
        humanReadableNames.put("DROID3","Motorola Droid 3");
        humanReadableNames.put("DROID4","Motorola Droid 4");
        humanReadableNames.put("DROID_BIONIC","Motorola Droid Bionic");
        humanReadableNames.put("Droid","Motorola Droid");
        humanReadableNames.put("DROID_Pro","Motorola Droid Pro");
        humanReadableNames.put("DROID_RAZR_HD","Motorola Droid Razr HD");
        humanReadableNames.put("DROID_RAZR","Motorola Droid Razr");
        humanReadableNames.put("DROID_X2","Motorola Droid X2");
        humanReadableNames.put("DROIDX","Motorola Droid X");
        humanReadableNames.put("EVO","HTC Evo");
        humanReadableNames.put("Galaxy_Nexus","Samsung Galaxy Nexus");
        humanReadableNames.put("google_sdk","Android Emulator");
        humanReadableNames.put("GT-I8160","Samsung Galaxy Ace 2");
        humanReadableNames.put("GT-I8190","Samsung Galaxy S III Mini");
        humanReadableNames.put("GT-I9000","Samsung Galaxy S");
        humanReadableNames.put("GT-I9001","Samsung Galaxy S Plus");
        humanReadableNames.put("GT-I9100M","Samsung Galaxy S II");
        humanReadableNames.put("GT-I9100P","Samsung Galaxy S II");
        humanReadableNames.put("GT-I9100","Samsung Galaxy S II");
        humanReadableNames.put("GT-I9100T","Samsung Galaxy S II");
        humanReadableNames.put("GT-I9300","Samsung Galaxy S III");
        humanReadableNames.put("GT-I9300T","Samsung Galaxy S III");
        humanReadableNames.put("GT-I9305","Samsung Galaxy S III");
        humanReadableNames.put("GT-I9505","Samsung Galaxy S 4");
        humanReadableNames.put("GT-N7000","Samsung Galaxy Note");
        humanReadableNames.put("GT-N7100","Samsung Galaxy Note II");
        humanReadableNames.put("GT-N7105","Samsung Galaxy Note II");
        humanReadableNames.put("GT-N8013","Samsung Galaxy Note 10.1");
        humanReadableNames.put("GT-P3113","Samsung Galaxy Tab 2 7.0");
        humanReadableNames.put("GT-P5113","Samsnung Galaxy Tab 2 10.1");
        humanReadableNames.put("GT-P7510","Samsung Galaxy Tab 10.1");
        humanReadableNames.put("GT-S5360","Samsung Galaxy Y");
        humanReadableNames.put("GT-S5570","Samsung Galaxy Mini");
        humanReadableNames.put("GT-S5830i","Samsung Galaxy Ace");
        humanReadableNames.put("GT-S5830","Samsung Galaxy Ace");
        humanReadableNames.put("HTC6435LVW","HTC Droid DNA");
        humanReadableNames.put("HTC_Desire_HD_A9191","HTC Desire HD");
        humanReadableNames.put("HTCEVODesign4G","HTC Evo Design 4G");
        humanReadableNames.put("HTCEVOV4G","HTC Evo V 4G");
        humanReadableNames.put("HTCONE","HTC One");
        humanReadableNames.put("HTC_PH39100","HTC Vivid 4G");
        humanReadableNames.put("HTC_Sensation_Z710e","HTC Sensation");
        humanReadableNames.put("HTC_VLE_U","HTC One S");
        humanReadableNames.put("KFJWA","Kindle Fire HD 8.9");
        humanReadableNames.put("KFJWI","Kindle Fire HD 8.9");
        humanReadableNames.put("KFOT","Kindle Fire");
        humanReadableNames.put("KFTT","Kindle Fire HD 7");
        humanReadableNames.put("LG-C800","LG myTouch Q");
        humanReadableNames.put("LG-E739","LG MyTouch e739");
        humanReadableNames.put("LGL55C","LG LGL55C");
        humanReadableNames.put("LG-LS970","LG Optimus G");
        humanReadableNames.put("LG-MS770","LG Motion 4G");
        humanReadableNames.put("LG-MS910","LG Esteem");
        humanReadableNames.put("LG-P509","LG Optimus T");
        humanReadableNames.put("LG-P769","LG Optimus L9");
        humanReadableNames.put("LG-P999","LG G2X P999");
        humanReadableNames.put("LG-VM696","LG Optimus Elite");
        humanReadableNames.put("LS670","LG Optimus S");
        humanReadableNames.put("LT26i","Sony Xperia S");
        humanReadableNames.put("MB855","Motorola Photon 4G");
        humanReadableNames.put("MB860","Motorola Atrix 4G");
        humanReadableNames.put("MB865","Motorola Atrix 2");
        humanReadableNames.put("MB886","Motorola Atrix HD");
        humanReadableNames.put("MOTWX435KT","Motorola Triumph");
        humanReadableNames.put("myTouch_4G_Slide","HTC myTouch 4G Slide");
        humanReadableNames.put("N860","ZTE Warp N860");
        humanReadableNames.put("Nexus_10","Google Nexus 10");
        humanReadableNames.put("Nexus_4","Google Nexus 4");
        humanReadableNames.put("Nexus_7","Asus Nexus 7");
        humanReadableNames.put("Nexus_S_4G","Samsung Nexus S 4G");
        humanReadableNames.put("Nexus_S","Samsung Nexus S");
        humanReadableNames.put("PantechP9070","Pantech Burst");
        humanReadableNames.put("PC36100","HTC Evo 4G");
        humanReadableNames.put("PG06100","HTC EVO Shift 4G");
        humanReadableNames.put("PG86100","HTC Evo 3D");
        humanReadableNames.put("PH44100","HTC Evo Design 4G");
        humanReadableNames.put("SAMSUNG-SGH-I317","Samsung Galaxy Note II");
        humanReadableNames.put("SAMSUNG-SGH-I337","Samsung Galaxy S 4");
        humanReadableNames.put("SAMSUNG-SGH-I717","Samsung Galaxy Note");
        humanReadableNames.put("SAMSUNG-SGH-I727","Samsung Skyrocket");
        humanReadableNames.put("SAMSUNG-SGH-I747","Samsung Galaxy S III");
        humanReadableNames.put("SAMSUNG-SGH-I777","Samsung Galaxy S II");
        humanReadableNames.put("SAMSUNG-SGH-I897","Samsung Captivate");
        humanReadableNames.put("SAMSUNG-SGH-I927","Samsung Captivate Glide");
        humanReadableNames.put("SAMSUNG-SGH-I997","Samsung Infuse 4G");
        humanReadableNames.put("SCH-I200","Samsung Galaxy Stellar");
        humanReadableNames.put("SCH-I405","Samsung Stratosphere");
        humanReadableNames.put("SCH-I500","Samsung Fascinate");
        humanReadableNames.put("SCH-I510","Samsung Droid Charge");
        humanReadableNames.put("SCH-I535","Samsung Galaxy S III");
        humanReadableNames.put("SCH-I545","Samsung Galaxy S 4");
        humanReadableNames.put("SCH-I605","Samsung Galaxy Note II");
        humanReadableNames.put("SCH-I800","Samsung Galaxy Tab 7.0");
        humanReadableNames.put("SCH-R530M","Samsung Galaxy S III");
        humanReadableNames.put("SCH-R530U","Samsung Galaxy S III");
        humanReadableNames.put("SCH-R720","Samsung Admire");
        humanReadableNames.put("SCH-S720C","Samsung Proclaim");
        humanReadableNames.put("SGH-I317M","Samsung Galaxy Note II");
        humanReadableNames.put("SGH-I727R","Samsung Galaxy S II");
        humanReadableNames.put("SGH-I747M","Samsung Galaxy S III");
        humanReadableNames.put("SGH-M919","Samsung Galaxy S 4");
        humanReadableNames.put("SGH-T679","Samsung Exhibit II");
        humanReadableNames.put("SGH-T769","Samsung Galaxy S Blaze");
        humanReadableNames.put("SGH-T889","Samsung Galaxy Note II");
        humanReadableNames.put("SGH-T959","Samsung Galaxy S Vibrant");
        humanReadableNames.put("SGH-T959V","Samsung Galaxy S 4G");
        humanReadableNames.put("SGH-T989D","Samsung Galaxy S II");
        humanReadableNames.put("SGH-T989","Samsung Galaxy S II");
        humanReadableNames.put("SGH-T999","Samsung Galaxy S III");
        humanReadableNames.put("SPH-D600","Samsung Conquer 4G");
        humanReadableNames.put("SPH-D700","Samsung Epic 4G");
        humanReadableNames.put("SPH-D710BST","Samsung Galaxy S II");
        humanReadableNames.put("SPH-D710","Samsung Epic");
        humanReadableNames.put("SPH-D710VMUB","Samsung Galaxy S II");
        humanReadableNames.put("SPH-L710","Samsung Galaxy S III");
        humanReadableNames.put("SPH-L720","Samsung Galaxy S 4");
        humanReadableNames.put("SPH-L900","Samsung Galaxy Note II");
        humanReadableNames.put("SPH-M820-BST","Samsung Galaxy Prevail");
        humanReadableNames.put("SPH-M930BST","Samsung Transform Ultra");
        humanReadableNames.put("Transformer_Prime_TF201","Asus Eee Pad Transformer Prime");
        humanReadableNames.put("Transformer_TF101","Asus Eee Pad Transformer");
        humanReadableNames.put("VM670","LG Optimus V");
        humanReadableNames.put("VS840_4G","LG Lucid 4G");
        humanReadableNames.put("VS910_4G","LG Revolution 4G");
        humanReadableNames.put("VS920_4G","LG Spectrum 4G");
        humanReadableNames.put("Xoom","Motorola Xoom");
        humanReadableNames.put("XT907","Motorola Droid Razr M");
    }

    public static String getDeviceName() {

        String dictName = humanReadableNames.get(Build.MODEL.replace(' ','_'));
        if (dictName != null) return dictName;

        if (Build.BRAND.equals("samsung") || Build.BRAND.equals("Samsung")) {
            return "Samsung" + Build.MODEL;
        }

        return Build.MODEL;

    }

    public static boolean isTablet() {
        Configuration config = Resources.getSystem().getConfiguration();
        //This assumes that the values for the screen sizes are consecutive, so XXLARGE > XLARGE > LARGE
        boolean isLarge = ((config.screenLayout & Configuration.SCREENLAYOUT_SIZE_MASK) >= Configuration.SCREENLAYOUT_SIZE_LARGE);
        return isLarge;
    }

}
