package de.tudarmstadt.informatik.hostage.services;

import android.app.Service;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Binder;
import android.os.IBinder;
import android.preference.PreferenceManager;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

import de.tudarmstadt.informatik.hostage.Hostage;
import de.tudarmstadt.informatik.hostage.location.MyLocationManager;
import de.tudarmstadt.informatik.hostage.logging.AttackRecord;
import de.tudarmstadt.informatik.hostage.logging.Logger;
import de.tudarmstadt.informatik.hostage.logging.MessageRecord;
import de.tudarmstadt.informatik.hostage.logging.NetworkRecord;
import de.tudarmstadt.informatik.hostage.logging.Record;
import de.tudarmstadt.informatik.hostage.persistence.HostageDBOpenHelper;
import de.tudarmstadt.informatik.hostage.ui.activity.MainActivity;
import de.tudarmstadt.informatik.hostage.ui.model.LogFilter;

/**
 * Multistage attack detection service
 */
public class MultiStage extends Service {
    @Override
    public IBinder onBind(Intent intent) {

        return null;
    }


    @Override
    public int onStartCommand(Intent intent, int flags, int startid) {

        fetchData();

        return 1;
    }


    private HostageDBOpenHelper mDBOpenHelper;

    StringBuilder message;


    private String bssid = "";

    private String ssid = "";


    private String externalIP;
    String stackRemoteIP;
    String stackLocalIp;
    String stackProtocol;
    int stackRport;
    int stackLport;
    String stackssid;
    String stackbssid;

    //fetch data of records of last 10 mins
    public Boolean fetchData() {

        Long currentTime = System.currentTimeMillis();

        int fetchInterval = 1000 * 60 * 30; // setInterval in millis  Millisec * Second * Minute

        Long filterTime = (currentTime - fetchInterval);

        LogFilter filter = new LogFilter();

        filter.setAboveTimestamp(filterTime);

        this.mDBOpenHelper = new HostageDBOpenHelper(MainActivity.getInstance().getBaseContext());
        List<Record> recordArray = mDBOpenHelper.getRecordsForFilter(filter);
        Collections.sort(recordArray, new Comparator<Record>() {
            public int compare(Record one, Record other) {
                return one.getRemoteIP().compareTo(other.getRemoteIP());
            }
        });
        ArrayList<Stackbean> b = new ArrayList<Stackbean>();
        String prevRemoteIP = "";
        String prevProt = "";
        int prevlport = 0;
        int prevrport = 0;
        String prevLocalIP = "";


        if (recordArray.size() != 0) {
            for (Record tmp : recordArray) {

                if ((prevRemoteIP.equals(tmp.getRemoteIP()) && !prevProt.equals(tmp.getProtocol()) && !prevProt.contentEquals("MULTISTAGE"))) {

                    b.add(new Stackbean(prevRemoteIP, prevLocalIP, prevProt, prevrport, prevlport, bssid, ssid));
                    b.add(new Stackbean(tmp.getRemoteIP(), tmp.getLocalIP(), tmp.getProtocol(), tmp.getRemotePort(), tmp.getLocalPort(), tmp.getBssid(), tmp.getSsid()));         //,tmp.getLocalPort(),tmp.getRemotePort()));
                }
                prevRemoteIP = tmp.getRemoteIP();
                prevProt = tmp.getProtocol();
                prevrport = tmp.getRemotePort();
                prevlport = tmp.getLocalPort();
                externalIP = tmp.getExternalIP();
                bssid = tmp.getBssid();
                ssid = tmp.getSsid();
                prevLocalIP = tmp.getLocalIP();


            }
        }

        if (b.size() != 0) {
            StringBuilder message = new StringBuilder();
            for (Stackbean tmp : b) {

               message.append("\nMulti Stage Attack Detected!\n" + "IP:" + tmp.getRemoteIp() + "\nProtocol:" + tmp.getProtocol());

              //  message.append("\nProtocol:" + tmp.getProtocol());


                stackRemoteIP=tmp.getRemoteIp();
                stackLocalIp=tmp.getLocalip();
                stackProtocol=tmp.getProtocol();
                stackRport=tmp.getRemotePort();
                stackLport=tmp.getLocalPort();
                stackbssid=tmp.getBSSID();
                stackssid = tmp.getSSID();

               Toast.makeText(MainActivity.getInstance().getApplicationContext(), message, Toast.LENGTH_LONG).show();
            }
            log(MessageRecord.TYPE.RECEIVE, message.toString(), stackRemoteIP, stackLocalIp, stackProtocol,stackRport, stackLport,stackbssid, stackssid);
            b.clear();
            message.equals("");

        }

        return true;

    }


    //Packing the attack record
    public void log(MessageRecord.TYPE type, String message, String remoteip, String localip, String protocol, int rport, int lport, String bssid, String ssid) {

        AttackRecord attackRecord = new AttackRecord(true);

        attackRecord.setProtocol("MULTISTAGE");
        attackRecord.setExternalIP(externalIP);
        attackRecord.setLocalIP(localip);
        attackRecord.setLocalPort(lport);
        attackRecord.setRemoteIP(remoteip);
        attackRecord.setRemotePort(rport);
        attackRecord.setBssid(bssid);

        NetworkRecord networkRecord = new NetworkRecord();
        networkRecord.setBssid(bssid);
        networkRecord.setSsid(ssid);
        if (MyLocationManager.getNewestLocation() != null) {
            networkRecord.setLatitude(MyLocationManager.getNewestLocation().getLatitude());
            networkRecord.setLongitude(MyLocationManager.getNewestLocation().getLongitude());
            networkRecord.setAccuracy(MyLocationManager.getNewestLocation().getAccuracy());
            networkRecord.setTimestampLocation(MyLocationManager.getNewestLocation().getTime());
        } else {
            networkRecord.setLatitude(0.0);
            networkRecord.setLongitude(0.0);
            networkRecord.setAccuracy(Float.MAX_VALUE);
            networkRecord.setTimestampLocation(0);
        }


        MessageRecord messageRecord = new MessageRecord(true);
        messageRecord.setAttack_id(attackRecord.getAttack_id());
        messageRecord.setType(type);
        messageRecord.setTimestamp(System.currentTimeMillis());
        messageRecord.setPacket(message);


        Logger.logMultiStageAttack(Hostage.getContext(), attackRecord, networkRecord, messageRecord, System.currentTimeMillis());

    }
}