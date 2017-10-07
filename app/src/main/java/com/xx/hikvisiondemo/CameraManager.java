package com.xx.hikvisiondemo;

import android.content.Context;
import android.content.Intent;
import android.os.SystemClock;
import android.util.Log;
import android.view.SurfaceHolder;

import com.hikvision.netsdk.HCNetSDK;
import com.hikvision.netsdk.NET_DVR_DEVICEINFO_V30;
import com.hikvision.netsdk.NET_DVR_PREVIEWINFO;
import com.hikvision.netsdk.PTZCommand;
import com.hikvision.netsdk.PTZPresetCmd;
import com.hikvision.netsdk.RealPlayCallBack;

import org.MediaPlayer.PlayM4.Player;
import org.MediaPlayer.PlayM4.PlayerCallBack;

/**
 * Created by JJT-ssd on 2016/9/23.
 */
public class CameraManager {
    private final static String TAG = "HC_DEBUG";
    public final static String ACTION_START_RENDERING = "action_start_rendering";
    public final static String ACTION_DVR_OUTLINE = "action_dvr_outline";
    /**
     * 设备信息 模拟通道数bychannum 数字通道数byipchanum
     */
    private NET_DVR_DEVICEINFO_V30 deviceInfo_V30 = null;
    /**
     * 登入标记 -1未登入，0已登入
     */
    private int m_iLogID = -1;

    /**
     * 播放标记 -1未播放，0正在播放
     */
    private int m_iPlayID = -1;
    private int m_iPort = -1;
    private String ip;
    private int port;
    private String username;
    private String password;
    private int channel;
    private SurfaceHolder holder;
    /**
     * 用于发广播的上下文
     */
    private Context context;

    private CameraManager manager = null;
    private boolean m_bStopPlayback;

    public CameraManager() {

    }

//    public static  synchronized CameraManager getInstance() {
//        if (manager == null) {
//            manager = new CameraManager();
//        }
//        return manager;
//    }

    /**
     * 设置播放设备信息
     *
     * @param device
     */
    public void setCameraDevice(CameraDevice device) {
        this.ip = device.getIP();
        this.port = Integer.parseInt(device.getPort());
        this.username = device.getUserName();
        this.password = device.getPassWord();
        this.channel = Integer.parseInt(device.getChannel());
    }

    /**
     * 设置播放视口
     *
     * @param holder
     */
    public void setSurfaceHolder(SurfaceHolder holder) {
        this.holder = holder;
    }

    /**
     * 用于发送广播的上下文
     *
     * @param context
     */
    public void setContext(Context context) {
        this.context = context;
    }

    public void initSDK() {
        if (m_iPlayID >= 0) {
            stopPlay();
        }
        if (HCNetSDK.getInstance().NET_DVR_Init()) {
            Log.i(TAG, "初始化SDK成功！");
        } else {
            Log.e(TAG, "初始化SDK失败！");
        }
    }

    public void loginDevice() {
        deviceInfo_V30 = new NET_DVR_DEVICEINFO_V30();
        m_iLogID = HCNetSDK.getInstance().NET_DVR_Login_V30(ip, port, username,
                password, deviceInfo_V30);

        System.out.println("下面是设备信息************************");
        System.out.println("userId=" + m_iLogID);
        System.out.println("通道开始=" + deviceInfo_V30.byStartChan);
        System.out.println("通道个数=" + deviceInfo_V30.byChanNum);
        System.out.println("设备类型=" + deviceInfo_V30.byDVRType);
        System.out.println("ip通道个数=" + deviceInfo_V30.byIPChanNum);

        if (m_iLogID < 0) {
            int errorCode = HCNetSDK.getInstance().NET_DVR_GetLastError();
            Log.e(TAG,
                    "登入设备失败！" + getErrorMsg(errorCode));
        } else {
            Log.i(TAG, "登入设备成功！");
        }
    }


    //preSetPosition为预设点 取1为预设点1 取2为预设点2
    public synchronized void realPlay(int preSetPosition) {
        try {
            if (m_iLogID < 0) {
                Log.e(TAG, "尝试重新登入");
                int count = 0;
                while (count < 10) {
                    Log.i(TAG, "正在第" + (count + 1) + "次重新登入");
                    loginDevice();
                    if (m_iLogID < 0) {
                        count++;
                        SystemClock.sleep(300);
                    } else {
                        Log.i(TAG, "第" + (count + 1) + "次登入成功");
                        break;
                    }
                }
                if (m_iLogID < 0) {
                    Log.e(TAG, "尝试登入" + count + "次均失败！");
                    return;
                }
            }

            if (m_iPlayID < 0) {
                // 预览参数配置
//                NET_DVR_CLIENTINFO ClientInfo = new NET_DVR_CLIENTINFO();
                NET_DVR_PREVIEWINFO ClientInfo = new NET_DVR_PREVIEWINFO();
//                ClientInfo.lChannel = channel + deviceInfo_V30.byStartChan;
                ClientInfo.lChannel = 0;
                ClientInfo.dwStreamType = 0; // substream
                ClientInfo.bBlocked = 1;
                // 多播地址，需要多播预览时配置
//                ClientInfo.sMultiCastIP = "";

                m_iPlayID = HCNetSDK.getInstance().NET_DVR_RealPlay_V40(
                        m_iLogID, ClientInfo, getRealPlayerCallBack());

                if (m_iPlayID < 0) {
                    int count = 0;
                    while (true) {
                        m_iPlayID = HCNetSDK.getInstance().NET_DVR_RealPlay_V40(
                                m_iLogID, ClientInfo, getRealPlayerCallBack());
                        if (m_iPlayID < 0) {
                            count++;
                            SystemClock.sleep(1000);
                        } else {
                            //设置默认点
                            gotoPreSetPoint(preSetPosition);
                            break;
                        }
                    }
                    if (m_iPlayID < 0) {
                        Log.e(TAG, "实时播放失败！"
                                + getErrorMsg(HCNetSDK.getInstance()
                                .NET_DVR_GetLastError()));
                        if (HCNetSDK.getInstance().NET_DVR_GetLastError() == 416) {
                            // 发送广播
                            context.sendBroadcast(new Intent(ACTION_DVR_OUTLINE));
                        }
                        return;
                    }
                } else {
                    Log.i(TAG, "开始实时播放！");
                    //设置默认点
                    gotoPreSetPoint(preSetPosition);
                }
            } else {
                Log.d(TAG, "正在播放中？");
            }
        } catch (Exception e) {
            Log.e(TAG, "异常：" + e.toString());
        }
    }

    private void startSinglePreview() {
        if (m_iPlayID >= 0) {
            Log.i(TAG, "Please stop palyback first");
            return;
        }
        RealPlayCallBack fRealDataCallBack = getRealPlayerCbf();
        if (fRealDataCallBack == null) {
            Log.e(TAG, "fRealDataCallBack object is failed!");
            return;
        }
        Log.i(TAG, "m_iStartChan:" + 0);

        NET_DVR_PREVIEWINFO previewInfo = new NET_DVR_PREVIEWINFO();
        previewInfo.lChannel = 0;
        previewInfo.dwStreamType = 0; // substream
        previewInfo.bBlocked = 1;
//         NET_DVR_CLIENTINFO struClienInfo = new NET_DVR_CLIENTINFO();
//         struClienInfo.lChannel = m_iStartChan;
//         struClienInfo.lLinkMode = 0;
        // HCNetSDK start preview
        m_iPlayID = HCNetSDK.getInstance().NET_DVR_RealPlay_V40(m_iLogID,
                previewInfo, fRealDataCallBack);
//         m_iPlayID = HCNetSDK.getInstance().NET_DVR_RealPlay_V30(m_iLogID,
//         struClienInfo, fRealDataCallBack, false);
        if (m_iPlayID < 0) {
            Log.e(TAG, "NET_DVR_RealPlay is failed!Err:"
                    + HCNetSDK.getInstance().NET_DVR_GetLastError());
            return;
        }

        Log.i(TAG,
                "NetSdk Play sucess ***********************3***************************");
//        m_oPreviewBtn.setText("Stop");
    }

