package com.genonbeta.TrebleShot.dialog;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.Context;
import android.content.DialogInterface;
import android.content.SharedPreferences;
import android.os.Handler;
import android.os.Looper;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.CompoundButton;
import android.widget.TextView;
import android.widget.Toast;

import com.genonbeta.CoolSocket.CoolSocket;
import com.genonbeta.TrebleShot.R;
import com.genonbeta.TrebleShot.adapter.EstablishConnectionDialog;
import com.genonbeta.TrebleShot.callback.OnDeviceSelectedListener;
import com.genonbeta.TrebleShot.config.AppConfig;
import com.genonbeta.TrebleShot.config.Keyword;
import com.genonbeta.TrebleShot.database.AccessDatabase;
import com.genonbeta.TrebleShot.object.NetworkDevice;
import com.genonbeta.TrebleShot.service.WorkerService;
import com.genonbeta.TrebleShot.util.UpdateUtils;
import com.genonbeta.android.framework.io.DocumentFile;

import org.json.JSONObject;

import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.util.ArrayList;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.widget.AppCompatImageView;
import androidx.appcompat.widget.SwitchCompat;

/**
 * Created by: veli
 * Date: 5/18/17 5:16 PM
 */

public class DeviceInfoDialog extends AlertDialog.Builder
{
    public static final String TAG = DeviceInfoDialog.class.getSimpleName();
    public static final int JOB_RECEIVE_UPDATE = 1;

    public DeviceInfoDialog(@NonNull final Activity activity, final AccessDatabase database, final SharedPreferences sharedPreferences, final NetworkDevice device)
    {
        super(activity);

        try {
            database.reconstruct(device);

            @SuppressLint("InflateParams")
            View rootView = LayoutInflater.from(activity).inflate(R.layout.layout_device_info, null);

            TextView notSupportedText = rootView.findViewById(R.id.device_info_not_supported_text);
            TextView modelText = rootView.findViewById(R.id.device_info_brand_and_model);
            TextView versionText = rootView.findViewById(R.id.device_info_version);
            AppCompatImageView updateButton = rootView.findViewById(R.id.device_info_get_update_button);
            SwitchCompat accessSwitch = rootView.findViewById(R.id.device_info_access_switcher);
            final SwitchCompat trustSwitch = rootView.findViewById(R.id.device_info_trust_switcher);

            if (device.versionNumber < AppConfig.SUPPORTED_MIN_VERSION)
                notSupportedText.setVisibility(View.VISIBLE);

            updateButton.setOnClickListener(new View.OnClickListener()
            {
                @Override
                public void onClick(View v)
                {
                    new EstablishConnectionDialog(activity, device, new OnDeviceSelectedListener()
                    {
                        @Override
                        public void onDeviceSelected(final NetworkDevice.Connection connection, ArrayList<NetworkDevice.Connection> availableInterfaces)
                        {
                            WorkerService.run(activity, new WorkerService.RunningTask(TAG, JOB_RECEIVE_UPDATE)
                            {
                                @Override
                                public void onRun()
                                {
                                    publishStatusText(getService().getString(R.string.mesg_ongoingUpdateDownload));

                                    try {
                                        final Context context = getContext();
                                        final DocumentFile receivedFile = UpdateUtils.receiveUpdate(activity, sharedPreferences, device, getInterrupter(), new UpdateUtils.OnConnectionReadyListener()
                                        {
                                            @Override
                                            public void onConnectionReady(ServerSocket socket)
                                            {
                                                CoolSocket.connect(new CoolSocket.Client.ConnectionHandler()
                                                {
                                                    @Override
                                                    public void onConnect(CoolSocket.Client client)
                                                    {
                                                        try {
                                                            CoolSocket.ActiveConnection activeConnection = client.connect(new InetSocketAddress(connection.ipAddress, AppConfig.SERVER_PORT_COMMUNICATION), AppConfig.DEFAULT_SOCKET_TIMEOUT);
                                                            activeConnection.reply(new JSONObject().put(Keyword.REQUEST, Keyword.BACK_COMP_REQUEST_SEND_UPDATE).toString());

                                                            CoolSocket.ActiveConnection.Response response = activeConnection.receive();
                                                            JSONObject responseJSON = new JSONObject(response.response);

                                                            if (!responseJSON.has(Keyword.RESULT) || !responseJSON.getBoolean(Keyword.RESULT))
                                                                throw new Exception("Not the answer we were looking for.");
                                                        } catch (Exception e) {
                                                            e.printStackTrace();
                                                            getInterrupter().interrupt(false);
                                                        }
                                                    }
                                                });
                                            }
                                        });

                                        new Handler(Looper.getMainLooper()).post(new Runnable()
                                        {
                                            @Override
                                            public void run()
                                            {
                                                Toast.makeText(context, receivedFile == null
                                                        ? R.string.mesg_somethingWentWrong
                                                        : R.string.mesg_updateDownloadComplete, Toast.LENGTH_LONG).show();
                                            }
                                        });
                                    } catch (Exception e) {
                                        e.printStackTrace();
                                    }
                                }
                            });
                        }
                    }).show();
                }
            });

            modelText.setText(String.format("%s %s", device.brand.toUpperCase(), device.model.toUpperCase()));
            versionText.setText(device.versionName);

            accessSwitch.setChecked(!device.isRestricted);

            trustSwitch.setEnabled(!device.isRestricted);
            trustSwitch.setChecked(device.isTrusted);

            accessSwitch.setOnCheckedChangeListener(
                    new CompoundButton.OnCheckedChangeListener()
                    {
                        @Override
                        public void onCheckedChanged(CompoundButton button, boolean isChecked)
                        {
                            device.isRestricted = !isChecked;
                            database.publish(device);

                            trustSwitch.setEnabled(isChecked);
                        }
                    }
            );

            trustSwitch.setOnCheckedChangeListener(
                    new CompoundButton.OnCheckedChangeListener()
                    {
                        @Override
                        public void onCheckedChanged(CompoundButton button, boolean isChecked)
                        {
                            device.isTrusted = isChecked;
                            database.publish(device);
                        }
                    }
            );

            setTitle(device.nickname);
            setView(rootView);
            setPositiveButton(R.string.butn_close, null);

            setNegativeButton(R.string.butn_remove, new DialogInterface.OnClickListener()
            {
                @Override
                public void onClick(DialogInterface dialog, int which)
                {
                    new RemoveDeviceDialog(activity, device)
                            .show();
                }
            });
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
