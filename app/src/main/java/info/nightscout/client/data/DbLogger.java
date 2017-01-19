package info.nightscout.client.data;

import android.content.Intent;
import android.content.pm.ResolveInfo;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

import info.nightscout.androidaps.Config;
import info.nightscout.androidaps.MainApp;
import info.nightscout.androidaps.R;
import info.nightscout.utils.ToastUtils;

/**
 * Created by mike on 02.07.2016.
 */
public class DbLogger {
    public static void dbAdd(Intent intent, String data, Class sender) {
        Logger log = LoggerFactory.getLogger(sender);
        List<ResolveInfo> q = MainApp.instance().getApplicationContext().getPackageManager().queryBroadcastReceivers(intent, 0);
        if (q.size() < 1) {
            ToastUtils.showToastInUiThread(MainApp.instance().getApplicationContext(),MainApp.sResources.getString(R.string.nsclientnotinstalled));
            log.error("DBADD No receivers");
        } else if (Config.logNSUpload)
            log.debug("DBADD dbAdd " + q.size() + " receivers " + data);
    }

   public static void dbRemove(Intent intent, String data, Class sender) {
        Logger log = LoggerFactory.getLogger(sender);
        List<ResolveInfo> q = MainApp.instance().getApplicationContext().getPackageManager().queryBroadcastReceivers(intent, 0);
        if (q.size() < 1) {
            ToastUtils.showToastInUiThread(MainApp.instance().getApplicationContext(),MainApp.sResources.getString(R.string.nsclientnotinstalled));
            log.error("DBREMOVE No receivers");
        } else if (Config.logNSUpload)
            log.debug("DBREMOVE dbRemove " + q.size() + " receivers " + data);
    }
}
