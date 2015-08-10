/*
 * Copyright 2014 Albert Vaca Cintora <albertvaka@gmail.com>
 *
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU General Public License as
 * published by the Free Software Foundation; either version 2 of
 * the License or (at your option) version 3 or any later version
 * accepted by the membership of KDE e.V. (or its successor approved
 * by the membership of KDE e.V.), which shall act as a proxy
 * defined in Section 14 of version 3 of the license.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>. 
*/

package org.kde.kdeconnect.Helpers;

import android.content.res.Configuration;
import android.content.res.Resources;
import android.os.Build;
import android.util.Log;

import java.util.HashMap;

public class DeviceHelper {

    //from https://github.com/meetup/android-device-names
    //Converted to java using:
    //cat android_models.properties | awk -F'=' '{sub(/ *$/, "", $1)} sub(/^ */, "", $2) { if ($2 != "") print "humanReadableNames.put(\""$1"\",\"" $2 "\");"}'
    private final static HashMap<String,String> humanReadableNames = new HashMap<>();
    static {
        humanReadableNames.put("5860E","Coolpad Quattro 4G");
        humanReadableNames.put("831C","HTC One M8");
        humanReadableNames.put("9920","Star Alps S9920");
        humanReadableNames.put("A0001","OnePlus One");
        humanReadableNames.put("A1-810","Acer Iconia A1-810");
        humanReadableNames.put("ADR6300","HTC Droid Incredible");
        humanReadableNames.put("ADR6330VW","HTC Rhyme");
        humanReadableNames.put("ADR6350","HTC Droid Incredible 2");
        humanReadableNames.put("ADR6400L","HTC Thunderbolt");
        humanReadableNames.put("ADR6410LVW","HTC Droid Incredible 4G");
        humanReadableNames.put("ADR6425LVW","HTC Rezound 4G");
        humanReadableNames.put("ALCATEL_ONE_TOUCH_5035X","Alcatel One Touch X Pop");
        humanReadableNames.put("ALCATEL_ONE_TOUCH_7041X","Alcatel One Touch Pop C7");
        humanReadableNames.put("ASUS_T00J","Asus ZenFone 5");
        humanReadableNames.put("ASUS_Transformer_Pad_TF300T","Asus Transformer Pad");
        humanReadableNames.put("Aquaris_E4.5","bq Aquaris E4.5");
        humanReadableNames.put("C1905","Sony Xperia M");
        humanReadableNames.put("C2105","Sony Xperia L");
        humanReadableNames.put("C5155","Kyocera Rise");
        humanReadableNames.put("C5170","Kyocera Hydro");
        humanReadableNames.put("C5302","Xperia SP");
        humanReadableNames.put("C5303","Sony Xperia SP");
        humanReadableNames.put("C5306","Xperia SP");
        humanReadableNames.put("C6603","Sony Xperia Z");
        humanReadableNames.put("C6606","Sony Xperia Z");
        humanReadableNames.put("C6833","Sony Xperia Z Ultra");
        humanReadableNames.put("C6903","Sony Xperia Z1");
        humanReadableNames.put("C6916","Sony Xperia Z1S");
        humanReadableNames.put("CM990","Huawei Evolution III");
        humanReadableNames.put("CUBOT_ONE","Cubot One");
        humanReadableNames.put("D2005","Sony Xperia E1");
        humanReadableNames.put("D2302","Xperia M2");
        humanReadableNames.put("D2303","Sony Xperia M2");
        humanReadableNames.put("D2305","Xperia M2");
        humanReadableNames.put("D2306","Xperia M2");
        humanReadableNames.put("D2316","Xperia M2");
        humanReadableNames.put("D5503","Sony Xperia Z1");
        humanReadableNames.put("D5803","Sony Xperia Z3 Compact");
        humanReadableNames.put("D5833","Xperia Z3 Compact");
        humanReadableNames.put("D6503","Sony Xperia Z2");
        humanReadableNames.put("D6603","Sony Xperia Z3");
        humanReadableNames.put("D6653","Sony Xperia Z3");
        humanReadableNames.put("DROID2","Motorola Droid 2");
        humanReadableNames.put("DROID2_GLOBAL","Motorola Droid 2 Global");
        humanReadableNames.put("DROID3","Motorola Droid 3");
        humanReadableNames.put("DROID4","Motorola Droid 4");
        humanReadableNames.put("DROIDX","Motorola Droid X");
        humanReadableNames.put("DROID_BIONIC","Motorola Droid Bionic");
        humanReadableNames.put("DROID_Pro","Motorola Droid Pro");
        humanReadableNames.put("DROID_RAZR","Motorola Droid Razr");
        humanReadableNames.put("DROID_RAZR_HD","Motorola Droid Razr HD");
        humanReadableNames.put("DROID_X2","Motorola Droid X2");
        humanReadableNames.put("Desire_HD","HTC Desire HD");
        humanReadableNames.put("Droid","Motorola Droid");
        humanReadableNames.put("EVO","HTC Evo");
        humanReadableNames.put("GT-I8160","Samsung Galaxy Ace 2");
        humanReadableNames.put("GT-I8190","Samsung Galaxy S III Mini");
        humanReadableNames.put("GT-I8190L","Samsung Galaxy S3 Mini");
        humanReadableNames.put("GT-I8190N","Samsung Galaxy S III Mini");
        humanReadableNames.put("GT-I8260","Samsung Galaxy Core");
        humanReadableNames.put("GT-I8262","Samsung Galaxy Core");
        humanReadableNames.put("GT-I8550L","Samsung Galaxy Win");
        humanReadableNames.put("GT-I9000","Samsung Galaxy S");
        humanReadableNames.put("GT-I9001","Samsung Galaxy S Plus");
        humanReadableNames.put("GT-I9060","Samsung Galaxy Grand Neo");
        humanReadableNames.put("GT-I9063T","Samsung Galaxy Grand Neo Duos");
        humanReadableNames.put("GT-I9070","Samsung Galaxy S Advance");
        humanReadableNames.put("GT-I9082","Samsung Galaxy Grand");
        humanReadableNames.put("GT-I9100","Samsung Galaxy S II");
        humanReadableNames.put("GT-I9100M","Samsung Galaxy S II");
        humanReadableNames.put("GT-I9100P","Samsung Galaxy S II");
        humanReadableNames.put("GT-I9100T","Samsung Galaxy S II");
        humanReadableNames.put("GT-I9105P","Samsung Galaxy S2 Plus");
        humanReadableNames.put("GT-I9190","Samsung Galaxy S4 Mini");
        humanReadableNames.put("GT-I9192","Samsung Galaxy S4 Mini Duos");
        humanReadableNames.put("GT-I9195","Samsung Galaxy S4 Mini");
        humanReadableNames.put("GT-I9197","Galaxy S4 Mini");
        humanReadableNames.put("GT-I9198","Galaxy S4 Mini");
        humanReadableNames.put("GT-I9210","Galaxy S2");
        humanReadableNames.put("GT-I9295","Samsung Galaxy S4 Active");
        humanReadableNames.put("GT-I9300","Samsung Galaxy S III");
        humanReadableNames.put("GT-I9300T","Samsung Galaxy S III");
        humanReadableNames.put("GT-I9305","Samsung Galaxy S III");
        humanReadableNames.put("GT-I9305T","Samsung Galaxy S III");
        humanReadableNames.put("GT-I9500","Samsung Galaxy S4");
        humanReadableNames.put("GT-I9505","Samsung Galaxy S4");
        humanReadableNames.put("GT-I9506","Samsung Galaxy S4");
        humanReadableNames.put("GT-I9507","Samsung Galaxy S4");
        humanReadableNames.put("GT-N5110","Samsung Galaxy Note 8.0");
        humanReadableNames.put("GT-N7000","Samsung Galaxy Note");
        humanReadableNames.put("GT-N7100","Samsung Galaxy Note II");
        humanReadableNames.put("GT-N7105","Samsung Galaxy Note II");
        humanReadableNames.put("GT-N7105T","Samsung Galaxy Note II");
        humanReadableNames.put("GT-N8000","Samsung Galaxy Note 10.1");
        humanReadableNames.put("GT-N8010","Samsung Galaxy Note 10.1");
        humanReadableNames.put("GT-N8013","Samsung Galaxy Note 10.1");
        humanReadableNames.put("GT-P3100","Samsung Galaxy Tab 2");
        humanReadableNames.put("GT-P3110","Samsung Galaxy Tab 2");
        humanReadableNames.put("GT-P3113","Samsung Galaxy Tab 2 7.0");
        humanReadableNames.put("GT-P5110","Samsung Galaxy Tab 2");
        humanReadableNames.put("GT-P5113","Samsnung Galaxy Tab 2 10.1");
        humanReadableNames.put("GT-P5210","Samsung Galaxy Tab 3 10.1");
        humanReadableNames.put("GT-P7510","Samsung Galaxy Tab 10.1");
        humanReadableNames.put("GT-S5301L","Samsung Galaxy Pocket Plus");
        humanReadableNames.put("GT-S5360","Samsung Galaxy Y");
        humanReadableNames.put("GT-S5570","Samsung Galaxy Mini");
        humanReadableNames.put("GT-S5830","Samsung Galaxy Ace");
        humanReadableNames.put("GT-S5830i","Samsung Galaxy Ace");
        humanReadableNames.put("GT-S6310","Samsung Galaxy Young");
        humanReadableNames.put("GT-S6310N","Samsung Galaxy Young");
        humanReadableNames.put("GT-S6810P","Samsung Galaxy Fame");
        humanReadableNames.put("GT-S7560M","Samsung Galaxy Ace II X");
        humanReadableNames.put("GT-S7562","Samsung Galaxy S Duos");
        humanReadableNames.put("GT-S7580","Samsung Galaxy Trend Plus");
        humanReadableNames.put("Galaxy_Nexus","Samsung Galaxy Nexus");
        humanReadableNames.put("HM_1SW","Xiaomi Redmi");
        humanReadableNames.put("HTC6435LVW","HTC Droid DNA");
        humanReadableNames.put("HTC6500LVW","HTC One");
        humanReadableNames.put("HTC6525LVW","HTC One M8");
        humanReadableNames.put("HTCEVODesign4G","HTC Evo Design 4G");
        humanReadableNames.put("HTCEVOV4G","HTC Evo V 4G");
        humanReadableNames.put("HTCONE","HTC One");
        humanReadableNames.put("HTC_Desire_500","HTC Desire 500");
        humanReadableNames.put("HTC_Desire_HD_A9191","HTC Desire HD");
        humanReadableNames.put("HTC_One_mini","HTC One mini");
        humanReadableNames.put("HTC_PH39100","HTC Vivid 4G");
        humanReadableNames.put("HTC_PN071","HTC One");
        humanReadableNames.put("HTC_Sensation_Z710e","HTC Sensation");
        humanReadableNames.put("HTC_VLE_U","HTC One S");
        humanReadableNames.put("HUAWEI_G510-0251","Huawei Ascend G510");
        humanReadableNames.put("HUAWEI_P6-U06","Huawei Ascend P6");
        humanReadableNames.put("HUAWEI_Y300-0100","Huawei Ascend Y300");
        humanReadableNames.put("ISW11SC","Galaxy S2");
        humanReadableNames.put("KFJWA","Kindle Fire HD 8.9");
        humanReadableNames.put("KFJWI","Kindle Fire HD 8.9");
        humanReadableNames.put("KFOT","Kindle Fire");
        humanReadableNames.put("KFTT","Kindle Fire HD 7");
        humanReadableNames.put("L-01F","G2");
        humanReadableNames.put("LG-C800","LG myTouch Q");
        humanReadableNames.put("LG-D415","LG Optimus L90");
        humanReadableNames.put("LG-D620","LG G2 Mini");
        humanReadableNames.put("LG-D686","LG G Pro Lite Dual");
        humanReadableNames.put("LG-D800","LG G2");
        humanReadableNames.put("LG-D801","LG G2");
        humanReadableNames.put("LG-D802","LG G2");
        humanReadableNames.put("LG-D803","G2");
        humanReadableNames.put("LG-D805","G2");
        humanReadableNames.put("LG-D850","LG G3");
        humanReadableNames.put("LG-D851","LG G3");
        humanReadableNames.put("LG-D852","G3");
        humanReadableNames.put("LG-D855","LG G3");
        humanReadableNames.put("LG-E411g","LG Optimus L1 II");
        humanReadableNames.put("LG-E425g","LG Optimus L3 II");
        humanReadableNames.put("LG-E440g","LG Optimus L4 II");
        humanReadableNames.put("LG-E460","LG Optimus L5 II");
        humanReadableNames.put("LG-E610","LG Optimus L5");
        humanReadableNames.put("LG-E612g","LG Optimus L5 Dual");
        humanReadableNames.put("LG-E739","LG MyTouch e739");
        humanReadableNames.put("LG-E970","LG Optimus G");
        humanReadableNames.put("LG-E971","Optimus G");
        humanReadableNames.put("LG-E980","LG Optimus G Pro");
        humanReadableNames.put("LG-H815","G4");
        humanReadableNames.put("LG-LG730","LG Venice");
        humanReadableNames.put("LG-LS720","LG Optimus F3");
        humanReadableNames.put("LG-LS840","LG Viper");
        humanReadableNames.put("LG-LS970","LG Optimus G");
        humanReadableNames.put("LG-LS980","LG G2");
        humanReadableNames.put("LG-MS770","LG Motion 4G");
        humanReadableNames.put("LG-MS910","LG Esteem");
        humanReadableNames.put("LG-P509","LG Optimus T");
        humanReadableNames.put("LG-P760","LG Optimus L9");
        humanReadableNames.put("LG-P768","LG Optimus L9");
        humanReadableNames.put("LG-P769","LG Optimus L9");
        humanReadableNames.put("LG-P999","LG G2X P999");
        humanReadableNames.put("LG-VM696","LG Optimus Elite");
        humanReadableNames.put("LGL34C","LG Optimus Fuel");
        humanReadableNames.put("LGL55C","LG LGL55C");
        humanReadableNames.put("LGLS740","LG Volt");
        humanReadableNames.put("LGLS990","LG G3");
        humanReadableNames.put("LGMS323","LG Optimus L70");
        humanReadableNames.put("LGMS500","LG Optimus F6");
        humanReadableNames.put("LGMS769","LG Optimus L9");
        humanReadableNames.put("LS670","LG Optimus S");
        humanReadableNames.put("LT22i","Sony Xperia P");
        humanReadableNames.put("LT25i","Sony Xperia V");
        humanReadableNames.put("LT26i","Sony Xperia S");
        humanReadableNames.put("LT30p","Sony Xperia T");
        humanReadableNames.put("MB855","Motorola Photon 4G");
        humanReadableNames.put("MB860","Motorola Atrix 4G");
        humanReadableNames.put("MB865","Motorola Atrix 2");
        humanReadableNames.put("MB886","Motorola Atrix HD");
        humanReadableNames.put("ME173X","Asus MeMO Pad HD 7");
        humanReadableNames.put("MI_3W","Xiaomi Mi 3");
        humanReadableNames.put("MOTWX435KT","Motorola Triumph");
        humanReadableNames.put("N3","Star NO.1 N3");
        humanReadableNames.put("N860","ZTE Warp N860");
        humanReadableNames.put("NEXUS 4","Nexus 4");
        humanReadableNames.put("NEXUS 5","Nexus 5");
        humanReadableNames.put("NEXUS 6","Nexus 6");
        humanReadableNames.put("Nexus_10","Google Nexus 10");
        humanReadableNames.put("Nexus_4","Google Nexus 4");
        humanReadableNames.put("Nexus_7","Asus Nexus 7");
        humanReadableNames.put("Nexus_S","Samsung Nexus S");
        humanReadableNames.put("Nexus_S_4G","Samsung Nexus S 4G");
        humanReadableNames.put("Orange_Daytona","Huawei Ascend G510");
        humanReadableNames.put("PC36100","HTC Evo 4G");
        humanReadableNames.put("PG06100","HTC EVO Shift 4G");
        humanReadableNames.put("PG86100","HTC Evo 3D");
        humanReadableNames.put("PH44100","HTC Evo Design 4G");
        humanReadableNames.put("PantechP9070","Pantech Burst");
        humanReadableNames.put("QMV7A","Verizon Ellipsis 7");
        humanReadableNames.put("SAMSUNG-SGH-I317","Samsung Galaxy Note II");
        humanReadableNames.put("SAMSUNG-SGH-I337","Samsung Galaxy S4");
        humanReadableNames.put("SAMSUNG-SGH-I527","Samsung Galaxy Mega");
        humanReadableNames.put("SAMSUNG-SGH-I537","Samsung Galaxy S4 Active");
        humanReadableNames.put("SAMSUNG-SGH-I717","Samsung Galaxy Note");
        humanReadableNames.put("SAMSUNG-SGH-I727","Samsung Skyrocket");
        humanReadableNames.put("SAMSUNG-SGH-I747","Samsung Galaxy S III");
        humanReadableNames.put("SAMSUNG-SGH-I777","Samsung Galaxy S II");
        humanReadableNames.put("SAMSUNG-SGH-I897","Samsung Captivate");
        humanReadableNames.put("SAMSUNG-SGH-I927","Samsung Captivate Glide");
        humanReadableNames.put("SAMSUNG-SGH-I997","Samsung Infuse 4G");
        humanReadableNames.put("SAMSUNG-SM-G730A","Samsung Galaxy S3 Mini");
        humanReadableNames.put("SAMSUNG-SM-G870A","Samsung Galaxy S5 Active");
        humanReadableNames.put("SAMSUNG-SM-G900A","Samsung Galaxy S5");
        humanReadableNames.put("SAMSUNG-SM-G920A","Samsung Galaxy S6");
        humanReadableNames.put("SAMSUNG-SM-N900A","Samsung Galaxy Note 3");
        humanReadableNames.put("SAMSUNG-SM-N910A","Samsung Galaxy Note 4");
        humanReadableNames.put("SC-02C","Galaxy S2");
        humanReadableNames.put("SC-03E","Galaxy S3");
        humanReadableNames.put("SC-04E","Galaxy S4");
        humanReadableNames.put("SC-06D","Galaxy S3");
        humanReadableNames.put("SCH-I200","Samsung Galaxy Stellar");
        humanReadableNames.put("SCH-I337","Galaxy S4");
        humanReadableNames.put("SCH-I405","Samsung Stratosphere");
        humanReadableNames.put("SCH-I415","Samsung Galaxy Stratosphere II");
        humanReadableNames.put("SCH-I435","Samsung Galaxy S4 Mini");
        humanReadableNames.put("SCH-I500","Samsung Fascinate");
        humanReadableNames.put("SCH-I510","Samsung Droid Charge");
        humanReadableNames.put("SCH-I535","Samsung Galaxy S III");
        humanReadableNames.put("SCH-I545","Samsung Galaxy S4");
        humanReadableNames.put("SCH-I605","Samsung Galaxy Note II");
        humanReadableNames.put("SCH-I800","Samsung Galaxy Tab 7.0");
        humanReadableNames.put("SCH-I939","Galaxy S3");
        humanReadableNames.put("SCH-I959","Galaxy S4");
        humanReadableNames.put("SCH-J021","Galaxy S3");
        humanReadableNames.put("SCH-R530C","Samsung Galaxy S3");
        humanReadableNames.put("SCH-R530M","Samsung Galaxy S III");
        humanReadableNames.put("SCH-R530U","Samsung Galaxy S III");
        humanReadableNames.put("SCH-R720","Samsung Admire");
        humanReadableNames.put("SCH-R760","Galaxy S2");
        humanReadableNames.put("SCH-R970","Samsung Galaxy S4");
        humanReadableNames.put("SCH-S720C","Samsung Proclaim");
        humanReadableNames.put("SCH-S738C","Samsung Galaxy Centura");
        humanReadableNames.put("SCH-S968C","Samsung Galaxy S III");
        humanReadableNames.put("SCL21","Galaxy S3");
        humanReadableNames.put("SGH-I257M","Samsung Galaxy S4 Mini");
        humanReadableNames.put("SGH-I317M","Samsung Galaxy Note II");
        humanReadableNames.put("SGH-I337M","Samsung Galaxy S4");
        humanReadableNames.put("SGH-I727R","Samsung Galaxy S II");
        humanReadableNames.put("SGH-I747M","Samsung Galaxy S III");
        humanReadableNames.put("SGH-I757M","Galaxy S2");
        humanReadableNames.put("SGH-I777M","Galaxy S2");
        humanReadableNames.put("SGH-M919","Samsung Galaxy S4");
        humanReadableNames.put("SGH-M919N","Samsung Galaxy S4");
        humanReadableNames.put("SGH-N035","Galaxy S3");
        humanReadableNames.put("SGH-N045","Galaxy S4");
        humanReadableNames.put("SGH-N064","Galaxy S3");
        humanReadableNames.put("SGH-T399","Samsung Galaxy Light");
        humanReadableNames.put("SGH-T399N","Samsung Galaxy Light");
        humanReadableNames.put("SGH-T599N","Samsung Galaxy Exhibit");
        humanReadableNames.put("SGH-T679","Samsung Exhibit II");
        humanReadableNames.put("SGH-T769","Samsung Galaxy S Blaze");
        humanReadableNames.put("SGH-T889","Samsung Galaxy Note II");
        humanReadableNames.put("SGH-T959","Samsung Galaxy S Vibrant");
        humanReadableNames.put("SGH-T959V","Samsung Galaxy S 4G");
        humanReadableNames.put("SGH-T989","Samsung Galaxy S II");
        humanReadableNames.put("SGH-T989D","Samsung Galaxy S II");
        humanReadableNames.put("SGH-T999","Samsung Galaxy S III");
        humanReadableNames.put("SGH-T999L","Samsung Galaxy S III");
        humanReadableNames.put("SGH-T999V","Samsung Galaxy S III");
        humanReadableNames.put("SGP312","Sony Xperia Tablet Z");
        humanReadableNames.put("SHV-E210K","Samsung Galaxy S3");
        humanReadableNames.put("SHV-E210S","Samsung Galaxy S III");
        humanReadableNames.put("SHV-E250K","Samsung Galaxy Note 2");
        humanReadableNames.put("SHV-E250S","Samsung Galaxy Note II");
        humanReadableNames.put("SHV-E300","Galaxy S4");
        humanReadableNames.put("SHW-M250","Galaxy S2");
        humanReadableNames.put("SM-G3815","Samsung Galaxy Express II");
        humanReadableNames.put("SM-G386T","Samsung Galaxy Avant");
        humanReadableNames.put("SM-G386T1","Samsung Galaxy Avant");
        humanReadableNames.put("SM-G7102","Samsung Galaxy Grand II");
        humanReadableNames.put("SM-G800F","Samsung Galaxy S5 Mini");
        humanReadableNames.put("SM-G860P","Samsung Galaxy S5 Sport");
        humanReadableNames.put("SM-G900F","Samsung Galaxy S5");
        humanReadableNames.put("SM-G900H","Samsung Galaxy S5");
        humanReadableNames.put("SM-G900I","Samsung Galaxy S5");
        humanReadableNames.put("SM-G900P","Samsung Galaxy S5");
        humanReadableNames.put("SM-G900R4","Galaxy S5");
        humanReadableNames.put("SM-G900RZWAUSC","Galaxy S5");
        humanReadableNames.put("SM-G900T","Samsung Galaxy S5");
        humanReadableNames.put("SM-G900V","Samsung Galaxy S5");
        humanReadableNames.put("SM-G900W8","Samsung Galaxy S5");
        humanReadableNames.put("SM-G9200","Galaxy S6");
        humanReadableNames.put("SM-G920F","Galaxy S6");
        humanReadableNames.put("SM-G920I","Galaxy S6");
        humanReadableNames.put("SM-G920P","Samsung Galaxy S6");
        humanReadableNames.put("SM-G920R","Galaxy S6");
        humanReadableNames.put("SM-G920T","Samsung Galaxy S6");
        humanReadableNames.put("SM-G920V","Samsung Galaxy S6");
        humanReadableNames.put("SM-G920W8","Galaxy S6");
        humanReadableNames.put("SM-G9250","Galaxy S6 Edge");
        humanReadableNames.put("SM-G925A","Galaxy S6 Edge");
        humanReadableNames.put("SM-G925F","Galaxy S6 Edge");
        humanReadableNames.put("SM-G925P","Galaxy S6 Edge");
        humanReadableNames.put("SM-G925R","Galaxy S6 Edge");
        humanReadableNames.put("SM-G925T","Galaxy S6 Edge");
        humanReadableNames.put("SM-G925V","Galaxy S6 Edge");
        humanReadableNames.put("SM-G925W8","Galaxy S6 Edge");
        humanReadableNames.put("SM-N7505","Samsung Galaxy Note 3 Neo");
        humanReadableNames.put("SM-N900","Samsung Galaxy Note 3");
        humanReadableNames.put("SM-N9005","Samsung Galaxy Note 3");
        humanReadableNames.put("SM-N9006","Samsung Galaxy Note 3");
        humanReadableNames.put("SM-N900P","Samsung Galaxy Note 3");
        humanReadableNames.put("SM-N900T","Samsung Galaxy Note 3");
        humanReadableNames.put("SM-N900V","Samsung Galaxy Note 3");
        humanReadableNames.put("SM-N900W8","Samsung Galaxy Note 3");
        humanReadableNames.put("SM-N910C","Samsung Galaxy Note 4");
        humanReadableNames.put("SM-N910F","Samsung Galaxy Note 4");
        humanReadableNames.put("SM-N910G","Samsung Galaxy Note 4");
        humanReadableNames.put("SM-N910P","Samsung Galaxy Note 4");
        humanReadableNames.put("SM-N910T","Samsung Galaxy Note 4");
        humanReadableNames.put("SM-N910V","Samsung Galaxy Note 4");
        humanReadableNames.put("SM-N910W8","Samsung Galaxy Note 4");
        humanReadableNames.put("SM-P600","Samsung Galaxy Note 10.1");
        humanReadableNames.put("SM-T210R","Samsung Galaxy Tab 3 7.0");
        humanReadableNames.put("SM-T217S","Samsung Galaxy Tab 3 7.0");
        humanReadableNames.put("SM-T230NU","Samsung Galaxy Tab 4");
        humanReadableNames.put("SM-T310","Samsung Galaxy Tab 3 8.0");
        humanReadableNames.put("SM-T530NU","Samsung Galaxy Tab 4 10.1");
        humanReadableNames.put("SM-T800","Samsung Galaxy Tab S 10.5");
        humanReadableNames.put("SPH-D600","Samsung Conquer 4G");
        humanReadableNames.put("SPH-D700","Samsung Epic 4G");
        humanReadableNames.put("SPH-D710","Samsung Epic");
        humanReadableNames.put("SPH-D710BST","Samsung Galaxy S II");
        humanReadableNames.put("SPH-D710VMUB","Samsung Galaxy S II");
        humanReadableNames.put("SPH-L300","Samsung Galaxy Victory");
        humanReadableNames.put("SPH-L520","Samsung Galaxy S4 Mini");
        humanReadableNames.put("SPH-L710","Samsung Galaxy S III");
        humanReadableNames.put("SPH-L710T","Samsung Galaxy S III");
        humanReadableNames.put("SPH-L720","Samsung Galaxy S4");
        humanReadableNames.put("SPH-L720T","Samsung Galaxy S4");
        humanReadableNames.put("SPH-L900","Samsung Galaxy Note II");
        humanReadableNames.put("SPH-M820-BST","Samsung Galaxy Prevail");
        humanReadableNames.put("SPH-M830","Samsung Galaxy Rush");
        humanReadableNames.put("SPH-M840","Samsung Galaxy Prevail 2");
        humanReadableNames.put("SPH-M930BST","Samsung Transform Ultra");
        humanReadableNames.put("ST21i","Sony Xperia Tipo");
        humanReadableNames.put("ST25i","Sony Xperia U");
        humanReadableNames.put("ST26i","Sony Xperia J");
        humanReadableNames.put("Transformer_Prime_TF201","Asus Eee Pad Transformer Prime");
        humanReadableNames.put("Transformer_TF101","Asus Eee Pad Transformer");
        humanReadableNames.put("VM670","LG Optimus V");
        humanReadableNames.put("VS840_4G","LG Lucid 4G");
        humanReadableNames.put("VS870_4G","LG Lucid 2");
        humanReadableNames.put("VS910_4G","LG Revolution 4G");
        humanReadableNames.put("VS920_4G","LG Spectrum 4G");
        humanReadableNames.put("VS930_4G","LG Spectrum 2");
        humanReadableNames.put("VS980_4G","LG G2");
        humanReadableNames.put("VS985_4G","LG G3 4G");
        humanReadableNames.put("XT1022","Motorola Moto E");
        humanReadableNames.put("XT1028","Motorola Moto G");
        humanReadableNames.put("XT1030","Motorola Droid Mini");
        humanReadableNames.put("XT1031","Motorola Moto G");
        humanReadableNames.put("XT1032","Motorola Moto G");
        humanReadableNames.put("XT1033","Motorola Moto G");
        humanReadableNames.put("XT1034","Motorola Moto G");
        humanReadableNames.put("XT1039","Motorola Moto G");
        humanReadableNames.put("XT1045","Motorola Moto G");
        humanReadableNames.put("XT1049","Motorola Moto X");
        humanReadableNames.put("XT1053","Motorola Moto X");
        humanReadableNames.put("XT1056","Motorola Moto X");
        humanReadableNames.put("XT1058","Motorola Moto X");
        humanReadableNames.put("XT1060","Motorola Moto X");
        humanReadableNames.put("XT1068","Motorola Moto G");
        humanReadableNames.put("XT1080","Motorola Droid Ultra");
        humanReadableNames.put("XT1095","Motorola Moto X");
        humanReadableNames.put("XT1096","Motorola Moto X");
        humanReadableNames.put("XT1097","Motorola Moto X");
        humanReadableNames.put("XT1254","Motorola Droid Turbo");
        humanReadableNames.put("XT897","Motorola Photo Q");
        humanReadableNames.put("XT907","Motorola Droid Razr M");
        humanReadableNames.put("Xoom","Motorola Xoom");
        humanReadableNames.put("Z970","ZTE ZMax");
        humanReadableNames.put("bq_Aquaris_5","bq Aquaris 5");
        humanReadableNames.put("bq_Aquaris_5_HD","bq Aquaris 5 HD");
        humanReadableNames.put("google_sdk","Android Emulator");
        humanReadableNames.put("myTouch_4G_Slide","HTC myTouch 4G Slide");

    }

    public static String getDeviceName() {
        String deviceName = null;
        try {
            String dictName = humanReadableNames.get(Build.MODEL.replace(' ', '_'));
            if (dictName != null) {
                deviceName = dictName;
            } else if (Build.BRAND.equalsIgnoreCase("samsung")) {
                deviceName = "Samsung " + Build.MODEL;
            } else {
                deviceName = Build.BRAND;
            }
        } catch (Exception e) {
            //Some phones might not define BRAND or MODEL, ignore exceptions
            Log.e("Exception", e.getMessage());
            e.printStackTrace();
        }
        if (deviceName == null || deviceName.isEmpty()) {
            return "Android"; //Could not find a name
        } else {
            return deviceName;
        }
    }

    public static boolean isTablet() {
        Configuration config = Resources.getSystem().getConfiguration();
        //This assumes that the values for the screen sizes are consecutive, so XXLARGE > XLARGE > LARGE
        boolean isLarge = ((config.screenLayout & Configuration.SCREENLAYOUT_SIZE_MASK) >= Configuration.SCREENLAYOUT_SIZE_LARGE);
        return isLarge;
    }

}
