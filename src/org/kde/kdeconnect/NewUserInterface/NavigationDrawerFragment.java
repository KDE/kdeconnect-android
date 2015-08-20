package org.kde.kdeconnect.NewUserInterface;

import android.content.Context;
import android.content.Intent;
import android.content.res.Resources;
import android.app.Activity;
import android.support.v4.app.Fragment;
import android.support.v7.app.ActionBarDrawerToggle;
import android.support.v4.view.GravityCompat;
import android.support.v4.widget.DrawerLayout;
import android.content.SharedPreferences;
import android.content.res.Configuration;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.LinearLayout;
import android.widget.ListView;

import org.kde.kdeconnect.BackgroundService;
import org.kde.kdeconnect.Device;
import org.kde.kdeconnect.Helpers.CheatSheet;
import org.kde.kdeconnect.NewUserInterface.List.MaterialDeviceItem;
import org.kde.kdeconnect.UserInterface.CustomDevicesActivity;
import org.kde.kdeconnect.UserInterface.List.ListAdapter;
import org.kde.kdeconnect.UserInterface.List.SectionItem;
import org.kde.kdeconnect.UserInterface.MainSettingsActivity;
import org.kde.kdeconnect_tp.R;

import java.util.ArrayList;
import java.util.Collection;

public class NavigationDrawerFragment extends Fragment {

    /**
     * Callbacks interface that all activities using this fragment must implement.
     */
    public interface NavigationDrawerCallbacks {

        void onDeviceSelected(Device device);

    }

    private static final String STATE_SELECTED_DEVICE = "selected_device";

    private NavigationDrawerCallbacks mCallbacks;

    private NavigationDrawerCallbacks mDeviceCallback = new NavigationDrawerCallbacks() {
        @Override
        public void onDeviceSelected(Device device) {
            selectDevice(device);
        }
    };

    private ActionBarDrawerToggle mDrawerToggle;

    private DrawerLayout mDrawerLayout;
    private LinearLayout rootView;
    private ListView mDrawerListView;
    private View mFragmentContainerView;
    private SharedPreferences preferences;

    private Activity mActivity;


    private String mCurrentSelectedDeviceId = null;

