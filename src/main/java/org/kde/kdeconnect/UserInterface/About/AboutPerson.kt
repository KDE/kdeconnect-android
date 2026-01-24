/*
 * SPDX-FileCopyrightText: 2021 Maxim Leshchenko <cnmaks90@gmail.com>
 *
 * SPDX-License-Identifier: GPL-2.0-only OR GPL-3.0-only OR LicenseRef-KDE-Accepted-GPL
 */

package org.kde.kdeconnect.UserInterface.About

import android.os.Parcel
import android.os.Parcelable

class AboutPerson @JvmOverloads constructor(val name: String, val task: Int? = null, val emailAddress: String? = null, val webAddress: String? = null) : Parcelable {
    constructor(parcel: Parcel) : this(parcel.readString().toString(), if (parcel.readByte() == 0x01.toByte()) parcel.readInt() else null, parcel.readString(), parcel.readString())

    override fun writeToParcel(parcel: Parcel, flags: Int) {
        parcel.writeString(name)

        if (task != null) {
            parcel.writeByte(0x01)
            parcel.writeInt(task)
        } else {
            parcel.writeByte(0x00)
        }

        parcel.writeString(emailAddress)
        parcel.writeString(webAddress)
    }

    override fun describeContents(): Int = 0

    companion object CREATOR : Parcelable.Creator<AboutPerson> {
        override fun createFromParcel(parcel: Parcel): AboutPerson = AboutPerson(parcel)
        override fun newArray(size: Int): Array<AboutPerson?> = arrayOfNulls(size)
    }
}