    /**
     * @return callback instance
     * @fn getRealPlayerCbf
     * @author zhuzhenlei
     * @brief get realplay callback instance
     */
    private RealPlayCallBack getRealPlayerCbf() {
        RealPlayCallBack cbf = new RealPlayCallBack() {
            public void fRealDataCallBack(int iRealHandle, int iDataType,
                                          byte[] pDataBuffer, int iDataSize) {
                // player channel 1
                processRealData(1, iDataType, pDataBuffer,
                        iDataSize, Player.STREAM_REALTIME);
            }
        };
        return cbf;
    }

    private boolean m_bNeedDecode = true;

    /**
     * @param iPlayViewNo - player channel [in]
     * @param iDataType   - data type [in]
     * @param pDataBuffer - data buffer [in]
     * @param iDataSize   - data size [in]
     * @param iStreamMode - stream mode [in]
     * @param NULL        [out]
     * @return NULL
     * @fn processRealData
     * @author zhuzhenlei
     * @brief process real data
     */
    public void processRealData(int iPlayViewNo, int iDataType,
                                byte[] pDataBuffer, int iDataSize, int iStreamMode) {
        if (!m_bNeedDecode) {
            // Log.i(TAG, "iPlayViewNo:" + iPlayViewNo + ",iDataType:" +
            // iDataType + ",iDataSize:" + iDataSize);
        } else {
            if (HCNetSDK.NET_DVR_SYSHEAD == iDataType) {
                if (m_iPort >= 0) {
                    return;
                }
                m_iPort = Player.getInstance().getPort();
                if (m_iPort == -1) {
                    Log.e(TAG, "getPort is failed with: "
                            + Player.getInstance().getLastError(m_iPort));
                    return;
                }
                Log.i(TAG, "getPort succ with: " + m_iPort);
                if (iDataSize > 0) {
                    if (!Player.getInstance().setStreamOpenMode(m_iPort,
                            iStreamMode)) // set stream mode
                    {
                        Log.e(TAG, "setStreamOpenMode failed");
                        return;
                    }
                    if (!Player.getInstance().openStream(m_iPort, pDataBuffer,
                            iDataSize, 2 * 1024 * 1024)) // open stream
                    {
                        Log.e(TAG, "openStream failed");
                        return;
                    }
                    if (!Player.getInstance().play(m_iPort,
                            holder)) {
                        Log.e(TAG, "play failed");
                        return;
                    }
                    if (!Player.getInstance().playSound(m_iPort)) {
                        Log.e(TAG, "playSound failed with error code:"
                                + Player.getInstance().getLastError(m_iPort));
                        return;
                    }
                }
            } else {
                if (!Player.getInstance().inputData(m_iPort, pDataBuffer,
                        iDataSize)) {
                    // Log.e(TAG, "inputData failed with: " +
                    // Player.getInstance().getLastError(m_iPort));
                    for (int i = 0; i < 4000 && m_iPlayID >= 0
                            && !m_bStopPlayback; i++) {
                        if (Player.getInstance().inputData(m_iPort,
                                pDataBuffer, iDataSize)) {
                            break;

                        }

                        if (i % 100 == 0) {
                            Log.e(TAG, "inputData failed with: "
                                    + Player.getInstance()
                                    .getLastError(m_iPort) + ", i:" + i);
                        }

                        try {
                            Thread.sleep(10);
                        } catch (InterruptedException e) {
                            // TODO Auto-generated catch block
                            e.printStackTrace();

                        }
                    }
                }

            }
        }

    }

    /**
     * 获取实时播放回调
     *
     * @return
     */
    private RealPlayCallBack getRealPlayerCallBack() {
        return new RealPlayCallBack() {
            /**
             * iRealHandle 当前的预览句柄 iDataType 数据类型 pDataBuffer 存放数据的缓冲区指针
             * iDataSize 缓冲区大小
             */
            @Override
            public void fRealDataCallBack(int iRealHandle, int iDataType,
                                          byte[] pDataBuffer, int iDataSize) {
                processRealData(iDataType, pDataBuffer, iDataSize,
                        Player.STREAM_REALTIME);
            }
        };
    }

    ///pointNum>=1
    public void gotoPreSetPoint(int pointNum) {
        if (m_iLogID < 0) return;
        boolean isArrive = false;
        while (!isArrive) {
            isArrive = HCNetSDK.getInstance().NET_DVR_PTZPreset_Other(m_iLogID, 1, PTZPresetCmd.GOTO_PRESET, pointNum);
        }

    }

    public void setLoginId(int m_iLogID) {
        this.m_iLogID = m_iLogID;
    }

    /**
     * 视频流解码
     *
     * @param iDataType   数据类型
     * @param pDataBuffer 存放数据的缓冲区指针
     * @param iDataSize   缓冲区大小
     * @param iStreamMode 播放模式
     */
    private void processRealData(int iDataType, byte[] pDataBuffer,
                                 int iDataSize, int iStreamMode) {
        Log.d(TAG, "processRealData: " + iDataType);
        Log.d(TAG, "processRealData: " + pDataBuffer);
        Log.d(TAG, "processRealData: " + iDataSize);
        Log.d(TAG, "processRealData: " + iStreamMode);
        int i = 0;
        try {
            switch (iDataType) {
                case HCNetSDK.NET_DVR_SYSHEAD:
                    Log.d(TAG, "处理头数据");
                    if (m_iPort >= 0) {
                        break;
                    }
                    m_iPort = Player.getInstance().getPort();
                    if (m_iPort == -1) break;//获取播放端口失败！
                    if (iDataSize > 0) {
                        if (!Player.getInstance().setStreamOpenMode(m_iPort, iStreamMode))
                            break; //设置视频流失败！
                        // 抓图回调函数
                        PlayerCallBack.PlayerDisplayCB displayCB = new PlayerCallBack.PlayerDisplayCB() {
                            //就这个函数出毛病了
                            @Override
                            public void onDisplay(int arg0, byte[] arg1, int arg2,
                                                  int arg3, int arg4, int arg5, int arg6, int arg7) {
                                if (null != context) {
                                    context.sendBroadcast(new Intent(
                                            ACTION_START_RENDERING));
                                } else {
                                    Log.e(TAG,
                                            "Context为空！没有setContext(Context context)？");
                                }
                                Log.d(TAG, "开始画面渲染");
                                if (!Player.getInstance()
                                        .setDisplayCB(m_iPort, null)) {
                                    //移除显示回调失败！
                                }
                            }
                        };
                        if (!Player.getInstance().setDisplayCB(m_iPort, displayCB)) {
                            //设置显示回调失败！
                            break;
                        }
                        if (!Player.getInstance().setDisplayBuf(m_iPort, 10)) { // 帧率，不设置为默认15
                            //设置播放缓冲区最大缓冲帧数失败！
                            break;
                        }
                        if (!Player.getInstance().openStream(m_iPort, pDataBuffer,
                                iDataSize, 2 * 1024 * 1024)) {
                            //打开视频流失败！
                            break;
                        }
                        if (!Player.getInstance().play(m_iPort, holder)) {
                            //本地播放失败！
                            break;
                        }
                    } else {
                        Log.e(TAG, "视频流无数据！");
                    }
                    break;
                case HCNetSDK.NET_DVR_STREAMDATA:
                case HCNetSDK.NET_DVR_STD_AUDIODATA:
                case HCNetSDK.NET_DVR_STD_VIDEODATA:
                    // Log.i(TAG, "处理流数据");
                    if (iDataSize > 0 && m_iPort != -1) {
                        for (i = 0; i < 400; i++) {
                            if (Player.getInstance().inputData(m_iPort,
                                    pDataBuffer, iDataSize)) {
                                //输入数据成功！
                                break;
                            }
                            Thread.sleep(10);
                        }
                        if (i == 400) {
                            Log.e(TAG, "输入数据失败！");
                        }
                    }
                    break;
                default:
                    break;
            }
        } catch (Exception e) {
            Log.e(TAG, "视频流解码异常！" + e.toString());
        }
    }

