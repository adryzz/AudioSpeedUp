package com.lodauria.audiospeedup;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.NotificationCompat;
import androidx.core.app.NotificationManagerCompat;
import android.annotation.SuppressLint;
import android.app.AlertDialog;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.media.AudioManager;
import android.media.MediaPlayer;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.SystemClock;
import android.view.View;
import android.widget.Button;
import android.widget.ImageButton;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;
import java.util.Objects;
import java.util.concurrent.TimeUnit;

public class MainActivity extends AppCompatActivity {

    // GLOBAL VARIABLES ----------------------------------------------------------------------------
    private Button test;
    private Button help;
    private TextView label;
    private SeekBar speed;
    private SeekBar player;
    private MediaPlayer mp;
    private ImageButton play_b;
    private ImageButton restart_b;
    private ImageButton stop_b;
    private int flag = 0;
    private float factor;
    public static boolean mp_play = true;
    public static boolean mp_stop = true;
    private NotificationManagerCompat notificationManager;
    private NotificationCompat.Builder notification;
    private Thread mp_updater;

    // GET SPEED FACTOR ----------------------------------------------------------------------------
    private void getFactor(){
        SharedPreferences save= getSharedPreferences("factor", 0);
        factor = save.getFloat("factor", (float) 2.0);
    }

    // SAVE NEW SPEED FACTOR -----------------------------------------------------------------------
    private void saveFactor(){
        factor = (float) (speed.getProgress()/4.0 + 0.5);
        SharedPreferences save = getSharedPreferences("factor", 0);
        SharedPreferences.Editor editor= save.edit();
        editor.putFloat("factor", factor);
        editor.apply();
    }

    // COMPLETION LISTENER OF THE MEDIA PLAYER -----------------------------------------------------
    private void set_mp_listener(final boolean from_sharing){
        mp.setOnCompletionListener(new MediaPlayer.OnCompletionListener() {
            @Override
            public void onCompletion(MediaPlayer media_p) {
                // Same of the stop button
                if (from_sharing && !hasWindowFocus()){
                    mp.release();
                    mp = null;
                    notificationManager.cancelAll();
                    finishAndRemoveTask();
                }
                else{
                    mp.pause();
                    notificationManager.cancelAll();
                    mp.seekTo(0);
                    player.setProgress(0);
                    play_b.setImageResource(R.drawable.play);
                }
            }
        });
    }

    // SETUP ACTIVITY FOR SHARING OPTION -----------------------------------------------------------
    private boolean setup_for_sharing(){
        // Check if media player definition was successful
        if (mp == null){
            // This means that the file is not supported
            Toast.makeText(this, "Audio format not supported!", Toast.LENGTH_SHORT).show();
            finishAndRemoveTask();
            return true;
        }
        // Everything fine, the bottom buttons are unnecessary
        test.setVisibility(View.INVISIBLE);
        help.setVisibility(View.INVISIBLE);
        return false;
    }

