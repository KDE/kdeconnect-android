package org.kde.kdeconnect.Plugins.PowerPlugin;

import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.service.controls.Control;
import android.service.controls.ControlsProviderService;
import android.service.controls.DeviceTypes;
import android.service.controls.actions.BooleanAction;
import android.service.controls.actions.ControlAction;

import androidx.annotation.NonNull;
import androidx.annotation.RequiresApi;


import org.reactivestreams.FlowAdapters;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Flow;
import java.util.function.Consumer;

import io.reactivex.Flowable;
import io.reactivex.processors.ReplayProcessor;

@RequiresApi(30)
public class PowerControlsService extends ControlsProviderService {

    private ReplayProcessor updatePublisher;
    ArrayList<Control> controls =new ArrayList<>();

    final static String MY_TEST_ID = "myAwesomeId";

    @NonNull
    @Override
    public Flow.Publisher createPublisherForAllAvailable() {
        Context context=getBaseContext();
        Intent i=new Intent();
        PendingIntent pi=PendingIntent.getActivity(context,1,i,PendingIntent.FLAG_UPDATE_CURRENT);


        Control control = new Control.StatelessBuilder(MY_TEST_ID, pi)
            .setTitle("My device")
            .setSubtitle("KDE Connect")
            .setDeviceType(DeviceTypes.TYPE_DISPLAY) //There's no type "computer"
            .build();

        controls.add(control);

        return FlowAdapters.toFlowPublisher(Flowable.fromIterable(controls));
    }


    @NonNull
    @Override
    public Flow.Publisher<Control> createPublisherFor(@NonNull List<String> controlIds) {

        Context context = getBaseContext();
        /* Fill in details for the activity related to this device. On long press,
         * this Intent will be launched in a bottomsheet. Please design the activity
         * accordingly to fit a more limited space (about 2/3 screen height).
         */
        Intent i = new Intent();
        PendingIntent pi = PendingIntent.getActivity(context, 1, i, PendingIntent.FLAG_UPDATE_CURRENT);

        updatePublisher = ReplayProcessor.create();

        // For each controlId in controlIds
        if (controlIds.contains(MY_TEST_ID)) {

            Control control = new Control.StatefulBuilder(MY_TEST_ID, pi)
                .setTitle("My device")
                .setSubtitle("KDE Connect")
                .setDeviceType(DeviceTypes.TYPE_DISPLAY) //There's no type "computer"
                .setStatus(Control.STATUS_OK) // For example, Control.STATUS_OK
                .build();

            updatePublisher.onNext(control);
        }

        return FlowAdapters.toFlowPublisher(updatePublisher);
    }

    @Override
    public void performControlAction(@NonNull String controlId, @NonNull ControlAction action, @NonNull Consumer consumer) {

        /* First, locate the control identified by the controlId. Once it is located, you can
         * interpret the action appropriately for that specific device. For instance, the following
         * assumes that the controlId is associated with a light, and the light can be turned on
         * or off.
         */
        if (action instanceof BooleanAction) {

            // Inform SystemUI that the action has been received and is being processed
            consumer.accept(ControlAction.RESPONSE_OK);


            /* This is where application logic/network requests would be invoked to update the state of
             * the device.
             * After updating, the application should use the publisher to update SystemUI with the new
             * state.
             */

        }
    }
}