    public void startMove(int orientation, int m_iLogID) {
        if (m_iLogID < 0) {
            return;
        }
        switch (orientation) {
            case 9:
                HCNetSDK.getInstance().NET_DVR_PTZControl_Other(m_iLogID, 1,
                        PTZCommand.UP_RIGHT, 0);
                break;
            case 8:
                HCNetSDK.getInstance().NET_DVR_PTZControl_Other(m_iLogID, 1,
                        PTZCommand.TILT_UP, 0);
                break;
            case 7:
                HCNetSDK.getInstance().NET_DVR_PTZControl_Other(m_iLogID, 1,
                        PTZCommand.UP_LEFT, 0);
                break;
            case 6:
                HCNetSDK.getInstance().NET_DVR_PTZControl_Other(m_iLogID, 1,
                        PTZCommand.PAN_RIGHT, 0);
                break;
            case 5:
                HCNetSDK.getInstance().NET_DVR_PTZControl_Other(m_iLogID, 1,
                        PTZCommand.PAN_AUTO, 0);
                break;
            case 4:
                HCNetSDK.getInstance().NET_DVR_PTZControl_Other(m_iLogID, 1,
                        PTZCommand.PAN_LEFT, 0);
                break;
            case 3:
                HCNetSDK.getInstance().NET_DVR_PTZControl_Other(m_iLogID, 1,
                        PTZCommand.DOWN_RIGHT, 0);
                break;
            case 2:
                HCNetSDK.getInstance().NET_DVR_PTZControl_Other(m_iLogID, 1,
                        PTZCommand.TILT_DOWN, 0);
                break;
            case 1:
                HCNetSDK.getInstance().NET_DVR_PTZControl_Other(m_iLogID, 1,
                        PTZCommand.DOWN_LEFT, 0);
                break;
            default:
                break;
        }
    }

    /**
     * 停止移动 NET_DVR_PTZControl_Other参数：(播放标记, 通道， 指令码, 开始标记0或停止标记1)
     *
     * @param orientation 九宫格数字方向
     */
    public void stopMove(int orientation, int m_iLogID) {
        if (m_iLogID < 0) {
            return;
        }
        switch (orientation) {
            case 9:
                HCNetSDK.getInstance().NET_DVR_PTZControl_Other(m_iLogID, 1,
                        PTZCommand.UP_RIGHT, 1);
                break;
            case 8:
                HCNetSDK.getInstance().NET_DVR_PTZControl_Other(m_iLogID, 1,
                        PTZCommand.TILT_UP, 1);
                break;
            case 7:
                HCNetSDK.getInstance().NET_DVR_PTZControl_Other(m_iLogID, 1,
                        PTZCommand.UP_LEFT, 1);
                break;
            case 6:
                HCNetSDK.getInstance().NET_DVR_PTZControl_Other(m_iLogID, 1,
                        PTZCommand.PAN_RIGHT, 1);
                break;
            case 5:
                HCNetSDK.getInstance().NET_DVR_PTZControl_Other(m_iLogID, 1,
                        PTZCommand.PAN_AUTO, 1);
                break;
            case 4:
                HCNetSDK.getInstance().NET_DVR_PTZControl_Other(m_iLogID, 1,
                        PTZCommand.PAN_LEFT, 1);
                break;
            case 3:
                HCNetSDK.getInstance().NET_DVR_PTZControl_Other(m_iLogID, 1,
                        PTZCommand.DOWN_RIGHT, 1);
                break;
            case 2:
                HCNetSDK.getInstance().NET_DVR_PTZControl_Other(m_iLogID, 1,
                        PTZCommand.TILT_DOWN, 1);
                break;
            case 1:
                HCNetSDK.getInstance().NET_DVR_PTZControl_Other(m_iLogID, 1,
                        PTZCommand.DOWN_LEFT, 1);
                break;
            default:
                break;
        }
    }

    /**
     * 开始缩放 NET_DVR_PTZControl_Other参数：(播放标记, 通道， 指令码, 开始标记0或停止标记1)
     *
     * @param x -1缩小 1放大
     */
    public void startZoom(int x, int m_iLogID) {
//        if (m_iPlayID < 0) {
//            return;
//        }
        if (x < 0) {
            HCNetSDK.getInstance().NET_DVR_PTZControl_Other(m_iLogID, 1,
                    PTZCommand.ZOOM_OUT, 0);
        } else {
            HCNetSDK.getInstance().NET_DVR_PTZControl_Other(m_iLogID, 1,
                    PTZCommand.ZOOM_IN, 0);
        }
    }

    /**
     * 停止缩放 NET_DVR_PTZControl_Other参数：(播放标记, 通道， 指令码, 开始标记0或停止标记1)
     *
     * @param x -1缩小 1放大
     */
    public void stopZoom(int x, int m_iLogID) {
        if (m_iLogID < 0) {
            return;
        }
        if (x < 0) {
            HCNetSDK.getInstance().NET_DVR_PTZControl_Other(m_iLogID, 1,
                    PTZCommand.ZOOM_OUT, 1);
        } else {
            HCNetSDK.getInstance().NET_DVR_PTZControl_Other(m_iLogID, 1,
                    PTZCommand.ZOOM_IN, 1);

        }
    }

    /**
     * 开始变焦 NET_DVR_PTZControl_Other参数：(播放标记, 通道， 指令码, 开始标记0或停止标记1)
     *
     * @param x -1近端 1远端
     */
    public void startFocus(int x) {
        if (m_iPlayID < 0) {
            return;
        }
        if (x < 0) {
            HCNetSDK.getInstance().NET_DVR_PTZControl_Other(m_iLogID, 1,
                    PTZCommand.FOCUS_NEAR, 0);
        } else {
            HCNetSDK.getInstance().NET_DVR_PTZControl_Other(m_iLogID, 1,
                    PTZCommand.FOCUS_FAR, 0);
        }
    }

    /**
     * 停止变焦 NET_DVR_PTZControl_Other参数：(播放标记, 通道， 指令码, 开始标记0或停止标记1)
     *
     * @param x -1近端 1远端
     */
    public void stopFocus(int x) {
        if (m_iPlayID < 0) {
            return;
        }
        if (x < 0) {
            HCNetSDK.getInstance().NET_DVR_PTZControl_Other(m_iLogID, 1,
                    PTZCommand.FOCUS_NEAR, 1);

        } else {
            HCNetSDK.getInstance().NET_DVR_PTZControl_Other(m_iLogID, 1,
                    PTZCommand.FOCUS_FAR, 1);
        }

    }


    /**
     * 停止播放
     */
    public synchronized void stopPlay() {
        if (m_iPlayID < 0) {
            return;//已经停止
        }
        // 停止网络播放
        if (HCNetSDK.getInstance().NET_DVR_StopRealPlay(m_iPlayID)) {
            Log.i(TAG, "停止实时播放成功！");
        } else {
            Log.e(TAG,
                    "停止实时播放失败！"
                            + getErrorMsg(HCNetSDK.getInstance()
                            .NET_DVR_GetLastError()));
            return;
        }
        // 停止本地播放
        if (Player.getInstance().stop(m_iPort)) {
            Log.i(TAG, "停止本地播放成功！");
        } else {
            Log.e(TAG, "停止本地播放失败！");
            return;
        }
        // 关闭视频流
        if (Player.getInstance().closeStream(m_iPort)) {
            Log.i(TAG, "关闭视频流成功！");
        } else {
            Log.e(TAG, "关闭视频流失败！");
            return;
        }
        // 释放播放端口
        if (Player.getInstance().freePort(m_iPort)) {
            Log.i(TAG, "释放播放端口成功！");
        } else {
            Log.e(TAG, "释放播放端口失败！");
            return;
        }
        // 播放端口复位
        m_iPort = -1;
        // 正在播放标记复位
        m_iPlayID = -1;
        Log.i(TAG, "停止播放成功！");
    }

