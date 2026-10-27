package com.scriptura.app;

import android.Manifest;
import android.app.*;
import android.content.*;
import android.content.pm.PackageManager;
import android.os.Build;
import java.util.Calendar;

public final class Reminders extends BroadcastReceiver {
    private static final String PREFS="scriptura_reminders";
    private static final String CHANNEL="scriptura_daily";
    private static PendingIntent pending(Context c) {
        Intent i=new Intent(c,Reminders.class);
        i.setAction("com.scriptura.app.DAILY_READING");
        return PendingIntent.getBroadcast(c,102,i,PendingIntent.FLAG_UPDATE_CURRENT|PendingIntent.FLAG_IMMUTABLE);
    }
    public static void enable(Context c){
        c.getSharedPreferences(PREFS,Context.MODE_PRIVATE).edit().putBoolean("enabled",true).apply();
        schedule(c);
        if(Build.VERSION.SDK_INT>=33 && c instanceof Activity &&
            c.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)!=PackageManager.PERMISSION_GRANTED)
            ((Activity)c).requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS},153);
    }
    public static void disable(Context c){
        c.getSharedPreferences(PREFS,Context.MODE_PRIVATE).edit().putBoolean("enabled",false).apply();
        AlarmManager manager=(AlarmManager)c.getSystemService(Context.ALARM_SERVICE);
        if(manager!=null)manager.cancel(pending(c));
    }
    private static void schedule(Context c){
        AlarmManager manager=(AlarmManager)c.getSystemService(Context.ALARM_SERVICE);
        if(manager==null)return;
        Calendar nine=Calendar.getInstance();nine.set(Calendar.HOUR_OF_DAY,9);nine.set(Calendar.MINUTE,0);
        nine.set(Calendar.SECOND,0);nine.set(Calendar.MILLISECOND,0);
        if(nine.getTimeInMillis()<=System.currentTimeMillis())nine.add(Calendar.DATE,1);
        manager.setInexactRepeating(AlarmManager.RTC_WAKEUP,nine.getTimeInMillis(),AlarmManager.INTERVAL_DAY,pending(c));
    }
    @Override public void onReceive(Context context,Intent intent){
        if(Intent.ACTION_BOOT_COMPLETED.equals(intent.getAction())){
            if(context.getSharedPreferences(PREFS,Context.MODE_PRIVATE).getBoolean("enabled",false))schedule(context);
            return;
        }
        if(!"com.scriptura.app.DAILY_READING".equals(intent.getAction()))return;
        if(!context.getSharedPreferences(PREFS,Context.MODE_PRIVATE).getBoolean("enabled",false))return;
        if(Build.VERSION.SDK_INT>=33&&context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)!=PackageManager.PERMISSION_GRANTED)return;
        NotificationManager nm=(NotificationManager)context.getSystemService(Context.NOTIFICATION_SERVICE);
        if(nm==null)return;
        if(Build.VERSION.SDK_INT>=26)nm.createNotificationChannel(
            new NotificationChannel(CHANNEL,"Lectura diaria",NotificationManager.IMPORTANCE_DEFAULT));
        Intent open=new Intent(context,MainActivity.class);
        open.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK|Intent.FLAG_ACTIVITY_CLEAR_TOP);
        PendingIntent click=PendingIntent.getActivity(context,103,open,
            PendingIntent.FLAG_UPDATE_CURRENT|PendingIntent.FLAG_IMMUTABLE);
        Notification notice=new Notification.Builder(context,CHANNEL)
            .setSmallIcon(android.R.drawable.ic_menu_myplaces)
            .setContentTitle("Scriptura · Una palabra para hoy")
            .setContentText("Descubre tus lecturas del Antiguo y Nuevo Testamento.")
            .setContentIntent(click).setAutoCancel(true).build();
        nm.notify(405,notice);
    }
}
