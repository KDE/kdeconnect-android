package org.kde.kdeconnect.Plugins.RunCommandPlugin;

import android.content.Intent;
import android.os.Build;
import android.support.annotation.RequiresApi;
import android.widget.RemoteViewsService;

@RequiresApi( api = Build.VERSION_CODES.ICE_CREAM_SANDWICH )
public class RunCommandWidgetDataProviderService extends RemoteViewsService {

    @Override
    public RemoteViewsFactory onGetViewFactory(Intent intent) {

        return (new RunCommandWidgetDataProvider(RunCommandWidgetDataProviderService.this, intent));
    }
}