    // SETUP ACTIVITY IF FROM LAUNCHER -------------------------------------------------------------
    private void setup_normal(){
        mp = MediaPlayer.create(this, R.raw.test);
        player.setEnabled(false);
        restart_b.setEnabled(false);
        stop_b.setEnabled(false);
        play_b.setEnabled(false);

        // Define the test button for reproducing audio
        test.setOnClickListener(new Button.OnClickListener() {
            @Override
            public void onClick(View v) {
                flag += 1;
                if (flag==1) {
                    // Check if audio is mute
                    AudioManager am = (AudioManager) getApplicationContext().getSystemService(Context.AUDIO_SERVICE);
                    if (Objects.requireNonNull(am).getStreamVolume(AudioManager.STREAM_MUSIC)==0)
                        Toast.makeText(getApplicationContext(), "Turn the volume up", Toast.LENGTH_SHORT).show();
                    mp_updater.start();
                    player.setEnabled(true);
                    restart_b.setEnabled(true);
                    stop_b.setEnabled(true);
                    play_b.setEnabled(true);
                }
                // Easter egg after 10 tap
                if (flag==10) {
                    mp.stop();
                    mp = MediaPlayer.create(getApplicationContext(), R.raw.easteregg);
                    set_mp_listener(false);
                    mp.setLooping(false);
                    mp.setVolume(1.0f, 1.0f);
                    player.setMax(mp.getDuration()/100);
                }
                mp.seekTo(0);
                mp.setPlaybackParams(mp.getPlaybackParams().setSpeed(factor));
                play_b.setImageResource(R.drawable.pause);
            }
        });

        // Define the help button with the warning message
        help.setOnClickListener(new Button.OnClickListener() {
            @Override
            public void onClick(View v) {
                AlertDialog alertDialog = new AlertDialog.Builder(MainActivity.this).create();
                alertDialog.setTitle("How to use:");
                alertDialog.setMessage("Select a file to play by using the sharing option of apps like " +
                        "WhatsApp.\n\nWARNING: Some audio formats are supported only on latest Android versions.");
                alertDialog.setButton(AlertDialog.BUTTON_NEUTRAL, "OK",
                        new DialogInterface.OnClickListener() {
                            public void onClick(DialogInterface dialog, int which) {
                                dialog.dismiss();
                            }
                        });
                alertDialog.show();
            }
        });
    }

    // ON ACTIVITY CREATION ------------------------------------------------------------------------
    @SuppressLint("SetTextI18n")
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        // Standard setup
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        // All element initialization
        test = findViewById(R.id.testButton);
        help = findViewById(R.id.helpButton);
        play_b = findViewById(R.id.playButton);
        stop_b = findViewById(R.id.stopButton);
        restart_b = findViewById(R.id.restartButton);
        label = findViewById(R.id.SpeedVal);
        speed = findViewById(R.id.speedBar);
        player = findViewById(R.id.seekBar);

        // FIRST SETUP -----------------------------------------------------------------------------
        // Obtain the speed factor
        getFactor();

        // Identify if the call was from the launcher
        Intent intent = getIntent();
        final boolean from_sharing = Objects.equals(intent.getAction(),"android.intent.action.SEND");
        if (from_sharing){
            moveTaskToBack(true);
            // Get the file shared and initialize correctly the activity
            Uri data = intent.getParcelableExtra(Intent.EXTRA_STREAM);
            mp = MediaPlayer.create(getApplicationContext(), data);
            // If file can't be open stop all the other things below
            if (setup_for_sharing()) return;
        }
        else{
            // Setup activity for a launcher event
            setup_normal();
        }

        // Setup the media player end the screen in general
        mp.setLooping(false);
        mp.setVolume(1.0f, 1.0f);
        speed.setProgress((int) ((factor-0.5)*4.0));
        label.setText( "Speed: " + factor + "x");

