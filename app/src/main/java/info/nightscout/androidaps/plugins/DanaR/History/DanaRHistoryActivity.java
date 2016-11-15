package info.nightscout.androidaps.plugins.DanaR.History;

import android.app.Activity;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.Bundle;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.IBinder;
import android.support.v7.widget.CardView;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.Spinner;
import android.widget.TextView;

import com.j256.ormlite.dao.Dao;
import com.j256.ormlite.stmt.PreparedQuery;
import com.j256.ormlite.stmt.QueryBuilder;
import com.j256.ormlite.stmt.Where;
import com.squareup.otto.Subscribe;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.SQLException;
import java.text.DateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

import info.nightscout.androidaps.Constants;
import info.nightscout.androidaps.MainApp;
import info.nightscout.androidaps.R;
import info.nightscout.androidaps.db.DanaRHistoryRecord;
import info.nightscout.androidaps.plugins.ConfigBuilder.ConfigBuilderPlugin;
import info.nightscout.androidaps.plugins.DanaR.Services.ExecutionService;
import info.nightscout.androidaps.plugins.DanaR.comm.RecordTypes;
import info.nightscout.androidaps.plugins.DanaR.events.EventDanaRConnectionStatus;
import info.nightscout.androidaps.plugins.DanaR.events.EventDanaRSyncStatus;
import info.nightscout.client.data.NSProfile;
import info.nightscout.utils.DecimalFormatter;
import info.nightscout.utils.ToastUtils;

public class DanaRHistoryActivity extends Activity {
    private static Logger log = LoggerFactory.getLogger(DanaRHistoryActivity.class);

    private boolean mBounded;
    private static ExecutionService mExecutionService;

    private Handler mHandler;
    private static HandlerThread mHandlerThread;

    static NSProfile profile = null;

    Spinner historyTypeSpinner;
    TextView statusView;
    Button reloadButton;
    Button syncButton;
    RecyclerView recyclerView;
    LinearLayoutManager llm;

    static byte showingType = RecordTypes.RECORD_TYPE_ALARM;
    List<DanaRHistoryRecord> historyList = new ArrayList<>();

    public static class TypeList {
        public byte type;
        String name;

        public TypeList(byte type, String name) {
            this.type = type;
            this.name = name;
        }

        @Override
        public String toString() {
            return name;
        }
    }

    public DanaRHistoryActivity() {
        super();
        mHandlerThread = new HandlerThread(DanaRHistoryActivity.class.getSimpleName());
        mHandlerThread.start();
        this.mHandler = new Handler(mHandlerThread.getLooper());
    }


    @Override
    public void onStart() {
        super.onStart();
        Intent intent = new Intent(this, ExecutionService.class);
        bindService(intent, mConnection, Context.BIND_AUTO_CREATE);
    }

    @Override
    protected void onResume() {
        super.onResume();
        MainApp.bus().register(this);
    }

    @Override
    protected void onPause() {
        super.onPause();
        MainApp.bus().unregister(this);
    }

    @Override
    public void onStop() {
        super.onStop();
        if (mBounded) {
            unbindService(mConnection);
            mBounded = false;
        }
    }

