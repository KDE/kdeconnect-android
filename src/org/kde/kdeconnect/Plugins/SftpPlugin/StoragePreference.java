/*
 * SPDX-FileCopyrightText: 2018 Erik Duisters <e.duisters1@gmail.com>
 *
 * SPDX-License-Identifier: GPL-2.0-only OR GPL-3.0-only OR LicenseRef-KDE-Accepted-GPL
 */

package org.kde.kdeconnect.Plugins.SftpPlugin;

import android.content.Context;
import android.provider.DocumentsContract;
import android.util.AttributeSet;
import android.view.View;
import android.widget.CheckBox;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.preference.DialogPreference;
import androidx.preference.PreferenceViewHolder;

import org.kde.kdeconnect_tp.R;

public class StoragePreference extends DialogPreference {
    @Nullable
    private SftpPlugin.StorageInfo storageInfo;
    @Nullable
    private OnLongClickListener onLongClickListener;

    CheckBox checkbox;
    public boolean inSelectionMode;

    public void setInSelectionMode(boolean inSelectionMode) {
        if (this.inSelectionMode != inSelectionMode) {
            this.inSelectionMode = inSelectionMode;
            notifyChanged();
        }
    }

    public StoragePreference(Context context, AttributeSet attrs) {
        super(context, attrs);

        setDialogLayoutResource(R.layout.fragment_storage_preference_dialog);
        setWidgetLayoutResource(R.layout.preference_checkbox);
        setPersistent(false);
        inSelectionMode = false;
    }

    public StoragePreference(Context context) {
        this(context, null);
    }

    public void setOnLongClickListener(@Nullable OnLongClickListener listener) {
        this.onLongClickListener = listener;
    }

    public void setStorageInfo(@NonNull SftpPlugin.StorageInfo storageInfo) {
        if (this.storageInfo != null && this.storageInfo.equals(storageInfo)) {
            return;
        }

        if (callChangeListener(storageInfo)) {
            setStorageInfoInternal(storageInfo);
        }
    }

    private void setStorageInfoInternal(@NonNull SftpPlugin.StorageInfo storageInfo) {
        this.storageInfo = storageInfo;

        setTitle(storageInfo.displayName);
        setSummary(DocumentsContract.getTreeDocumentId(storageInfo.uri));
    }

    @Nullable
    public SftpPlugin.StorageInfo getStorageInfo() {
        return storageInfo;
    }

    @Override
    public void setDefaultValue(Object defaultValue) {
        if (defaultValue == null || defaultValue instanceof SftpPlugin.StorageInfo) {
            super.setDefaultValue(defaultValue);
        } else {
            throw new RuntimeException("StoragePreference defaultValue must be null or an instance of StfpPlugin.StorageInfo");
        }
    }

    @Override
    protected void onSetInitialValue(@Nullable Object defaultValue) {
        if (defaultValue != null) {
            setStorageInfoInternal((SftpPlugin.StorageInfo) defaultValue);
        }
    }

    @Override
    public void onBindViewHolder(@NonNull PreferenceViewHolder holder) {
        super.onBindViewHolder(holder);

        checkbox = (CheckBox) holder.itemView.findViewById(R.id.checkbox);

        checkbox.setVisibility(inSelectionMode ? View.VISIBLE : View.INVISIBLE);

        holder.itemView.setOnLongClickListener(v -> {
            if (onLongClickListener != null) {
                onLongClickListener.onLongClick(StoragePreference.this);
                return true;
            }
            return false;
        });
    }

    @Override
    protected void onClick() {
        if (inSelectionMode) {
            checkbox.setChecked(!checkbox.isChecked());
        } else {
            super.onClick();
        }
    }

    public interface OnLongClickListener {
        void onLongClick(StoragePreference storagePreference);
    }
}