        // SPEED BAR LISTENER ----------------------------------------------------------------------
        // Listener for the speed bar (the factor saved and the speed have to change)
        speed.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                // Modify only the text shown
                label.setText( "Speed: " + (progress/4.0 + 0.5)  + "x");
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {
                // Nothing here
            }

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {
                // Save the new speed factor
                saveFactor();
                // If audio reproduction was already started
                if (mp.isPlaying()) {
                    // Update instantaneously the reproduction speed
                    mp.setPlaybackParams(mp.getPlaybackParams().setSpeed(factor));
                }
            }
        });

        // NOTIFICATION ----------------------------------------------------------------------------
        // Intent for opening the app
        Intent contentIntent = new Intent(this, MainActivity.class);
        PendingIntent enter = PendingIntent.getActivity(
                            this, 0, contentIntent, 0);

        // Intent for the play/pause button
        Intent playIntent = new Intent(this, PlayReceiver.class);
        PendingIntent playPendingIntent =
                PendingIntent.getBroadcast(this, 0, playIntent, 0);

        // Intent for the sop button
        Intent stopIntent = new Intent(this, StopReceiver.class);
        PendingIntent stopPendingIntent =
                PendingIntent.getBroadcast(this, 0, stopIntent, 0);

        //Notification manager declaration
        notificationManager = NotificationManagerCompat.from(this);
        //Different behaviour for new or old Android versions
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            String chanel_id = "3000";
            CharSequence name = "Audio player";
            String description = "Notification with the audio player";
            int importance = NotificationManager.IMPORTANCE_LOW;
            NotificationChannel mChannel = new NotificationChannel(chanel_id, name, importance);
            mChannel.setDescription(description);
            mChannel.enableLights(true);
            assert notificationManager != null;
            notificationManager.createNotificationChannel(mChannel);
            notification = new NotificationCompat.Builder(this, chanel_id);
        } else {
            notification = new NotificationCompat.Builder(this, "channel1");
        }

        // First notification content declaration
        notification.setSmallIcon(R.drawable.my_notify)
                .setContentTitle("Reproducing audio...")
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setContentIntent(enter)
                .setAutoCancel(true)
                .setOngoing(true)
                .setOnlyAlertOnce(true)
                .setProgress(mp.getDuration(), 0, false)
                .addAction(R.drawable.ic_launcher_background, "Play/Pause", playPendingIntent)
                .addAction(R.drawable.ic_launcher_background, "Stop", stopPendingIntent);

        // THREAD FOR UPDATING MEDIA PLAYER --------------------------------------------------------
        mp_updater = new Thread (new Runnable() {
            @Override
            public void run() {
                // Setup player timeline
                if (mp != null) player.setMax(mp.getDuration()/100);
                // If the audio file duration can't be obtained set indeterminate progressbar
                if (mp != null && mp.getDuration() == -1){
                    notification.setProgress(0, 0, true);
                    notificationManager.notify(1, notification.build());
                    player.setEnabled(false);
                }
                else {
                    // Thread start cycling in this loop and exit when media player is deleted
                    while (mp != null){
                        // If inside the loop mp became null some method throw exceptions
                        try {
                            // Update the notification content
                            notification.setProgress(mp.getDuration() / 100,
                                    mp.getCurrentPosition() / 100, false);
                            @SuppressLint("DefaultLocale") String tt = String.format("%02d:%02d",
                                    TimeUnit.MILLISECONDS.toMinutes(mp.getCurrentPosition()),
                                    TimeUnit.MILLISECONDS.toSeconds(mp.getCurrentPosition()) -
                                    TimeUnit.MINUTES.toSeconds(TimeUnit.MILLISECONDS.toMinutes(
                                                    mp.getCurrentPosition())));
                            notification.setContentText(tt);
                            if (!player.isPressed() && mp.isPlaying()) {
                                // Change the player and the notification timeline only during reproduction
                                player.setProgress(mp.getCurrentPosition() / 100);
                                notificationManager.notify(1, notification.build());
                            }
                            // Slow down the looping time (so we change the refresh rate of the timeline)
                            SystemClock.sleep(250);
                        } catch (Exception e){
                            // If an exception has occurred means that mp has been deleted and we stop
                            return;
                        }
                    }
                }
            }
        });

        // THREAD FOR HANDLING NOTIFICATION BUTTONS ------------------------------------------------
        // A really bad solution... The idea would be handling buttons action inside the specific
        // classes, but they are static and it's a mess
        // TODO: can be solved without changing everything?
        Thread mp_handler = new Thread(new Runnable() {
            @Override
            public void run() {
                while (mp != null) {
                    if (!mp_play) {
                        // Play/pause button has been pressed recently
                        mp_play = true;
                        try {
                            if (mp.isPlaying()) {
                                mp.pause();
                                play_b.setImageResource(R.drawable.play);
                            } else {
                                mp.setPlaybackParams(mp.getPlaybackParams().setSpeed(factor));
                                play_b.setImageResource(R.drawable.pause);
                            }
                        } catch (Exception e) {
                            if (mp == null) return;
                        }
                    }
                    if (!mp_stop) {
                        // Stop button has been pressed recently
                        mp_stop = true;
                        try {
                            if (from_sharing && !hasWindowFocus()) {
                                mp.release();
                                mp = null;
                                notificationManager.cancelAll();
                                finishAndRemoveTask();
                                return;
                            } else {
                                mp.pause();
                                notificationManager.cancelAll();
                                mp.seekTo(0);
                                player.setProgress(0);
                                play_b.setImageResource(R.drawable.play);
                            }
                        } catch (Exception e) {
                            if (mp == null) return;
                        }
                    }
                }
            }
        });
        mp_handler.start();

        // RESTART BUTTON --------------------------------------------------------------------------
        restart_b.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                // The media player is set to zero without stopping or starting media reproduction
                mp.seekTo(0);
                player.setProgress(0);
                notificationManager.cancelAll();
            }
        });

        // STOP BUTTON -----------------------------------------------------------------------------
        stop_b.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                // Close the activity if we are from a file sharing
                if (from_sharing && !hasWindowFocus()){
                    mp.release();
                    mp = null;
                    notificationManager.cancelAll();
                    finishAndRemoveTask();
                }
                else{
                    // Simply restore activity layout otherwise
                    mp.pause();
                    notificationManager.cancelAll();
                    mp.seekTo(0);
                    player.setProgress(0);
                    play_b.setImageResource(R.drawable.play);
                }
            }
        });

        // PLAY-PAUSE BUTTON -----------------------------------------------------------------------
        play_b.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                // Different behaviour if the audio is playing or not
                if (mp.isPlaying()){
                    // Pause audio
                    mp.pause();
                    play_b.setImageResource(R.drawable.play);
                }
                else{
                    // Play audio
                    mp.setPlaybackParams(mp.getPlaybackParams().setSpeed(factor));
                    play_b.setImageResource(R.drawable.pause);
                }
            }
        });

        set_mp_listener(from_sharing);

        // PLAYER TIMELINE -------------------------------------------------------------------------
        player.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            boolean wasPlaying;

            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                // Move media player cursor
                if (fromUser) mp.seekTo(progress*100);
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {
                // Stop audio when moving
                wasPlaying = mp.isPlaying();
                mp.pause();
                notificationManager.cancelAll();
                play_b.setImageResource(R.drawable.play);
            }

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {
                // Start audio when releasing
                if (wasPlaying) {
                    mp.setPlaybackParams(mp.getPlaybackParams().setSpeed(factor));
                    play_b.setImageResource(R.drawable.pause);
                }
            }
        });

        if (from_sharing) {
            // Check if audio is mute
            AudioManager am = (AudioManager) getApplicationContext().getSystemService(Context.AUDIO_SERVICE);
            if (Objects.requireNonNull(am).getStreamVolume(AudioManager.STREAM_MUSIC)==0)
                Toast.makeText(this, "Turn the volume up", Toast.LENGTH_SHORT).show();
            mp.setPlaybackParams(mp.getPlaybackParams().setSpeed(factor));
            mp_updater.start();
            play_b.setImageResource(R.drawable.pause);
        }

    }

    // NEW INTENT RECEIVED -------------------------------------------------------------------------
    @Override
    protected void onNewIntent(Intent newIntent) {
        super.onNewIntent(newIntent);
        // Check if the new intent is from user
        if (Objects.equals(newIntent.getAction(), "android.intent.action.SEND") ||
                Objects.equals(newIntent.getAction(), "android.intent.action.MAIN")) {

            if (mp != null) {
                mp.release();
                mp = null;
            }
            if (notificationManager != null) notificationManager.cancelAll();
            finish();
            startActivity(newIntent);
        }
    }

    // ON DESTROY ----------------------------------------------------------------------------------
    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (mp != null) {
            mp.release();
            mp = null;
        }
        if (notificationManager != null) notificationManager.cancelAll();
    }

    // ON BACK PRESSED -----------------------------------------------------------------------------
    @Override
    public void onBackPressed() {
        if (mp != null) {
            mp.release();
            mp = null;
        }
        if (notificationManager != null) notificationManager.cancelAll();
        finishAndRemoveTask();
    }

    // END OF MAIN ACTIVITY ------------------------------------------------------------------------

}