    ServiceConnection mConnection = new ServiceConnection() {

        public void onServiceDisconnected(ComponentName name) {
            log.debug("Service is disconnected");
            mBounded = false;
            mExecutionService = null;
        }

        public void onServiceConnected(ComponentName name, IBinder service) {
            log.debug("Service is connected");
            mBounded = true;
            ExecutionService.LocalBinder mLocalBinder = (ExecutionService.LocalBinder) service;
            mExecutionService = mLocalBinder.getServiceInstance();
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.danar_historyactivity);

        historyTypeSpinner = (Spinner) findViewById(R.id.danar_historytype);
        statusView = (TextView) findViewById(R.id.danar_historystatus);
        reloadButton = (Button) findViewById(R.id.danar_historyreload);
        syncButton = (Button) findViewById(R.id.danar_historysync);
        recyclerView = (RecyclerView) findViewById(R.id.danar_history_recyclerview);

        recyclerView.setHasFixedSize(true);
        llm = new LinearLayoutManager(this);
        recyclerView.setLayoutManager(llm);

        RecyclerViewAdapter adapter = new RecyclerViewAdapter(historyList);
        recyclerView.setAdapter(adapter);

        statusView.setVisibility(View.GONE);

        // Types

        ArrayList<TypeList> typeList = new ArrayList<>();
        typeList.add(new TypeList(RecordTypes.RECORD_TYPE_DAILY, getString(R.string.danar_history_dailyinsulin)));
        typeList.add(new TypeList(RecordTypes.RECORD_TYPE_ALARM, getString(R.string.danar_history_alarm)));
        typeList.add(new TypeList(RecordTypes.RECORD_TYPE_BASALHOUR, getString(R.string.danar_history_basalhours)));
        typeList.add(new TypeList(RecordTypes.RECORD_TYPE_BOLUS, getString(R.string.danar_history_bolus)));
        typeList.add(new TypeList(RecordTypes.RECORD_TYPE_CARBO, getString(R.string.danar_history_carbohydrates)));
        typeList.add(new TypeList(RecordTypes.RECORD_TYPE_ERROR, getString(R.string.danar_history_errors)));
        typeList.add(new TypeList(RecordTypes.RECORD_TYPE_GLUCOSE, getString(R.string.danar_history_glucose)));
        typeList.add(new TypeList(RecordTypes.RECORD_TYPE_REFILL, getString(R.string.danar_history_refill)));
        typeList.add(new TypeList(RecordTypes.RECORD_TYPE_SUSPEND, getString(R.string.danar_history_syspend)));
        ArrayAdapter<TypeList> spinnerAdapter = new ArrayAdapter<>(this,
                android.R.layout.simple_spinner_item, typeList);
        spinnerAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        historyTypeSpinner.setAdapter(spinnerAdapter);

        reloadButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (mExecutionService.isConnected() || mExecutionService.isConnecting()) {
                    ToastUtils.showToastInUiThread(MainApp.instance().getApplicationContext(), getString(R.string.pumpbusy));
                    return;
                }
                mHandler.post(new Runnable() {
                    @Override
                    public void run() {
                        TypeList selected = (TypeList) historyTypeSpinner.getSelectedItem();
                        runOnUiThread(new Runnable() {
                            @Override
                            public void run() {
                                reloadButton.setVisibility(View.GONE);
                                syncButton.setVisibility(View.GONE);
                                statusView.setVisibility(View.VISIBLE);
                            }
                        });
                        clearCardView();
                        mExecutionService.loadHistory(selected.type);
                        loadDataFromDB(selected.type);
                        runOnUiThread(new Runnable() {
                            @Override
                            public void run() {
                                reloadButton.setVisibility(View.VISIBLE);
                                syncButton.setVisibility(View.VISIBLE);
                                statusView.setVisibility(View.GONE);
                            }
                        });
                    }
                });
            }
        });

        syncButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                mHandler.post(new Runnable() {
                    @Override
                    public void run() {
                        runOnUiThread(new Runnable() {
                            @Override
                            public void run() {
                                reloadButton.setVisibility(View.GONE);
                                syncButton.setVisibility(View.GONE);
                                statusView.setVisibility(View.VISIBLE);
                            }
                        });
                        DanaRNSHistorySync sync = new DanaRNSHistorySync(historyList);
                        sync.sync(DanaRNSHistorySync.SYNC_ALL);
                        runOnUiThread(new Runnable() {
                            @Override
                            public void run() {
                                reloadButton.setVisibility(View.VISIBLE);
                                syncButton.setVisibility(View.VISIBLE);
                                statusView.setVisibility(View.GONE);
                            }
                        });
                    }
                });
            }
        });

        historyTypeSpinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                TypeList selected = (TypeList) historyTypeSpinner.getSelectedItem();
                loadDataFromDB(selected.type);
                showingType = selected.type;
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {
                clearCardView();
            }
        });
        profile = ConfigBuilderPlugin.getActiveProfile().getProfile();
        if (profile == null) {
            ToastUtils.showToastInUiThread(MainApp.instance().getApplicationContext(), MainApp.sResources.getString(R.string.noprofile));
            finish();
        }
    }

    public static class RecyclerViewAdapter extends RecyclerView.Adapter<RecyclerViewAdapter.HistoryViewHolder> {

        List<DanaRHistoryRecord> historyList;

        RecyclerViewAdapter(List<DanaRHistoryRecord> historyList) {
            this.historyList = historyList;
        }

        @Override
        public HistoryViewHolder onCreateViewHolder(ViewGroup viewGroup, int viewType) {
            View v = LayoutInflater.from(viewGroup.getContext()).inflate(R.layout.danar_history_item, viewGroup, false);
            return new HistoryViewHolder(v);
        }

        @Override
        public void onBindViewHolder(HistoryViewHolder holder, int position) {
            DateFormat df = DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT);
            DanaRHistoryRecord record = historyList.get(position);
            holder.time.setText(df.format(new Date(record.getRecordDate())));
            holder.value.setText(DecimalFormatter.to2Decimal(record.getRecordValue()));
            holder.stringvalue.setText(record.getStringRecordValue());
            holder.bolustype.setText(record.getBolusType());
            holder.duration.setText(DecimalFormatter.to0Decimal(record.getRecordDuration()));
            holder.alarm.setText(record.getRecordAlarm());
            switch (showingType) {
                case RecordTypes.RECORD_TYPE_ALARM:
                    holder.time.setVisibility(View.VISIBLE);
                    holder.value.setVisibility(View.VISIBLE);
                    holder.stringvalue.setVisibility(View.GONE);
                    holder.bolustype.setVisibility(View.GONE);
                    holder.duration.setVisibility(View.GONE);
                    holder.dailybasal.setVisibility(View.GONE);
                    holder.dailybolus.setVisibility(View.GONE);
                    holder.dailytotal.setVisibility(View.GONE);
                    holder.alarm.setVisibility(View.VISIBLE);
                    break;
                case RecordTypes.RECORD_TYPE_BOLUS:
                    holder.time.setVisibility(View.VISIBLE);
                    holder.value.setVisibility(View.VISIBLE);
                    holder.stringvalue.setVisibility(View.GONE);
                    holder.bolustype.setVisibility(View.VISIBLE);
                    holder.duration.setVisibility(View.VISIBLE);
                    holder.dailybasal.setVisibility(View.GONE);
                    holder.dailybolus.setVisibility(View.GONE);
                    holder.dailytotal.setVisibility(View.GONE);
                    holder.alarm.setVisibility(View.GONE);
                    break;
                case RecordTypes.RECORD_TYPE_DAILY:
                    df = DateFormat.getDateInstance(DateFormat.SHORT);
                    holder.dailybasal.setText(DecimalFormatter.to2Decimal(record.getRecordDailyBasal()) + "U");
                    holder.dailybolus.setText(DecimalFormatter.to2Decimal(record.getRecordDailyBolus()) + "U");
                    holder.dailytotal.setText(DecimalFormatter.to2Decimal(record.getRecordDailyBolus()+ record.getRecordDailyBasal()) + "U");
                    holder.time.setText(df.format(new Date(record.getRecordDate())));
                    holder.time.setVisibility(View.VISIBLE);
                    holder.value.setVisibility(View.GONE);
                    holder.stringvalue.setVisibility(View.GONE);
                    holder.bolustype.setVisibility(View.GONE);
                    holder.duration.setVisibility(View.GONE);
                    holder.dailybasal.setVisibility(View.VISIBLE);
                    holder.dailybolus.setVisibility(View.VISIBLE);
                    holder.dailytotal.setVisibility(View.VISIBLE);
                    holder.alarm.setVisibility(View.GONE);
                    break;
                case RecordTypes.RECORD_TYPE_GLUCOSE:
                    holder.value.setText(NSProfile.toUnitsString(record.getRecordValue(), record.getRecordValue() * Constants.MGDL_TO_MMOLL, profile.getUnits()));
                    // rest is the same
                case RecordTypes.RECORD_TYPE_CARBO:
                case RecordTypes.RECORD_TYPE_BASALHOUR:
                case RecordTypes.RECORD_TYPE_ERROR:
                case RecordTypes.RECORD_TYPE_PRIME:
                case RecordTypes.RECORD_TYPE_REFILL:
                case RecordTypes.RECORD_TYPE_TB:
                    holder.time.setVisibility(View.VISIBLE);
                    holder.value.setVisibility(View.VISIBLE);
                    holder.stringvalue.setVisibility(View.GONE);
                    holder.bolustype.setVisibility(View.GONE);
                    holder.duration.setVisibility(View.GONE);
                    holder.dailybasal.setVisibility(View.GONE);
                    holder.dailybolus.setVisibility(View.GONE);
                    holder.dailytotal.setVisibility(View.GONE);
                    holder.alarm.setVisibility(View.GONE);
                    break;
                case RecordTypes.RECORD_TYPE_SUSPEND:
                    holder.time.setVisibility(View.VISIBLE);
                    holder.value.setVisibility(View.GONE);
                    holder.stringvalue.setVisibility(View.VISIBLE);
                    holder.bolustype.setVisibility(View.GONE);
                    holder.duration.setVisibility(View.GONE);
                    holder.dailybasal.setVisibility(View.GONE);
                    holder.dailybolus.setVisibility(View.GONE);
                    holder.dailytotal.setVisibility(View.GONE);
                    holder.alarm.setVisibility(View.GONE);
                    break;
            }
        }

        @Override
        public int getItemCount() {
            return historyList.size();
        }

        @Override
        public void onAttachedToRecyclerView(RecyclerView recyclerView) {
            super.onAttachedToRecyclerView(recyclerView);
        }

        public static class HistoryViewHolder extends RecyclerView.ViewHolder {
            CardView cv;
            TextView time;
            TextView value;
            TextView bolustype;
            TextView stringvalue;
            TextView duration;
            TextView dailybasal;
            TextView dailybolus;
            TextView dailytotal;
            TextView alarm;

            HistoryViewHolder(View itemView) {
                super(itemView);
                cv = (CardView) itemView.findViewById(R.id.danar_history_cardview);
                time = (TextView) itemView.findViewById(R.id.danar_history_time);
                value = (TextView) itemView.findViewById(R.id.danar_history_value);
                bolustype = (TextView) itemView.findViewById(R.id.danar_history_bolustype);
                stringvalue = (TextView) itemView.findViewById(R.id.danar_history_stringvalue);
                duration = (TextView) itemView.findViewById(R.id.danar_history_duration);
                dailybasal = (TextView) itemView.findViewById(R.id.danar_history_dailybasal);
                dailybolus = (TextView) itemView.findViewById(R.id.danar_history_dailybolus);
                dailytotal = (TextView) itemView.findViewById(R.id.danar_history_dailytotal);
                alarm = (TextView) itemView.findViewById(R.id.danar_history_alarm);
            }
        }
    }

    private void loadDataFromDB(byte type) {
        try {
            Dao<DanaRHistoryRecord, String> dao = MainApp.getDbHelper().getDaoDanaRHistory();
            QueryBuilder<DanaRHistoryRecord, String> queryBuilder = dao.queryBuilder();
            queryBuilder.orderBy("recordDate", false);
            Where where = queryBuilder.where();
            where.eq("recordCode", type);
            queryBuilder.limit(200L);
            PreparedQuery<DanaRHistoryRecord> preparedQuery = queryBuilder.prepare();
            historyList = dao.query(preparedQuery);
        } catch (SQLException e) {
            e.printStackTrace();
            historyList = new ArrayList<>();
        }
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                recyclerView.swapAdapter(new RecyclerViewAdapter(historyList), false);
            }
        });
    }

    private void clearCardView() {
        historyList = new ArrayList<>();
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                recyclerView.swapAdapter(new RecyclerViewAdapter(historyList), false);
            }
        });
    }

    @Subscribe
    public void onStatusEvent(final EventDanaRSyncStatus s) {
        log.debug("EventDanaRSyncStatus: " + s.message);
        runOnUiThread(
                new Runnable() {
                    @Override
                    public void run() {
                        statusView.setText(s.message);
                    }
                });
    }

    @Subscribe
    public void onStatusEvent(final EventDanaRConnectionStatus c) {
        runOnUiThread(
                new Runnable() {
                    @Override
                    public void run() {
                        if (c.sStatus == EventDanaRConnectionStatus.CONNECTING) {
                            statusView.setText(String.format(getString(R.string.danar_history_connectingfor), c.sSecondsElapsed));
                            log.debug("EventDanaRConnectionStatus: " + "Connecting for " + c.sSecondsElapsed + "s");
                        } else if (c.sStatus == EventDanaRConnectionStatus.CONNECTED) {
                            statusView.setText(MainApp.sResources.getString(R.string.connected));
                            log.debug("EventDanaRConnectionStatus: Connected");
                        } else {
                            statusView.setText(MainApp.sResources.getString(R.string.disconnected));
                            log.debug("EventDanaRConnectionStatus: Disconnected");
                        }
                    }
                }
        );
    }


}
