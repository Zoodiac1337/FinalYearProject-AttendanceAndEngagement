package com.example.attendanceandengagement;

import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.media.MediaPlayer;
import android.os.Build;
import android.os.Bundle;
import android.os.CountDownTimer;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TableRow;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.core.app.ActivityCompat;
import androidx.core.app.NotificationCompat;
import androidx.core.app.NotificationManagerCompat;
import androidx.fragment.app.Fragment;

import com.github.mikephil.charting.charts.BarChart;
import com.github.mikephil.charting.components.XAxis;
import com.github.mikephil.charting.components.YAxis;
import com.github.mikephil.charting.data.BarData;
import com.github.mikephil.charting.data.BarDataSet;
import com.github.mikephil.charting.data.BarEntry;
import com.github.mikephil.charting.utils.PercentFormatter;
import com.google.android.gms.tasks.OnCompleteListener;
import com.google.android.gms.tasks.Task;
import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FieldValue;
import com.google.firebase.firestore.FirebaseFirestore;

import java.time.LocalDate;
import java.time.Period;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * A simple {@link Fragment} subclass.
 * Use the {@link Study#newInstance} factory method to
 * create an instance of this fragment.
 */
public class Study extends Fragment {

    // TODO: Rename parameter arguments, choose names that match
    // the fragment initialization parameters, e.g. ARG_ITEM_NUMBER
    private static final String ARG_PARAM1 = "param1";
    private static final String ARG_PARAM2 = "param2";

    // TODO: Rename and change types of parameters
    private String mParam1;
    private String mParam2;
    private FirebaseFirestore db = FirebaseFirestore.getInstance();
    private final CountDownTimer[] timer = {null};
    private Button startButton;
    private Button stopButton;
    private Button studyGoalButton;
    private TextView timerText;
    private TableRow timerRow1;
    private TableRow timerRow2;
    private TextView timerSeconds;
    private TextView timerMinutes;
    private TextView goalReached;
    private EditText pomodoroWork;
    private EditText pomodoroBreak;
    private EditText studyGoal;
    private MediaPlayer notification;
    private BarChart barChart;
    private Bundle bundle;
    private SharedPreferences preferences;
    private SharedPreferences.Editor editor;
    private DocumentReference docRef;
    private NotificationManagerCompat notificationManager;
    private static final String CHANNEL_ID = "notificationChannel";

    public Study() {
        // Required empty public constructor
    }

    /**
     * Use this factory method to create a new instance of
     * this fragment using the provided parameters.
     *
     * @param param1 Parameter 1.
     * @param param2 Parameter 2.
     * @return A new instance of fragment Timetable.
     */
    // TODO: Rename and change types and number of parameters
    public static Study newInstance(String param1, String param2) {
        Study fragment = new Study();
        Bundle args = new Bundle();
        args.putString(ARG_PARAM1, param1);
        args.putString(ARG_PARAM2, param2);
        fragment.setArguments(args);
        return fragment;
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (getArguments() != null) {
            mParam1 = getArguments().getString(ARG_PARAM1);
            mParam2 = getArguments().getString(ARG_PARAM2);
        }
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {

        View view = inflater.inflate(R.layout.fragment_study, container, false);
        bundle = getArguments();
        preferences = this.getActivity().getSharedPreferences(bundle.getString("email"), Context.MODE_PRIVATE);
        editor = preferences.edit();
        notificationManager = NotificationManagerCompat.from(getContext());
        docRef = db.collection("Users").document(bundle.getString("email"));

        barChart = (BarChart) view.findViewById(R.id.barChart);

        notification = MediaPlayer.create(getContext(), R.raw.notification);
        pomodoroBreak = (EditText) view.findViewById(R.id.pomodoroBreak);
        timerSeconds = (TextView) view.findViewById(R.id.timerSeconds);
        timerMinutes = (TextView) view.findViewById(R.id.timerMinutes);
        goalReached = (TextView) view.findViewById(R.id.goalReached);
        pomodoroWork = (EditText) view.findViewById(R.id.pomodoroWork);
        studyGoal = (EditText) view.findViewById(R.id.studyGoal);
        startButton = (Button) view.findViewById(R.id.timerStartButton);
        stopButton = (Button) view.findViewById(R.id.timerStopButton);
        studyGoalButton = (Button) view.findViewById(R.id.studyGoalButton);
        timerText = (TextView) view.findViewById(R.id.timerText);
        timerRow1 = (TableRow) view.findViewById(R.id.timerRow1);
        timerRow2 = (TableRow) view.findViewById(R.id.timerRow2);

        timerRow1.setVisibility(View.GONE);
        timerRow2.setVisibility(View.VISIBLE);

        createNotificationChannel();
        updatePastDays();


        pomodoroWork.setText(preferences.getString("pomodoroWork", "25"));
        pomodoroBreak.setText(preferences.getString("pomodoroBreak", "5"));
        studyGoal.setText(preferences.getString("studyGoal", "2"));

        startButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                try {
                    editor.putString("pomodoroWork", pomodoroWork.getText().toString());
                    editor.putString("pomodoroBreak", pomodoroBreak.getText().toString());
                    editor.apply();
                    studyTimer();
                } catch (NumberFormatException e) {
                    Toast.makeText(getContext(), "Missing fields.", Toast.LENGTH_SHORT).show();
                }
            }

        });

        stopButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                stopTimer();
                stopNotification(1);
            }
        });

        studyGoalButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                setStudyGoal();
            }
        });

        return view;
    }


    public void studyTimer() {
        if (timer[0] != null)
            timer[0].cancel();
        int workMinutes = Integer.parseInt(pomodoroWork.getText().toString());
        int workSeconds = workMinutes * 60;

        timerText.setText("Study time: ");
        timerRow1.setVisibility(View.VISIBLE);
        timerRow2.setVisibility(View.GONE);

        timer[0] = new CountDownTimer(workSeconds * 1000, 1000) {
            int seconds = 0;
            int minutes = workMinutes;

            @Override
            public void onTick(long millisUntilFinished) {
                timerSeconds.setText(String.format("%02d", seconds));
                timerMinutes.setText(String.format("%02d", minutes));
                if (seconds == 0) {
                    seconds = 60;
                    timerMinutes.setText(String.valueOf(minutes));
                    createNotification(1, "Study timer", String.format("%02d", minutes)+":"+String.format("%02d", seconds));
                    minutes--;
                }
                createNotification(1, "Study timer", String.format("%02d", minutes)+":"+String.format("%02d", seconds));
                seconds--;
            }

            @Override
            public void onFinish() {
                breakTimer();
                updateMinutes(workMinutes);
                updatePastDays();
                notification.start();
            }
        };
        timer[0].start();
    }

    public void breakTimer() {
        final MediaPlayer notification = MediaPlayer.create(getContext(), R.raw.notification);

        if (timer[0] != null)
            timer[0].cancel();

        int breakMinutes = Integer.parseInt(pomodoroBreak.getText().toString());
        int breakSeconds = breakMinutes * 60;

        timerText.setText("Break time: ");

        timer[0] = new CountDownTimer(breakSeconds * 1000, 1000) {
            int seconds = 0;
            int minutes = breakMinutes;

            @Override
            public void onTick(long millisUntilFinished) {
                timerSeconds.setText(String.format("%02d", seconds));
                timerMinutes.setText(String.format("%02d", minutes));
                if (seconds == 0) {
                    seconds = 60;
                    timerMinutes.setText(String.valueOf(minutes));
                    createNotification(1, "Study timer", String.format("%02d", minutes)+":"+String.format("%02d", seconds));
                    minutes--;
                }
                createNotification(1, "Break timer", String.format("%02d", minutes)+":"+String.format("%02d", seconds));
                seconds--;
            }

            @Override
            public void onFinish() {
                notification.start();
                studyTimer();
            }
        };
        timer[0].start();
    }

    public void stopTimer() {
        if (timer[0] != null) {
            timerRow1.setVisibility(View.GONE);
            timerRow2.setVisibility(View.VISIBLE);

            if (timerText.getText().toString().equals("Study time: ")) {
                if (Integer.parseInt(timerSeconds.getText().toString()) < 10)
                    updateMinutes(Integer.parseInt(pomodoroWork.getText().toString()) - (Integer.parseInt(timerMinutes.getText().toString())));
                else
                    updateMinutes(Integer.parseInt(pomodoroWork.getText().toString()) - (Integer.parseInt(timerMinutes.getText().toString())) - 1);
                updatePastDays();
            }
            timer[0].cancel();
        }
    }

    public void updateMinutes(int minutesStudied) {
        docRef.update("minutesStudied." + LocalDate.now().toString(), FieldValue.increment(minutesStudied));
    }

    public void updatePastDays() {
        docRef.get().addOnCompleteListener(new OnCompleteListener<DocumentSnapshot>() {
            @Override
            public void onComplete(@NonNull Task<DocumentSnapshot> task) {
                Map<String, Float> barChartEntries = new LinkedHashMap<>();
                if (task.isSuccessful()) {
                    DocumentSnapshot document = task.getResult();
                    float StudyGoal = Integer.parseInt(preferences.getString("studyGoal", "2")) * 60;
                    for (int i = 6; i >= 0; i--) {
                        LocalDate date = LocalDate.now().minus(Period.ofDays(i));
                        if (document.getLong("minutesStudied." + date) != null)
                            barChartEntries.put(date.getDayOfMonth() + "th", document.getLong("minutesStudied." + date) * 100f / StudyGoal);
                        else barChartEntries.put(date.getDayOfMonth() + "th", 0f);
                    }
                    if (document.getLong("minutesStudied." + LocalDate.now()) != null)
                        goalReached.setText("Today reached: " + Math.round(document.getLong("minutesStudied." + LocalDate.now()) * 100 / StudyGoal) + "%");
                    else goalReached.setText("Today reached: 0%");
                    setBarData(barChartEntries);
                }
            }
        });
    }

    private void setBarData(Map<String, Float> entries) {
        ArrayList barEntriesArrayList = new ArrayList<>();
        ArrayList Labels = new ArrayList();

        int number = 0;
        for (String i : entries.keySet()) {
            Labels.add(i);
            barEntriesArrayList.add(new BarEntry(entries.get(i), number));
            number++;
        }
        BarDataSet barDataSet = new BarDataSet(barEntriesArrayList, "Percentage of daily goal achieved");

        BarData barData = new BarData(Labels, barDataSet);


        barDataSet.setColor(Color.parseColor("#E4045B"));
        barDataSet.setValueTextColor(Color.BLACK);
        barDataSet.setValueTextSize(15f);
        XAxis xAxis = barChart.getXAxis();
        xAxis.setPosition(XAxis.XAxisPosition.BOTTOM);
        xAxis.setDrawGridLines(false);
        YAxis leftAxis = barChart.getAxisLeft();
        leftAxis.setDrawAxisLine(false);
        leftAxis.setEnabled(false);
        YAxis rightAxis = barChart.getAxisRight();
        rightAxis.setValueFormatter(new PercentFormatter());
        rightAxis.setDrawAxisLine(false);
        barChart.setData(barData);
        barChart.setDescription("");
        barChart.animateY(750);
        barChart.setDrawBarShadow(false);
        barChart.setDrawGridBackground(false);
        barChart.notifyDataSetChanged();
        barChart.invalidate();
    }

    public void setStudyGoal() {
        editor.putString("studyGoal", studyGoal.getText().toString());
        editor.apply();
        updatePastDays();
    }

    private void createNotification(int notificationID, String title, String content){
        NotificationCompat.Builder builder = new NotificationCompat.Builder(getContext(), CHANNEL_ID)
                .setSmallIcon(R.drawable.round_timer_24)
                .setContentTitle(title)
                .setContentText(content)
                .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                .setOnlyAlertOnce(true);

        if (ActivityCompat.checkSelfPermission(getContext(), android.Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            Toast.makeText(getContext(), "Missing permissions for notifications.", Toast.LENGTH_SHORT).show();
        }
        else
            // notificationId is a unique int for each notification that you must define
            notificationManager.notify(notificationID, builder.build());
    }

    private void stopNotification(int notificationID){
        notificationManager.cancel(notificationID);
    }
    private void createNotificationChannel() {
        // Create the NotificationChannel, but only on API 26+ because
        // the NotificationChannel class is new and not in the support library
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            CharSequence name = "Channel name";
            String description = "Channel description";
            int importance = NotificationManager.IMPORTANCE_DEFAULT;
            NotificationChannel channel = new NotificationChannel(CHANNEL_ID, name, importance);
            channel.setDescription(description);
            // Register the channel with the system; you can't change the importance
            // or other notification behaviors after this
            NotificationManager notificationManager = (NotificationManager) getContext().getSystemService(getContext().NOTIFICATION_SERVICE);
            notificationManager.createNotificationChannel(channel);
        }
    }

}