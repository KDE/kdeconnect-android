/*
 * SPDX-FileCopyrightText: 2021 Maxim Leshchenko <cnmaks90@gmail.com>
 *
 * SPDX-License-Identifier: GPL-2.0-only OR GPL-3.0-only OR LicenseRef-KDE-Accepted-GPL
 */

package org.kde.kdeconnect.UserInterface.About

import android.os.Parcel
import android.os.Parcelable

class AboutData(var name: String, var icon: Int, var versionName: String, var bugURL: String? = null,
                var websiteURL: String? = null, var sourceCodeURL: String? = null, var donateURL: String? = null,
                var authorsFooterText: Int? = null) : Parcelable {
    val authors: MutableList<AboutPerson> = mutableListOf()

    constructor(parcel: Parcel) : this(parcel.readString()!!, parcel.readInt(), parcel.readString()!!,
                                       parcel.readString(), parcel.readString(), parcel.readString(), parcel.readString(),
                                       if (parcel.readByte() == 0x01.toByte()) parcel.readInt() else null) {
        parcel.readList(authors as List<*>, AboutPerson::class.java.classLoader)
    }

    companion object CREATOR : Parcelable.Creator<AboutData> {
        override fun createFromParcel(parcel: Parcel): AboutData = AboutData(parcel)
        override fun newArray(size: Int): Array<AboutData?> = arrayOfNulls(size)
    }

    override fun writeToParcel(parcel: Parcel, flags: Int) {
        parcel.writeString(name)
        parcel.writeInt(icon)
        parcel.writeString(versionName)
        parcel.writeList(authors.toList())

        parcel.writeString(bugURL)
        parcel.writeString(websiteURL)
        parcel.writeString(sourceCodeURL)
        parcel.writeString(donateURL)

        authorsFooterText?.let {
            parcel.writeByte(0x01)
            parcel.writeInt(it)
        } ?: parcel.writeByte(0x00)
    }

    override fun describeContents(): Int = 0
}