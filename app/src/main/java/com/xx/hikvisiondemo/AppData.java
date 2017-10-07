package com.xx.hikvisiondemo;

import android.app.Application;
import android.content.Context;
import android.content.SharedPreferences;
import android.media.AudioManager;
import android.media.MediaPlayer;


/**
 * 存储app全局数据，实现全局共享数据
 */
public class AppData extends Application {
    //创建串口通讯类
//    public SeraliPortHelper portHelper = new SeraliPortHelper();

    //获取数据初始设置
    public SharedPreferences preferences;
    public SharedPreferences.Editor editor;
    public static final String TOPIC_SUBCRIBE = "gk/light/3/1/d0001";//订阅硬件方
    public static final String TOPIC_PUBLIC = "gk/light/3/2/d0001";//发布硬件方

    public static final String TOPIC_BACK_SUBCRIBE = "gk/light/1/2/d0001";//订阅后台
    public static final String TOPIC_BACK_PUBLIC = "gk/light/1/1/d0001";//发布后台方

    public static final String ADDRESS = "192.168.1.64";
    public static final int PORT = 8000;
    public static final String USER = "admin";
    public static final String PSD = "admin123";
    //音频播放对象
    public static MediaPlayer mediaPlayer = new MediaPlayer();

    //记录当前的播放状态
    public boolean isPlaying = false;

    //记录当前播放广播的索引
    private int playingIndex = 0;


    //获取当前播放广播的索引
    public int getplayingIndex() {
        return this.playingIndex;
    }

    //设置当前播放广播的索引
    public void setPlayingIndex(int index) {
        this.playingIndex = index;
    }

    //记录一键呼叫的状态：true为开启，false为关闭
    public boolean isOpenCall = false;
    public boolean isOpenCamera = false;

    //设置摄像头参数
    public CameraDevice device = new CameraDevice("192.168.1.64", "8000", "admin", "admin123"/*"goockr86678686"*/, "1");

    @Override
    public void onCreate() {
        super.onCreate();
        //获取数据初始设置
        preferences = getSharedPreferences("preferences", MODE_PRIVATE);
        editor = preferences.edit();
        setinitValue();
    }

    //设置系统初始值
    private void setinitValue() {
        int volumeValue = preferences.getInt("volume", 7);
        String cusZHName = preferences.getString("cusZHName", "果壳科技");
        String cusENName = preferences.getString("cusENName", "Goockr");
        String passWord = preferences.getString("passWord", "123");
        //写入数据
        String address = preferences.getString("callAddress", "季华东路汇源通大厦");
        String latitude = preferences.getString("callLatitude", "N23.022");//纬度
        String longitude = preferences.getString("callLongitude", "E133.176");//经度

        //一键呼叫报警声
        boolean isOpenAlarm = preferences.getBoolean("isOpenAlarm", true);

        //提交存储的数据
        editor.commit();
        if (volumeValue == 7) {
            editor.putInt("volume", 7);
            editor.commit();
        }
        if (cusZHName.equals("果壳科技")) {
            editor.putString("cusZHName", "果壳科技");
            editor.commit();
        }
        if (cusENName.equals("Goockr")) {
            editor.putString("cusENName", "Goockr");
            editor.commit();
        }
        if (passWord.equals("123")) {
            editor.putString("passWord", "123");
            editor.commit();
        }
        if (address.equals("季华东路汇源通大厦")) {
            editor.putString("callAddress", "季华东路汇源通大厦");
            editor.commit();
        }
        if (latitude.equals("N23.022")) {
            editor.putString("callLatitude", "N23.022");
            editor.commit();
        }
        if (longitude.equals("E133.176")) {
            editor.putString("callLongitude", "E133.176");
            editor.commit();
        }
        if (isOpenAlarm == true) {
            editor.putBoolean("isOpenAlarm", true);
            editor.commit();
        }

        //设置初始音量值0-15
        AudioManager am = (AudioManager) getSystemService(Context.AUDIO_SERVICE);
        am.setStreamVolume(AudioManager.STREAM_MUSIC, volumeValue, AudioManager.FLAG_PLAY_SOUND);

    }
}
