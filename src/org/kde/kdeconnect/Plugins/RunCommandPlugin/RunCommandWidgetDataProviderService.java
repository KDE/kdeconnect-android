package org.kde.kdeconnect.Plugins.RunCommandPlugin;

import android.content.Intent;
import android.widget.RemoteViewsService;

public class RunCommandWidgetDataProviderService extends RemoteViewsService {

    @Override
    public RemoteViewsFactory onGetViewFactory(Intent intent) {

        return (new RunCommandWidgetDataProvider(RunCommandWidgetDataProviderService.this, intent));
    }
}