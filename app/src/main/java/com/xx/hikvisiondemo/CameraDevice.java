package com.xx.hikvisiondemo;

/**
 * 设备实体类
 */
public class CameraDevice {

    /**
     * IP地址
     */
    private String ip;
    /**
     * 端口
     */
    private String port;
    /**
     * 用户名
     */
    private String userName;
    /**
     * 密码
     */
    private String passWord;
    /**
     * 通道号
     */
    private String channel;

    public String getIP() {
        return ip;
    }

    public void setIP(String ip) {
        this.ip = ip;
    }

    public String getPort() {
        return port;
    }

    public void setPort(String port) {
        this.port = port;
    }

    public String getUserName() {
        return userName;
    }

    public void setUserName(String userName) {
        this.userName = userName;
    }

    public String getPassWord() {
        return passWord;
    }

    public void setPassWord(String passWord) {
        this.passWord = passWord;
    }

    public String getChannel() {
        return channel;
    }

    public void setChannel(String channel) {
        this.channel = channel;
    }

    public CameraDevice() {}
    public CameraDevice(String ip, String port, String userName, String passWord, String channel)
    {
        this.ip=ip;
        this.port=port;
        this.userName=userName;
        this.passWord=passWord;
        this.channel=channel;
    }

    @Override
    public String toString() {
        return "[IP=" + ip + "; PORT=" + port + "; USERNAME=" + userName + "; PASSWORD=" + passWord + "; CHANNEL=" + channel + ";]";
    }

}
