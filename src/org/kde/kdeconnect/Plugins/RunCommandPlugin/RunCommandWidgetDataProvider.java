package org.kde.kdeconnect.Plugins.RunCommandPlugin;

import android.content.Context;
import android.content.Intent;
import android.view.View;
import android.widget.RemoteViews;
import android.widget.RemoteViewsService;

import org.kde.kdeconnect_tp.R;

import java.util.ArrayList;


class RunCommandWidgetDataProvider implements RemoteViewsService.RemoteViewsFactory {

    private final Context mContext;

    public RunCommandWidgetDataProvider(Context context, Intent intent) {
        mContext = context;
    }

    private boolean checkPlugin() {
        return RunCommandWidget.getCurrentDevice() != null && RunCommandWidget.getCurrentDevice().isReachable() && RunCommandWidget.getCurrentDevice().getPlugin(RunCommandPlugin.class) != null;
    }

    @Override
    public void onCreate() {
    }

    @Override
    public void onDataSetChanged() {

    }

    @Override
    public void onDestroy() {
    }

    @Override
    public int getCount() {
        return checkPlugin() ? RunCommandWidget.getCurrentDevice().getPlugin(RunCommandPlugin.class).getCommandItems().size() : 0;
    }

    @Override
    public RemoteViews getViewAt(int i) {

        RemoteViews remoteView = new RemoteViews(mContext.getPackageName(), R.layout.list_item_entry);

        if (checkPlugin() && RunCommandWidget.getCurrentDevice().getPlugin(RunCommandPlugin.class).getCommandItems().size() > i) {
            CommandEntry listItem = RunCommandWidget.getCurrentDevice().getPlugin(RunCommandPlugin.class).getCommandItems().get(i);

            final Intent configIntent = new Intent(mContext, RunCommandWidget.class);
            configIntent.setAction(RunCommandWidget.RUN_COMMAND_ACTION);
            configIntent.putExtra(RunCommandWidget.TARGET_COMMAND, listItem.getKey());
            configIntent.putExtra(RunCommandWidget.TARGET_DEVICE, RunCommandWidget.getCurrentDevice().getDeviceId());

            remoteView.setTextViewText(R.id.list_item_entry_title, listItem.getName());
            remoteView.setTextViewText(R.id.list_item_entry_summary, listItem.getCommand());
            remoteView.setViewVisibility(R.id.list_item_entry_summary, View.VISIBLE);

            remoteView.setOnClickFillInIntent(R.id.list_item_entry, configIntent);
        }

        return remoteView;
    }

    @Override
    public RemoteViews getLoadingView() {
        return null;
    }

    @Override
    public int getViewTypeCount() {
        return 1;
    }

    @Override
    public long getItemId(int i) {
        int id = 0;
        if (RunCommandWidget.getCurrentDevice() != null) {
            RunCommandPlugin plugin = RunCommandWidget.getCurrentDevice().getPlugin(RunCommandPlugin.class);
            if (plugin != null) {
                ArrayList<CommandEntry> items = plugin.getCommandItems();
                id = items.get(i).getKey().hashCode();
            }
        }
        return id;
    }

    @Override
    public boolean hasStableIds() {
        return false;
    }
}