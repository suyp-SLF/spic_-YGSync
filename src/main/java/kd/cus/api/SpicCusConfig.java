package kd.cus.api;

import com.google.gson.Gson;

/**
 * @author Wu Yanqi
 */
public class SpicCusConfig {
    private String ftpHost;
    private String ftpUserName;
    private String ftpPassword;
    private String testUrl;
    private String sikuUrl;
    private String pzTestUrl;
    private String ftpPort;
    private String pzTokenUrl;
    private String ncUrl;
    private String jobNumber;

    static { }

    public static SpicCusConfig getSpicCusConfig(){
        Gson gson = new Gson();
        SpicCusConfig _this = gson.fromJson(System.getProperty("spic_testEnv"), SpicCusConfig.class);
        return _this;
    }
    
    public String getJobNumber() {
        return jobNumber;
    }
    
    public String getNcUrl() {
        return ncUrl;
    }
    
    public String getFtpHost() {
        return ftpHost;
    }

    public String getFtpUserName() {
        return ftpUserName;
    }

    public String getFtpPassword() {
        return ftpPassword;
    }

    public String getTestUrl() {
        return testUrl;
    }

    public String getSikuUrl() {
        return sikuUrl;
    }

    public String getPzTestUrl() {
        return pzTestUrl;
    }

    public String getFtpPort() {
        return ftpPort;
    }

    public String getPzTokenUrl() {
        return pzTokenUrl;
    }
}
