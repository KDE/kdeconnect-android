/*
 * Copyright 2018 Erik Duisters <e.duisters1@gmail.com>
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
 * along with this program. If not, see <http://www.gnu.org/licenses/>.
 */

package org.kde.kdeconnect.Plugins.SftpPlugin;

import org.apache.sshd.common.file.SshFile;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Calendar;
import java.util.Collections;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

//TODO: ls .. and ls / only show .. and / respectively I would expect a listing
//TODO: cd .. to / does not work and prints "Can't change directory: Can't check target"
class RootFile implements SshFile {
    private final boolean exists;
    private final String userName;
    private final List<SshFile> files;

    RootFile(List<SshFile> files, String userName, boolean exits) {
        this.files = files;
        this.userName = userName;
        this.exists = exits;
    }

    public String getAbsolutePath() {
        return "/";
    }

    public String getName() {
        return "/";
    }

    public Map<Attribute, Object> getAttributes(boolean followLinks) {
        Map<Attribute, Object> attrs = new HashMap<>();

        attrs.put(Attribute.Size, 0);
        attrs.put(Attribute.Owner, userName);
        attrs.put(Attribute.Group, userName);

        EnumSet<Permission> p = EnumSet.noneOf(Permission.class);
        p.add(Permission.UserExecute);
        p.add(Permission.GroupExecute);
        p.add(Permission.OthersExecute);
        attrs.put(Attribute.Permissions, p);

        long now = Calendar.getInstance().getTimeInMillis();
        attrs.put(Attribute.LastAccessTime, now);
        attrs.put(Attribute.LastModifiedTime, now);

        attrs.put(Attribute.IsSymbolicLink, false);
        attrs.put(Attribute.IsDirectory, true);
        attrs.put(Attribute.IsRegularFile, false);

        return attrs;
    }

    public void setAttributes(Map<Attribute, Object> attributes) {
    }

    public Object getAttribute(Attribute attribute, boolean followLinks) {
        return null;
    }

    public void setAttribute(Attribute attribute, Object value) {
    }

    public String readSymbolicLink() {
        return "";
    }

    public void createSymbolicLink(SshFile destination) {
    }

    public String getOwner() {
        return null;
    }

    public boolean isDirectory() {
        return true;
    }

    public boolean isFile() {
        return false;
    }

    public boolean doesExist() {
        return exists;
    }

    public boolean isReadable() {
        return true;
    }

    public boolean isWritable() {
        return false;
    }

    public boolean isExecutable() {
        return true;
    }

    public boolean isRemovable() {
        return false;
    }

    public SshFile getParentFile() {
        return this;
    }

    public long getLastModified() {
        return 0;
    }

    public boolean setLastModified(long time) {
        return false;
    }

    public long getSize() {
        return 0;
    }

    public boolean mkdir() {
        return false;
    }

    public boolean delete() {
        return false;
    }

    public boolean create() {
        return false;
    }

    public void truncate() {
    }

    public boolean move(SshFile destination) {
        return false;
    }

    public List<SshFile> listSshFiles() {
        return Collections.unmodifiableList(files);
    }

    public OutputStream createOutputStream(long offset) {
        return null;
    }

    public InputStream createInputStream(long offset) {
        return null;
    }

    public void handleClose() {
    }
}
