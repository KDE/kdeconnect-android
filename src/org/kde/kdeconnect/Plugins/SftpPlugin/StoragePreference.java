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

import android.content.Context;
import android.os.Build;
import android.provider.DocumentsContract;
import android.util.AttributeSet;
import android.view.View;
import android.widget.CheckBox;

import org.kde.kdeconnect_tp.R;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.preference.DialogPreference;
import androidx.preference.PreferenceViewHolder;
import butterknife.BindView;
import butterknife.ButterKnife;

public class StoragePreference extends DialogPreference {
    @Nullable
    private SftpPlugin.StorageInfo storageInfo;
    @Nullable
    private OnLongClickListener onLongClickListener;

    @BindView(R.id.checkbox) CheckBox checkbox;
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
        if (Build.VERSION.SDK_INT < 21) {
            setSummary(storageInfo.uri.getPath());
        } else {
            setSummary(DocumentsContract.getTreeDocumentId(storageInfo.uri));
        }
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
    public void onBindViewHolder(PreferenceViewHolder holder) {
        super.onBindViewHolder(holder);

        ButterKnife.bind(this, holder.itemView);

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
