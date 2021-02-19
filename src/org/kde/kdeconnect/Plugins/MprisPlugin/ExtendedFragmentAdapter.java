package org.kde.kdeconnect.Plugins.MprisPlugin;

import androidx.annotation.NonNull;
import androidx.collection.LongSparseArray;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentActivity;
import androidx.fragment.app.FragmentManager;
import androidx.lifecycle.Lifecycle;
import androidx.viewpager2.adapter.FragmentStateAdapter;

import java.lang.reflect.Field;

/**
 * Provides access to adapter fragments
 */
public abstract class ExtendedFragmentAdapter extends FragmentStateAdapter {

    public ExtendedFragmentAdapter(@NonNull FragmentActivity fragmentActivity) {
        super(fragmentActivity);
    }

    public ExtendedFragmentAdapter(@NonNull Fragment fragment) {
        super(fragment);
    }

    public ExtendedFragmentAdapter(@NonNull FragmentManager fragmentManager, @NonNull Lifecycle lifecycle) {
        super(fragmentManager, lifecycle);
    }

    protected LongSparseArray<Fragment> getFragments() {
        try {
            Field fragmentsField = FragmentStateAdapter.class.getDeclaredField("mFragments");
            fragmentsField.setAccessible(true);
            Object fieldData = fragmentsField.get(this);
            if (fieldData instanceof LongSparseArray) {
                //noinspection unchecked
                return (LongSparseArray<Fragment>) fieldData;
            }
        } catch (NoSuchFieldException e) {
            e.printStackTrace();
        } catch (IllegalAccessException e) {
            e.printStackTrace();
        }

        return null;
    }

    protected Fragment getFragment(int position) {
        LongSparseArray<Fragment> adapterFragments = getFragments();
        if (adapterFragments == null) return null;

        return adapterFragments.get(position);
    }
}