    /**
     * 登出设备
     */
    public void logoutDevice() {
        stopPlay();
        if (HCNetSDK.getInstance().NET_DVR_Logout_V30(m_iLogID)) {
            m_iLogID = -1;
            Log.i(TAG, "登出设备成功！");
        } else {
            m_iLogID = 0;
            Log.e(TAG,
                    "登出设备失败！"
                            + getErrorMsg(HCNetSDK.getInstance()
                            .NET_DVR_GetLastError()));
        }
    }

    /**
     * 释放海康SDK
     */
    public void freeSDK() {
        // 清理缓存
        if (HCNetSDK.getInstance().NET_DVR_Cleanup()) {
            Log.i(TAG, "释放SDK资源成功！");
        } else {
            Log.e(TAG, "释放SDK资源失败！");
        }
    }

    //预置点操作
    public void setPoint(int point,int m_iPlayID) {
        switch (point) {
            case 8://设置预置点

                break;
            case 9://清楚预置点
                break;
            case 39://转到预置点

                break;
        }
    }

    /**
     * 获取错误信息
     *
     * @param errorCode
     * @return
     */
    private String getErrorMsg(int errorCode) {
        switch (errorCode) {
            case 0:
                return "[" + errorCode + " NET_DVR_NOERROR] 没有错误。";
            case 1:
                return "[" + errorCode
                        + " NET_DVR_PASSWORD_ERROR] 用户名密码错误。注册时输入的用户名或者密码错误。";
            case 2:
                return "["
                        + errorCode
                        + " NET_DVR_NOENOUGHPRI] 权限不足。该注册用户没有权限执行当前对设备的操作，可以与远程用户参数配置做对比。";
            case 3:
                return "[" + errorCode + " NET_DVR_NOINIT] SDK未初始化。";
            case 4:
                return "[" + errorCode
                        + " NET_DVR_CHANNEL_ERROR] 通道号错误。设备没有对应的通道号。";
            case 5:
                return "[" + errorCode + " NET_DVR_OVER_MAXLINK] 设备总的连接数超过最大。";
            case 6:
                return "[" + errorCode
                        + " NET_DVR_VERSIONNOMATCH] 版本不匹配。SDK和设备的版本不匹配。";
            case 7:
                return "["
                        + errorCode
                        + " NET_DVR_NETWORK_FAIL_CONNECT] 连接设备失败。设备不在线或网络原因引起的连接超时等。";
            case 8:
                return "[" + errorCode + " NET_DVR_NETWORK_SEND_ERROR] 向设备发送失败。";
            case 9:
                return "[" + errorCode + " NET_DVR_NETWORK_RECV_ERROR] 从设备接收数据失败。";
            case 10:
                return "[" + errorCode
                        + " NET_DVR_NETWORK_RECV_TIMEOUT] 从设备接收数据超时。";
            case 11:
                return "["
                        + errorCode
                        + " NET_DVR_NETWORK_ERRORDATA] 传送的数据有误。发送给设备或者从设备接收到的数据错误，如远程参数配置时输入设备不支持的值。";
            case 12:
                return "[" + errorCode + " NET_DVR_ORDER_ERROR] 调用次序错误。";
            case 13:
                return "[" + errorCode + " NET_DVR_OPERNOPERMIT] 无此权限。";
            case 14:
                return "[" + errorCode + " NET_DVR_COMMANDTIMEOUT] 设备命令执行超时。";
            case 15:
                return "[" + errorCode
                        + " NET_DVR_ERRORSERIALPORT] 串口号错误。指定的设备串口号不存在。";
            case 16:
                return "[" + errorCode
                        + " NET_DVR_ERRORALARMPORT] 报警端口错误。指定的设备报警输出端口不存在。";
            case 17:
                return "["
                        + errorCode
                        + " NET_DVR_PARAMETER_ERROR] 参数错误。SDK接口中给入的输入或输出参数为空，或者参数格式或值不符合要求。";
            case 18:
                return "[" + errorCode + " NET_DVR_CHAN_EXCEPTION] 设备通道处于错误状态。";
            case 19:
                return "[" + errorCode
                        + " NET_DVR_NODISK] 设备无硬盘。当设备无硬盘时，对设备的录像文件、硬盘配置等操作失败。";
            case 20:
                return "["
                        + errorCode
                        + " NET_DVR_ERRORDISKNUM] 硬盘号错误。当对设备进行硬盘管理操作时，指定的硬盘号不存在时返回该错误。";
            case 21:
                return "[" + errorCode + " NET_DVR_DISK_FULL] 设备硬盘满。";
            case 22:
                return "[" + errorCode + " NET_DVR_DISK_ERROR] 设备硬盘出错。";
            case 23:
                return "[" + errorCode + " NET_DVR_NOSUPPORT] 设备不支持。";
            case 24:
                return "[" + errorCode + " NET_DVR_BUSY] 设备忙。";
            case 25:
                return "[" + errorCode + " NET_DVR_MODIFY_FAIL] 设备修改不成功。";
            case 26:
                return "[" + errorCode
                        + " NET_DVR_PASSWORD_FORMAT_ERROR] 密码输入格式不正确。";
            case 27:
                return "[" + errorCode + " NET_DVR_DISK_FORMATING] 硬盘正在格式化，不能启动操作。";
            case 28:
                return "[" + errorCode + " NET_DVR_DVRNORESOURCE] 设备资源不足。";
            case 29:
                return "[" + errorCode + " NET_DVR_DVROPRATEFAILED] 设备操作失败。";
            case 30:
                return "["
                        + errorCode
                        + " NET_DVR_OPENHOSTSOUND_FAIL] 语音对讲、语音广播操作中采集本地音频或打开音频输出失败。";
            case 31:
                return "[" + errorCode + " NET_DVR_DVRVOICEOPENED] 设备语音对讲被占用。";
            case 32:
                return "[" + errorCode + " NET_DVR_TIMEINPUTERROR] 时间输入不正确。";
            case 33:
                return "[" + errorCode + " NET_DVR_NOSPECFILE] 回放时设备没有指定的文件。";
            case 34:
                return "["
                        + errorCode
                        + " NET_DVR_CREATEFILE_ERROR] 创建文件出错。本地录像、保存图片、获取配置文件和远程下载录像时创建文件失败。";
            case 35:
                return "["
                        + errorCode
                        + " NET_DVR_FILEOPENFAIL] 打开文件出错。设置配置文件、设备升级、上传审讯文件时打开文件失败。";
            case 36:
                return "[" + errorCode + " NET_DVR_OPERNOTFINISH] 上次的操作还没有完成。";
            case 37:
                return "[" + errorCode + " NET_DVR_GETPLAYTIMEFAIL] 获取当前播放的时间出错。";
            case 38:
                return "[" + errorCode + " NET_DVR_PLAYFAIL] 播放出错。";
            case 39:
                return "[" + errorCode + " NET_DVR_FILEFORMAT_ERROR] 文件格式不正确。";
            case 40:
                return "[" + errorCode + " NET_DVR_DIR_ERROR] 路径错误。";
            case 41:
                return "[" + errorCode
                        + " NET_DVR_ALLOC_RESOURCE_ERROR] SDK资源分配错误。";
            case 42:
                return "["
                        + errorCode
                        + " NET_DVR_AUDIO_MODE_ERROR] 声卡模式错误。当前打开声音播放模式与实际设置的模式不符出错。";
            case 43:
                return "[" + errorCode
                        + " NET_DVR_NOENOUGH_BUF] 缓冲区太小。接收设备数据的缓冲区或存放图片缓冲区不足。";
            case 44:
                return "[" + errorCode + " NET_DVR_CREATESOCKET_ERROR] 创建SOCKET出错。";
            case 45:
                return "[" + errorCode + " NET_DVR_SETSOCKET_ERROR] 设置SOCKET出错。";
            case 46:
                return "[" + errorCode
                        + " NET_DVR_MAX_NUM] 个数达到最大。分配的注册连接数、预览连接数超过SDK支持的最大数。";
            case 47:
                return "[" + errorCode
                        + " NET_DVR_USERNOTEXIST] 用户不存在。注册的用户ID已注销或不可用。";
            case 48:
                return "[" + errorCode
                        + " NET_DVR_WRITEFLASHERROR] 写FLASH出错。设备升级时写FLASH失败。";
            case 49:
                return "[" + errorCode
                        + " NET_DVR_UPGRADEFAIL] 设备升级失败。网络或升级文件语言不匹配等原因升级失败。";
            case 50:
                return "[" + errorCode + " NET_DVR_CARDHAVEINIT] 解码卡已经初始化过。";
            case 51:
                return "[" + errorCode + " NET_DVR_PLAYERFAILED] 调用播放库中某个函数失败。";
            case 52:
                return "[" + errorCode + " NET_DVR_MAX_USERNUM] 登录设备的用户数达到最大。";
            case 53:
                return "[" + errorCode
                        + " NET_DVR_GETLOCALIPANDMACFAIL] 获得本地PC的IP地址或物理地址失败。";
            case 54:
                return "[" + errorCode + " NET_DVR_NOENCODEING] 设备该通道没有启动编码。";
            case 55:
                return "[" + errorCode + " NET_DVR_IPMISMATCH] IP地址不匹配。";
            case 56:
                return "[" + errorCode + " NET_DVR_MACMISMATCH] MAC地址不匹配。";
            case 57:
                return "[" + errorCode + " NET_DVR_UPGRADELANGMISMATCH] 升级文件语言不匹配。";
            case 58:
                return "[" + errorCode + " NET_DVR_MAX_PLAYERPORT] 播放器路数达到最大。";
            case 59:
                return "[" + errorCode + " NET_DVR_NOSPACEBACKUP] 备份设备中没有足够空间进行备份。";
            case 60:
                return "[" + errorCode + " NET_DVR_NODEVICEBACKUP] 没有找到指定的备份设备。";
            case 61:
                return "[" + errorCode
                        + " NET_DVR_PICTURE_BITS_ERROR] 图像素位数不符，限24色。";
            case 62:
                return "[" + errorCode
                        + " NET_DVR_PICTURE_DIMENSION_ERROR] 图片高*宽超限，限128*256。";
            case 63:
                return "[" + errorCode
                        + " NET_DVR_PICTURE_SIZ_ERROR] 图片大小超限，限100K。";
            case 64:
                return "[" + errorCode
                        + " NET_DVR_LOADPLAYERSDKFAILED] 载入当前目录下Player Sdk出错。";
            case 65:
                return "[" + errorCode
                        + " NET_DVR_LOADPLAYERSDKPROC_ERROR] 找不到Player Sdk中某个函数入口。";
            case 66:
                return "[" + errorCode
                        + " NET_DVR_LOADDSSDKFAILED] 载入当前目录下DSsdk出错。";
            case 67:
                return "[" + errorCode
                        + " NET_DVR_LOADDSSDKPROC_ERROR] 找不到DsSdk中某个函数入口。";
            case 68:
                return "[" + errorCode
                        + " NET_DVR_DSSDK_ERROR] 调用硬解码库DsSdk中某个函数失败。";
            case 69:
                return "[" + errorCode + " NET_DVR_VOICEMONOPOLIZE] 声卡被独占。";
            case 70:
                return "[" + errorCode + " NET_DVR_JOINMULTICASTFAILED] 加入多播组失败。";
            case 71:
                return "[" + errorCode + " NET_DVR_CREATEDIR_ERROR] 建立日志文件目录失败。";
            case 72:
                return "[" + errorCode + " NET_DVR_BINDSOCKET_ERROR] 绑定套接字失败。";
            case 73:
                return "["
                        + errorCode
                        + " NET_DVR_SOCKETCLOSE_ERROR] socket连接中断，此错误通常是由于连接中断或目的地不可达。";
            case 74:
                return "[" + errorCode + " NET_DVR_USERID_ISUSING] 注销时用户ID正在进行某操作。";
            case 75:
                return "[" + errorCode + " NET_DVR_SOCKETLISTEN_ERROR] 监听失败。";
            case 76:
                return "[" + errorCode + " NET_DVR_PROGRAM_EXCEPTION] 程序异常。";
            case 77:
                return "["
                        + errorCode
                        + " NET_DVR_WRITEFILE_FAILED] 写文件失败。本地录像、远程下载录像、下载图片等操作时写文件失败。";
            case 78:
                return "[" + errorCode + " NET_DVR_FORMAT_READONLY] 禁止格式化只读硬盘。";
            case 79:
                return "[" + errorCode
                        + " NET_DVR_WITHSAMEUSERNAME] 远程用户配置结构中存在相同的用户名。";
            case 80:
                return "[" + errorCode + " NET_DVR_DEVICETYPE_ERROR] 导入参数时设备型号不匹配。";
            case 81:
                return "[" + errorCode + " NET_DVR_LANGUAGE_ERROR] 导入参数时语言不匹配。";
            case 82:
                return "[" + errorCode
                        + " NET_DVR_PARAVERSION_ERROR] 导入参数时软件版本不匹配。";
            case 83:
                return "[" + errorCode + " NET_DVR_IPCHAN_NOTALIVE] 预览时外接IP通道不在线。";
            case 84:
                return "[" + errorCode
                        + " NET_DVR_RTSP_SDK_ERROR] 加载标准协议通讯库StreamTransClient失败。";
            case 85:
                return "[" + errorCode + " NET_DVR_CONVERT_SDK_ERROR] 加载转封装库失败。";
            case 86:
                return "[" + errorCode
                        + " NET_DVR_IPC_COUNT_OVERFLOW] 超出最大的IP接入通道数。";
            case 87:
                return "[" + errorCode
                        + " NET_DVR_MAX_ADD_NUM] 添加录像标签或者其他操作超出最多支持的个数。";
            case 88:
                return "["
                        + errorCode
                        + " NET_DVR_PARAMMODE_ERROR] 图像增强仪，参数模式错误（用于硬件设置时，客户端进行软件设置时错误值）。";
            case 89:
                return "[" + errorCode + " NET_DVR_CODESPITTER_OFFLINE] 码分器不在线。";
            case 90:
                return "[" + errorCode + " NET_DVR_BACKUP_COPYING] 设备正在备份。";
            case 91:
                return "[" + errorCode + " NET_DVR_CHAN_NOTSUPPORT] 通道不支持该操作。";
            case 92:
                return "[" + errorCode
                        + " NET_DVR_CALLINEINVALID] 高度线位置太集中或长度线不够倾斜。";
            case 93:
                return "[" + errorCode
                        + " NET_DVR_CALCANCELCONFLICT] 取消标定冲突，如果设置了规则及全局的实际大小尺寸过滤。";
            case 94:
                return "[" + errorCode + " NET_DVR_CALPOINTOUTRANGE] 标定点超出范围。";
            case 95:
                return "[" + errorCode + " NET_DVR_FILTERRECTINVALID] 尺寸过滤器不符合要求。";
            case 96:
                return "[" + errorCode + " NET_DVR_DDNS_DEVOFFLINE] 设备没有注册到ddns上。";
            case 97:
                return "[" + errorCode + " NET_DVR_DDNS_INTER_ERROR] DDNS 服务器内部错误。";
            case 99:
                return "[" + errorCode
                        + " NET_DVR_DEC_CHAN_REBIND] 解码通道绑定显示输出次数受限。";
            case 150:
                return "[" + errorCode
                        + " NET_DVR_ALIAS_DUPLICATE] 别名重复（HiDDNS的配置）。";
            case 200:
                return "[" + errorCode + " NET_DVR_NAME_NOT_ONLY] 名称已存在。";
            case 201:
                return "[" + errorCode + " NET_DVR_OVER_MAX_ARRAY] 阵列达到上限。";
            case 202:
                return "[" + errorCode + " NET_DVR_OVER_MAX_VD] 虚拟磁盘达到上限。";
            case 203:
                return "[" + errorCode + " NET_DVR_VD_SLOT_EXCEED] 虚拟磁盘槽位已满。";
            case 204:
                return "[" + errorCode
                        + " NET_DVR_PD_STATUS_INVALID] 重建阵列所需物理磁盘状态错误。";
            case 205:
                return "[" + errorCode
                        + " NET_DVR_PD_BE_DEDICATE_SPARE] 重建阵列所需物理磁盘为指定热备。";
            case 206:
                return "[" + errorCode + " NET_DVR_PD_NOT_FREE] 重建阵列所需物理磁盘非空闲。";
            case 207:
                return "[" + errorCode
                        + " NET_DVR_CANNOT_MIG2NEWMODE] 不能从当前的阵列类型迁移到新的阵列类型。";
            case 208:
                return "[" + errorCode + " NET_DVR_MIG_PAUSE] 迁移操作已暂停。";
            case 209:
                return "[" + errorCode + " NET_DVR_MIG_CANCEL] 正在执行的迁移操作已取消。";
            case 210:
                return "[" + errorCode + " NET_DVR_EXIST_VD] 阵列上存在虚拟磁盘，无法删除阵列。";
            case 211:
                return "[" + errorCode
                        + " NET_DVR_TARGET_IN_LD_FUNCTIONAL] 对象物理磁盘为虚拟磁盘组成部分且工作正常。";
            case 212:
                return "[" + errorCode
                        + " NET_DVR_HD_IS_ASSIGNED_ALREADY] 指定的物理磁盘被分配为虚拟磁盘。";
            case 213:
                return "[" + errorCode
                        + " NET_DVR_INVALID_HD_COUNT] 物理磁盘数量与指定的RAID等级不匹配。";
            case 214:
                return "[" + errorCode + " NET_DVR_LD_IS_FUNCTIONAL] 阵列正常，无法重建。";
            case 215:
                return "[" + errorCode + " NET_DVR_BGA_RUNNING] 存在正在执行的后台任务。";
            case 216:
                return "[" + errorCode + " NET_DVR_LD_NO_ATAPI] 无法用ATAPI盘创建虚拟磁盘。";
            case 217:
                return "[" + errorCode + " NET_DVR_MIGRATION_NOT_NEED] 阵列无需迁移。";
            case 218:
                return "[" + errorCode + " NET_DVR_HD_TYPE_MISMATCH] 物理磁盘不属于同意类型。";
            case 219:
                return "[" + errorCode + " NET_DVR_NO_LD_IN_DG] 无虚拟磁盘，无法进行此项操作。";
            case 220:
                return "[" + errorCode
                        + " NET_DVR_NO_ROOM_FOR_SPARE] 磁盘空间过小，无法被指定为热备盘。";
            case 221:
                return "[" + errorCode
                        + " NET_DVR_SPARE_IS_IN_MULTI_DG] 磁盘已被分配为某阵列热备盘。";
            case 222:
                return "[" + errorCode + " NET_DVR_DG_HAS_MISSING_PD] 阵列缺少盘。";
            case 223:
                return "[" + errorCode + " NET_DVR_NAME_EMPTY] 名称为空。";
            case 224:
                return "[" + errorCode + " NET_DVR_INPUT_PARAM] 输入参数有误。";
            case 225:
                return "[" + errorCode + " NET_DVR_PD_NOT_AVAILABLE] 物理磁盘不可用。";
            case 226:
                return "[" + errorCode + " NET_DVR_ARRAY_NOT_AVAILABLE] 阵列不可用。";
            case 227:
                return "[" + errorCode + " NET_DVR_PD_COUNT] 物理磁盘数不正确。";
            case 228:
                return "[" + errorCode + " NET_DVR_VD_SMALL] 虚拟磁盘太小。";
            case 229:
                return "[" + errorCode + " NET_DVR_NO_EXIST] 不存在。";
            case 230:
                return "[" + errorCode + " NET_DVR_NOT_SUPPORT] 不支持该操作。";
            case 231:
                return "[" + errorCode + " NET_DVR_NOT_FUNCTIONAL] 阵列状态不是正常状态。";
            case 232:
                return "[" + errorCode
                        + " NET_DVR_DEV_NODE_NOT_FOUND] 虚拟磁盘设备节点不存在。";
            case 233:
                return "[" + errorCode + " NET_DVR_SLOT_EXCEED] 槽位达到上限。";
            case 234:
                return "[" + errorCode + " NET_DVR_NO_VD_IN_ARRAY] 阵列上不存在虚拟磁盘。";
            case 235:
                return "[" + errorCode + " NET_DVR_VD_SLOT_INVALID] 虚拟磁盘槽位无效。";
            case 236:
                return "[" + errorCode + " NET_DVR_PD_NO_ENOUGH_SPACE] 所需物理磁盘空间不足。";
            case 237:
                return "[" + errorCode
                        + " NET_DVR_ARRAY_NONFUNCTION] 只有处于正常状态的阵列才能进行迁移。";
            case 238:
                return "[" + errorCode + " NET_DVR_ARRAY_NO_ENOUGH_SPACE] 阵列空间不足。";
            case 239:
                return "[" + errorCode
                        + " NET_DVR_STOPPING_SCANNING_ARRAY] 正在执行安全拔盘或重新扫描。";
            case 240:
                return "[" + errorCode + " NET_DVR_NOT_SUPPORT_16T] 不支持创建大于16T的阵列。";
            case 300:
                return "[" + errorCode + " NET_DVR_ID_ERROR] 配置ID不合理。";
            case 301:
                return "[" + errorCode + " NET_DVR_POLYGON_ERROR] 多边形不符合要求。";
            case 302:
                return "[" + errorCode + " NET_DVR_RULE_PARAM_ERROR] 规则参数不合理。";
            case 303:
                return "[" + errorCode + " NET_DVR_RULE_CFG_CONFLICT] 配置信息冲突。";
            case 304:
                return "[" + errorCode + " NET_DVR_CALIBRATE_NOT_READY] 当前没有标定信息。";
            case 305:
                return "[" + errorCode + " NET_DVR_CAMERA_DATA_ERROR] 摄像机参数不合理。";
            case 306:
                return "[" + errorCode
                        + " NET_DVR_CALIBRATE_DATA_UNFIT] 长度不够倾斜，不利于标定。";
            case 307:
                return "[" + errorCode
                        + " NET_DVR_CALIBRATE_DATA_CONFILICT] 标定出错，以为所有点共线或者位置太集中。";
            case 308:
                return "[" + errorCode
                        + " NET_DVR_CALIBRATE_CALC_FAIL] 摄像机标定参数值计算失败。";
            case 309:
                return "[" + errorCode
                        + " NET_DVR_CALIBRATE_LINE_OUT_RECT] 输入的样本标定线超出了样本外接矩形框。";
            case 310:
                return "[" + errorCode + " NET_DVR_ENTER_RULE_NOT_READY] 没有设置进入区域。";
            case 311:
                return "["
                        + errorCode
                        + " NET_DVR_AID_RULE_NO_INCLUDE_LANE] 交通事件规则中没有包括车道（特值拥堵和逆行）。";
            case 312:
                return "[" + errorCode + " NET_DVR_LANE_NOT_READY] 当前没有设置车道。";
            case 313:
                return "[" + errorCode
                        + " NET_DVR_RULE_INCLUDE_TWO_WAY] 事件规则中包含2种不同方向。";
            case 314:
                return "[" + errorCode
                        + " NET_DVR_LANE_TPS_RULE_CONFLICT] 车道和数据规则冲突。";
            case 315:
                return "[" + errorCode
                        + " NET_DVR_NOT_SUPPORT_EVENT_TYPE] 不支持的事件类型。";
            case 316:
                return "[" + errorCode + " NET_DVR_LANE_NO_WAY] 车道没有方向。";
            case 317:
                return "[" + errorCode + " NET_DVR_SIZE_FILTER_ERROR] 尺寸过滤框不合理。";
            case 318:
                return "[" + errorCode
                        + " NET_DVR_LIB_FFL_NO_FACE] 特征点定位时输入的图像没有人脸。";
            case 319:
                return "[" + errorCode
                        + " NET_DVR_LIB_FFL_IMG_TOO_SMALL] 特征点定位时输入的图像太小。";
            case 320:
                return "[" + errorCode
                        + " NET_DVR_LIB_FD_IMG_NO_FACE] 单张图像人脸检测时输入的图像没有人脸。";
            case 321:
                return "[" + errorCode + " NET_DVR_LIB_FACE_TOO_SMALL] 建模时人脸太小。";
            case 322:
                return "[" + errorCode
                        + " NET_DVR_LIB_FACE_QUALITY_TOO_BAD] 建模时人脸图像质量太差。";
            case 323:
                return "[" + errorCode + " NET_DVR_KEY_PARAM_ERR] 高级参数设置错误。";
            case 324:
                return "[" + errorCode
                        + " NET_DVR_CALIBRATE_DATA_ERR] 标定样本数目错误，或数据值错误，或样本点超出地平线。";
            case 325:
                return "[" + errorCode
                        + " NET_DVR_CALIBRATE_DISABLE_FAIL] 所配置规则不允许取消标定。";
            case 800:
                return "[" + errorCode + " NET_DVR_DEV_NET_OVERFLOW] 网络流量超过设备能力上限。";
            case 801:
                return "["
                        + errorCode
                        + " NET_DVR_STATUS_RECORDFILE_WRITING_NOT_LOCK] 录像文件在录像，无法被锁定。";
            case 802:
                return "[" + errorCode
                        + " NET_DVR_STATUS_CANT_FORMAT_LITTLE_DISK] 由于硬盘太小无法格式化。";
            case 901:
                return "[" + errorCode + " NET_ERR_WINCHAN_IDX] 开窗通道号错误。";
            case 902:
                return "[" + errorCode
                        + " NET_ERR_WIN_LAYER] 窗口层数错误，单个屏幕上最多覆盖的窗口层数。";
            case 903:
                return "[" + errorCode
                        + " NET_ERR_WIN_BLK_NUM] 窗口的块数错误，单个窗口可覆盖的屏幕个数。";
            case 904:
                return "[" + errorCode + " NET_ERR_OUTPUT_RESOLUTION] 输出分辨率错误。";
            case 905:
                return "[" + errorCode + " NET_ERR_LAYOUT] 布局号错误。";
            case 906:
                return "[" + errorCode + " NET_ERR_INPUT_RESOLUTION] 输入分辨率不支持。";
            case 907:
                return "[" + errorCode + " NET_ERR_SUBDEVICE_OFFLINE] 子设备不在线。";
            case 908:
                return "[" + errorCode + " NET_ERR_NO_DECODE_CHAN] 没有空闲解码通道。";
            case 909:
                return "[" + errorCode + " NET_ERR_MAX_WINDOW_ABILITY] 开窗能力上限。";
            case 910:
                return "[" + errorCode + " NET_ERR_ORDER_ERROR] 调用顺序有误。";
            case 911:
                return "[" + errorCode + " NET_ERR_PLAYING_PLAN] 正在执行预案。";
            case 912:
                return "[" + errorCode + " NET_ERR_DECODER_USED] 解码板正在使用。";
            case 401:
                return "[" + errorCode
                        + " NET_DVR_RTSP_ERROR_NOENOUGHPRI] 无权限：服务器返回401时，转成这个错误码。";
            case 402:
                return "[" + errorCode
                        + " NET_DVR_RTSP_ERROR_ALLOC_RESOURCE] 分配资源失败。";
            case 403:
                return "[" + errorCode + " NET_DVR_RTSP_ERROR_PARAMETER] 参数错误。";
            case 404:
                return "["
                        + errorCode
                        + " NET_DVR_RTSP_ERROR_NO_URL] 指定的URL地址不存在：服务器返回404时，转成这个错误码。";
            case 406:
                return "[" + errorCode
                        + " NET_DVR_RTSP_ERROR_FORCE_STOP] 用户中途强行退出。";
            case 407:
                return "[" + errorCode + " NET_DVR_RTSP_GETPORTFAILED] 获取RTSP端口错误。";
            case 410:
                return "[" + errorCode
                        + " NET_DVR_RTSP_DESCRIBERROR] RTSP DECRIBE交互错误。";
            case 411:
                return "[" + errorCode
                        + " NET_DVR_RTSP_DESCRIBESENDTIMEOUT] RTSP DECRIBE发送超时。";
            case 412:
                return "[" + errorCode
                        + " NET_DVR_RTSP_DESCRIBESENDERROR] RTSP DECRIBE发送失败。";
            case 413:
                return "[" + errorCode
                        + " NET_DVR_RTSP_DESCRIBERECVTIMEOUT] RTSP DECRIBE接收超时。";
            case 414:
                return "[" + errorCode
                        + " NET_DVR_RTSP_DESCRIBERECVDATALOST] RTSP DECRIBE接收数据错误。";
            case 415:
                return "[" + errorCode
                        + " NET_DVR_RTSP_DESCRIBERECVERROR] RTSP DECRIBE接收失败。";
            case 416:
                return "["
                        + errorCode
                        + " NET_DVR_RTSP_DESCRIBESERVERERR] RTSP DECRIBE服务器返回401,501等错误。";
            case 420:
                return "[" + errorCode
                        + " NET_DVR_RTSP_SETUPERROR] RTSP SETUP交互错误。";
            case 421:
                return "[" + errorCode
                        + " NET_DVR_RTSP_SETUPSENDTIMEOUT] RTSP SETUP发送超时。";
            case 422:
                return "[" + errorCode
                        + " NET_DVR_RTSP_SETUPSENDERROR] RTSP SETUP发送错误。";
            case 423:
                return "[" + errorCode
                        + " NET_DVR_RTSP_SETUPRECVTIMEOUT] RTSP SETUP接收超时。";
            case 424:
                return "[" + errorCode
                        + " NET_DVR_RTSP_SETUPRECVDATALOST] RTSP SETUP接收数据错误。";
            case 425:
                return "[" + errorCode
                        + " NET_DVR_RTSP_SETUPRECVERROR] RTSP SETUP接收失败。";
            case 426:
                return "[" + errorCode + " NET_DVR_RTSP_OVER_MAX_CHAN] 设备超过最大连接数。";
            case 430:
                return "[" + errorCode + " NET_DVR_RTSP_PLAYERROR] RTSP PLAY交互错误。";
            case 431:
                return "[" + errorCode
                        + " NET_DVR_RTSP_PLAYSENDTIMEOUT] RTSP PLAY发送超时。";
            case 432:
                return "[" + errorCode
                        + " NET_DVR_RTSP_PLAYSENDERROR] RTSP PLAY发送错误。";
            case 433:
                return "[" + errorCode
                        + " NET_DVR_RTSP_PLAYRECVTIMEOUT] RTSP PLAT接收超时。";
            case 434:
                return "[" + errorCode
                        + " NET_DVR_RTSP_PLAYRECVDATALOST] RTSP PLAY接收数据错误。";
            case 435:
                return "[" + errorCode
                        + " NET_DVR_RTSP_PLAYRECVERROR] RTSP PLAY接收失败。";
            case 436:
                return "[" + errorCode
                        + " NET_DVR_RTSP_PLAYSERVERERR] RTSP PLAY设备返回错误状态。";
            case 440:
                return "[" + errorCode
                        + " NET_DVR_RTSP_TEARDOWNERROR] RTSP TEARDOWN交互错误。";
            case 441:
                return "[" + errorCode
                        + " NET_DVR_RTSP_TEARDOWNSENDTIMEOUT] RTSP TEARDOWN发送超时。";
            case 442:
                return "[" + errorCode
                        + " NET_DVR_RTSP_TEARDOWNSENDERROR] RTSP TEARDOWN发送错误。";
            case 443:
                return "[" + errorCode
                        + " NET_DVR_RTSP_TEARDOWNRECVTIMEOUT] RTSP TEARDOWN接收超时。";
            case 444:
                return "["
                        + errorCode
                        + " NET_DVR_RTSP_TEARDOWNRECVDATALOST] RTSP TEARDOWN接收数据错误。";
            case 445:
                return "[" + errorCode
                        + " NET_DVR_RTSP_TEARDOWNRECVERROR] RTSP TEARDOWN接收失败。";
            case 446:
                return "[" + errorCode
                        + " NET_DVR_RTSP_TEARDOWNSERVERERR] RTSP TEARDOWN设备返回错误状态。";
            case 500:
                return "[" + errorCode + " NET_PLAYM4_NOERROR] 没有错误。";
            case 501:
                return "[" + errorCode + " NET_PLAYM4_PARA_OVER] 输入参数非法。";
            case 502:
                return "[" + errorCode + " NET_PLAYM4_ORDER_ERROR] 调用顺序不对。";
            case 503:
                return "[" + errorCode + " NET_PLAYM4_TIMER_ERROR] 多媒体时钟设置失败。";
            case 504:
                return "[" + errorCode + " NET_PLAYM4_DEC_VIDEO_ERROR] 视频解码失败。";
            case 505:
                return "[" + errorCode + " NET_PLAYM4_DEC_AUDIO_ERROR] 音频解码失败。";
            case 506:
                return "[" + errorCode + " NET_PLAYM4_ALLOC_MEMORY_ERROR] 分配内存失败。";
            case 507:
                return "[" + errorCode + " NET_PLAYM4_OPEN_FILE_ERROR] 文件操作失败。";
            case 508:
                return "[" + errorCode + " NET_PLAYM4_CREATE_OBJ_ERROR] 创建线程事件等失败。";
            case 509:
                return "[" + errorCode
                        + " NET_PLAYM4_CREATE_DDRAW_ERROR] 创建directDraw失败。";
            case 510:
                return "[" + errorCode
                        + " NET_PLAYM4_CREATE_OFFSCREEN_ERROR] 创建后端缓存失败。";
            case 511:
                return "[" + errorCode + " NET_PLAYM4_BUF_OVER] 缓冲区满，输入流失败。";
            case 512:
                return "[" + errorCode
                        + " NET_PLAYM4_CREATE_SOUND_ERROR] 创建音频设备失败。";
            case 513:
                return "[" + errorCode + " NET_PLAYM4_SET_VOLUME_ERROR] 设置音量失败。";
            case 514:
                return "[" + errorCode
                        + " NET_PLAYM4_SUPPORT_FILE_ONLY] 只能在播放文件时才能使用此接口。";
            case 515:
                return "[" + errorCode
                        + " NET_PLAYM4_SUPPORT_STREAM_ONLY] 只能在播放流时才能使用此接口。";
            case 516:
                return "[" + errorCode
                        + " NET_PLAYM4_SYS_NOT_SUPPORT] 系统不支持，解码器只能工作在Pentium";
            case 517:
                return "[" + errorCode + " NET_PLAYM4_FILEHEADER_UNKNOWN] 没有文件头。";
            case 518:
                return "[" + errorCode
                        + " NET_PLAYM4_VERSION_INCORRECT] 解码器和编码器版本不对应。";
            case 519:
                return "[" + errorCode
                        + " NET_PALYM4_INIT_DECODER_ERROR] 初始化解码器失败。";
            case 520:
                return "[" + errorCode
                        + " NET_PLAYM4_CHECK_FILE_ERROR] 文件太短或码流无法识别。";
            case 521:
                return "[" + errorCode
                        + " NET_PLAYM4_INIT_TIMER_ERROR] 初始化多媒体时钟失败。";
            case 522:
                return "[" + errorCode + " NET_PLAYM4_BLT_ERROR] 位拷贝失败。";
            case 523:
                return "[" + errorCode + " NET_PLAYM4_UPDATE_ERROR] 显示overlay失败。";
            case 524:
                return "[" + errorCode
                        + " NET_PLAYM4_OPEN_FILE_ERROR_MULTI] 打开混合流文件失败。";
            case 525:
                return "[" + errorCode
                        + " NET_PLAYM4_OPEN_FILE_ERROR_VIDEO] 打开视频流文件失败。";
            case 526:
                return "[" + errorCode
                        + " NET_PLAYM4_JPEG_COMPRESS_ERROR] JPEG压缩错误。";
            case 527:
                return "[" + errorCode
                        + " NET_PLAYM4_EXTRACT_NOT_SUPPORT] 不支持该文件版本。";
            case 528:
                return "[" + errorCode
                        + " NET_PLAYM4_EXTRACT_DATA_ERROR] 提取文件数据失败。";
            case 678:
                return "["
                        + errorCode
                        + " NET_QOS_ERR_SCHEDPARAMS_BAD_MINIMUM_INTERVAL] 预设的最小间隔错误。";
            case 679:
                return "[" + errorCode
                        + " NET_QOS_ERR_SCHEDPARAMS_BAD_FRACTION] 预设分数错误。";
            case 680:
                return "[" + errorCode
                        + " NET_QOS_ERR_SCHEDPARAMS_INVALID_BANDWIDTH] 预设的带宽值无效。";
            case 687:
                return "[" + errorCode + " NET_QOS_ERR_PACKET_TOO_BIG] 数据包太大。";
            case 688:
                return "[" + errorCode + " NET_QOS_ERR_PACKET_LENGTH] 数据包长度错误。";
            case 689:
                return "[" + errorCode + " NET_QOS_ERR_PACKET_VERSION] 数据包版本错误。";
            case 690:
                return "[" + errorCode + " NET_QOS_ERR_PACKET_UNKNOW] 未知数据包。";
            case 695:
                return "[" + errorCode + " NET_QOS_ERR_OUTOFMEM] 内存不足。";
            case 696:
                return "[" + errorCode
                        + " NET_QOS_ERR_LIB_NOT_INITIALIZED] Lib库没有初始化。";
            case 697:
                return "[" + errorCode + " NET_QOS_ERR_SESSION_NOT_FOUND] 没有找到会话。";
            case 698:
                return "[" + errorCode + " NET_QOS_ERR_INVALID_ARGUMENTS] 参数无效。";
            case 699:
                return "[" + errorCode + " NET_QOS_ERROR] Qos 错误。";
            case 700:
                return "[" + errorCode + " NET_QOS_OK] 没有错误。";
            default:
                return "[" + errorCode + " NET_???_??] 未知错误。";
        }
    }

}