    public NavigationDrawerFragment() {
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        mActivity = getActivity();
        
        preferences = mActivity.getSharedPreferences(STATE_SELECTED_DEVICE, Context.MODE_PRIVATE);

        if (savedInstanceState != null) {
            mCurrentSelectedDeviceId = savedInstanceState.getString(STATE_SELECTED_DEVICE);
        } else {
            mCurrentSelectedDeviceId = preferences.getString(STATE_SELECTED_DEVICE, null);
        }

        BackgroundService.RunCommand(mActivity, new BackgroundService.InstanceCallback() {
            @Override
            public void onServiceStart(BackgroundService service) {
                Device lastSelected = mCurrentSelectedDeviceId != null? service.getDevice(mCurrentSelectedDeviceId) : null;
                selectDevice(lastSelected);
            }
        });

    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {

        rootView = (LinearLayout) inflater.inflate(R.layout.fragment_navigation_drawer, container, false);

        mDrawerListView = (ListView) rootView.findViewById(R.id.deviceList);

        View v = rootView.findViewById(R.id.add_device_button);
        /*v.setOnTouchListener(new View.OnTouchListener() {
            @Override
            public boolean onTouch(View v, MotionEvent event) {

                Log.e("AAAAAAAAAA", "AAA" + event);
                if (event.getAction() == MotionEvent.ACTION_DOWN) {
                    v.setBackgroundColor(getResources().getColor(R.color.accent));
                } else {
                    v.setBackgroundColor(getResources().getColor(R.color.primary));
                }
                return true;
            }
        });*/
        v.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                selectDevice(null);
            }
        });

        View renameButton = rootView.findViewById(R.id.rename);
        CheatSheet.setup(renameButton); //Show contentDescription as tooltip if long-pressed

        return rootView;
    }

    public boolean isDrawerOpen() {
        return mDrawerLayout != null && mDrawerLayout.isDrawerOpen(mFragmentContainerView);
    }

    /**
     * Users of this fragment must call this method to set up the navigation drawer interactions.
     *
     * @param fragmentId   The android:id of this fragment in its activity's layout.
     * @param drawerLayout The DrawerLayout containing this fragment's UI.
     */
    public void setUp(int fragmentId, DrawerLayout drawerLayout) {
        mFragmentContainerView = getActivity().findViewById(fragmentId);
        mDrawerLayout = drawerLayout;

        // set a custom shadow that overlays the main content when the drawer opens
        mDrawerLayout.setDrawerShadow(R.drawable.drawer_shadow, GravityCompat.START);
        // set up the drawer's list view with items and click listener

        //ActionBar actionBar = getActionBar();
        //actionBar.setDisplayHomeAsUpEnabled(true);
        //actionBar.setHomeButtonEnabled(true);

        // ActionBarDrawerToggle ties together the the proper interactions
        // between the navigation drawer and the action bar app icon.
/*        mDrawerToggle = new ActionBarDrawerToggle(getActivity(), mDrawerLayout, R.string.navigation_drawer_open, R.string.navigation_drawer_close) {
            @Override
            public void onDrawerClosed(View drawerView) {
                super.onDrawerClosed(drawerView);
                if (!isAdded()) return;
                getActivity().supportInvalidateOptionsMenu();
            }
            @Override
            public void onDrawerOpened(View drawerView) {
                super.onDrawerOpened(drawerView);
                if (!isAdded()) return;
                getActivity().supportInvalidateOptionsMenu();
            }
        };

        // Defer code dependent on restoration of previous instance state.
        mDrawerLayout.post(new Runnable() {
            @Override
            public void run() {
                mDrawerToggle.syncState();
            }
        });

        mDrawerLayout.setDrawerListener(mDrawerToggle);
        */
    }

    private void selectDevice(Device device) {

        if (mDrawerLayout != null) {
            mDrawerLayout.closeDrawer(mFragmentContainerView);
        }

        if (mCallbacks != null) {
            mCallbacks.onDeviceSelected(device);
        }

        if (device != null) {
            mCurrentSelectedDeviceId = device.getDeviceId();

            preferences.edit().putString(STATE_SELECTED_DEVICE, mCurrentSelectedDeviceId).apply();
/*
            if (mDrawerListView != null) {
                mDrawerListView.setItemChecked(0, true);
            }
            */
        }
    }

    @Override
    public void onAttach(Activity activity) {
        super.onAttach(activity);
        try {
            mCallbacks = (NavigationDrawerCallbacks) activity;
        } catch (ClassCastException e) {
            throw new ClassCastException("Activity must implement NavigationDrawerCallbacks.");
        }
    }

    @Override
    public void onDetach() {
        super.onDetach();
        mCallbacks = null;
    }

    @Override
    public void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putString(STATE_SELECTED_DEVICE, mCurrentSelectedDeviceId);
    }


    @Override
    public void onActivityCreated(Bundle savedInstanceState) {
        super.onActivityCreated(savedInstanceState);
        // Needed for the drawer to work (see onOptionsItemSelected below)
        setHasOptionsMenu(true);
    }

    @Override
    public void onConfigurationChanged(Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        // Forward the new configuration the drawer toggle component.
        mDrawerToggle.onConfigurationChanged(newConfig);
    }

    //private MenuItem menuProgress;

    @Override
    public void onCreateOptionsMenu(Menu menu, MenuInflater inflater) {
        // If the drawer is open, show the global app actions in the action bar. See also
        // showGlobalContextActionBar, which controls the top-left area of the action bar.
        /*if (isDrawerOpen()) {
            inflater.inflate(R.menu.refresh, menu);
            menuProgress = menu.findItem(R.id.menu_progress);
        }*/
        super.onCreateOptionsMenu(menu, inflater);
    }

    @Override
    public boolean onOptionsItemSelected(final MenuItem item) {
        if (mDrawerToggle.onOptionsItemSelected(item)) {
            return true;
        }

        switch (item.getItemId()) {
/*            case R.id.menu_refresh:
                //updateComputerList();
                BackgroundService.RunCommand(mActivity, new BackgroundService.InstanceCallback() {
                    @Override
                    public void onServiceStart(BackgroundService service) {
                        service.onNetworkChange();
                    }
                });
                item.setVisible(false);
                menuProgress.setVisible(true);
                new Thread(new Runnable() {
                    @Override
                    public void run() {
                        try { Thread.sleep(1500); } catch (InterruptedException e) { }
                        mActivity.runOnUiThread(new Runnable() {
                            @Override
                            public void run() {
                                menuProgress.setVisible(false);
                                item.setVisible(true);
                            }
                        });
                    }
                }).start();
                break;
                */
            case R.id.menu_settings:
                startActivity(new Intent(mActivity,MainSettingsActivity.class));
                break;
            case R.id.menu_custom_device_list:
                startActivity(new Intent(mActivity, CustomDevicesActivity.class));
                break;
            default:
                break;
        }
        return true;
    }

    void updateComputerList() {

        BackgroundService.RunCommand(mActivity, new BackgroundService.InstanceCallback() {
            @Override
            public void onServiceStart(final BackgroundService service) {

                Collection<Device> devices = service.getDevices().values();
                final ArrayList<ListAdapter.Item> items = new ArrayList<>();

                SectionItem section;

                Resources res = getResources();

//                section = new SectionItem(res.getString(R.string.category_connected_devices));
//                section.isSectionEmpty = true;
//                items.add(section);
                for (Device d : devices) {
                    if (d.isReachable() && d.isPaired()) {
                        items.add(new MaterialDeviceItem(mActivity, d, mDeviceCallback));
//                        section.isSectionEmpty = false;
                    }
                }
/*
                section = new SectionItem(res.getString(R.string.category_not_paired_devices));
                section.isSectionEmpty = true;
                items.add(section);
                for (Device d : devices) {
                    if (d.isReachable() && !d.isPaired()) {
                        items.add(new MaterialDeviceItem(mActivity, d, mDeviceCallback));
                        section.isSectionEmpty = false;
                    }
                }
*/
                section = new SectionItem(res.getString(R.string.category_remembered_devices));
                section.isSectionEmpty = true;
                items.add(section);
                for (Device d : devices) {
                    if (!d.isReachable() && d.isPaired()) {
                        items.add(new MaterialDeviceItem(mActivity, d, mDeviceCallback));
                        section.isSectionEmpty = false;
                    }
                }
                if (section.isSectionEmpty) {
                    items.remove(items.size() - 1); //Remove remembered devices section if empty
                }

                mActivity.runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        mDrawerListView.setAdapter(new ListAdapter(mActivity, items));
                        mDrawerListView.setOnItemClickListener(new AdapterView.OnItemClickListener() {
                            @Override
                            public void onItemClick(AdapterView<?> adapterView, View view, int i, long l) {
                                view.performClick();
                            }
                        });
                    }
                });

            }
        });
    }
    @Override
    public void onStart() {
        super.onStart();
        BackgroundService.RunCommand(mActivity, new BackgroundService.InstanceCallback() {
            @Override
            public void onServiceStart(BackgroundService service) {
                service.onNetworkChange();
                service.setDeviceListChangedCallback(new BackgroundService.DeviceListChangedCallback() {
                    @Override
                    public void onDeviceListChanged() {
                        updateComputerList();
                    }
                });
            }
        });
    }

    @Override
    public void onStop() {
        super.onStop();
        BackgroundService.RunCommand(mActivity, new BackgroundService.InstanceCallback() {
            @Override
            public void onServiceStart(BackgroundService service) {
                service.setDeviceListChangedCallback(null);
            }
        });
    }

    @Override
    public void onResume() {
        super.onResume();
        updateComputerList();
    }


}
