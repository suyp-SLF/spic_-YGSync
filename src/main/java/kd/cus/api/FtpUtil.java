package kd.cus.api;

import org.apache.commons.net.ftp.FTPClient;
import org.apache.commons.net.ftp.FTPFile;
import org.apache.commons.net.ftp.FTPReply;

import java.io.*;
import java.net.SocketException;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;

/**
 * @Description 去司库服务器下载FTP文件
 * @Author : XZR
 * @CreatedDate : 2020/10/22 9:49
 */
public class FtpUtil {
	private static String localCharset = "GBK";
	private final static String START_STR = "开始传输";

	/**
	 * 从FTP服务器下载文件
	 *
	 * @param ftpHost
	 *            FTP IP地址
	 * 
	 * @param ftpUserName
	 *            FTP 用户名
	 * 
	 * @param ftpPassword
	 *            FTP用户名密码
	 * 
	 * @param ftpPort
	 *            FTP端口
	 * 
	 * @param ftpPath
	 *            FTP服务器中文件所在路径 格式： ftptest/aa
	 * 
	 * @param localPath
	 *            下载到本地的位置 格式：H:/download
	 * 
	 * @param fileName
	 *            文件名称InputStream
	 * @throws IOException
	 * @throws SocketException
	 */
	public static Map<String, Object> downloadFtpFile(String ftpHost, String ftpUserName, String ftpPassword,
			int ftpPort, String ftpPath, String fileName) throws SocketException, IOException {
		// 后台打印日志
		Date startDate = LogUtils.log(null, "FTPElecreceipt", START_STR,
				ftpHost + "端口:" + ftpPort + "用户名:" + ftpUserName + "密码:" + ftpPassword + "路径:" + ftpPath, "", null,
				null);

		// org.apache.commons.net.ftp.FTPClient ftpClient;
		LogUtils.log(false, "Elecreceipt_ftpClient1", START_STR, "司库", "没报异常创建方法", startDate, null);

		Map<String, Object> returnMap = new HashMap<String, Object>();

		InputStream inputStream = null;
		try {
			org.apache.commons.net.ftp.FTPClient ftpClient = new org.apache.commons.net.ftp.FTPClient();
			LogUtils.log(false, "Elecreceipt_ftpClient2", START_STR, "司库", "没报异常创建方法", startDate, null);
			ftpClient.setConnectTimeout(20 * 1000);// 设置连接FTP时间
			LogUtils.log(false, "Elecreceipt_ftpClient2", START_STR, "司库", "没报异常创建方法", startDate, null);
			ftpClient.connect(ftpHost, ftpPort);// 连接FTP服务器
			LogUtils.log(false, "Elecreceipt_connect", START_STR, "司库", "没报连接方法", startDate, null);
			ftpClient.login(ftpUserName, ftpPassword);// 登陆FTP服务器
			LogUtils.log(false, "Elecreceipt_login", START_STR, "司库", "没报异常", startDate, null);

			ftpClient.setControlEncoding("UTF-8"); // 中文支持

			ftpClient.setFileType(FTPClient.BINARY_FILE_TYPE);
			ftpClient.enterLocalActiveMode();//设置主动模式
			ftpClient.setBufferSize(1024 * 1024 * 1024);
			ftpClient.setDefaultTimeout(20 * 1000);
			ftpClient.setDataTimeout(20 * 1000);
			// ftpClient.enterLocalPassiveMode();//设置成被动模式

			// 判断FTP服务器编码，获得编码
			if (FTPReply.isPositiveCompletion(ftpClient.sendCommand("OPTS UTF8", "ON"))) {
				localCharset = "UTF-8";
			}
			// 路径ftpPath 把本地gbk转成服务器要的ISO-8859-1格式
			ftpPath = new String(ftpPath.getBytes(localCharset), "ISO-8859-1");

			ftpClient.changeWorkingDirectory(ftpPath);// 改变路径
			LogUtils.log(false, "Elecreceipt_ftpPath", START_STR, "司库", ftpPath, startDate, null);
			// 获得文件尺寸大小
			FTPFile[] files = ftpClient.listFiles();
			LogUtils.log(false, "Elecreceipt_files", START_STR, "司库", ftpPath, startDate, null);
			long fileSize = files[0].getSize();
			if (fileSize >= 1024) {
				fileSize = (fileSize / (long) 1024.0);
			}
			returnMap.put("fileSize", fileSize);
			LogUtils.log(false, "Elecreceipt_fileSize", START_STR, "司库", Long.toString(fileSize)+"大小", startDate, null);
			// 文件名fileName 把本地gbk转成服务器要的ISO-8859-1格式
			fileName = new String(fileName.getBytes(localCharset), "ISO-8859-1");
			inputStream = ftpClient.retrieveFileStream(fileName);
			
			LogUtils.log(false, "Elecreceipt_retrieveFileStream", START_STR, "司库", Integer.toString(inputStream.available())+"大小", startDate, null);
			returnMap.put("inputStream", inputStream);
			

			ftpClient.logout();// 退出
			LogUtils.log(false, "Elecreceipt_chenggong", START_STR, "司库", "没报异常", startDate, null);

		} catch (FileNotFoundException e) {
			LogUtils.log(false, "Elecreceipt", START_STR, "司库", "没有找到" + ftpPath + "文件", startDate, e);

		} catch (SocketException e) {
			LogUtils.log(false, "Elecreceipt", START_STR, "连接FTP失败", "司库", startDate, e);

		} catch (IOException e) {
			LogUtils.log(false, "Elecreceipt", START_STR, "文件读取错误", "司库", startDate, e);

		} catch (Exception e) {
			LogUtils.log(false, "Elecreceipt", START_STR, "其他异常", "司库", startDate, e);

		}

		return returnMap;
	
	}

}