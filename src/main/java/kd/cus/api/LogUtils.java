package kd.cus.api;

import java.text.SimpleDateFormat;
import java.util.Date;

import kd.bos.logging.Log;
import kd.bos.logging.LogFactory;

/**
 * log日志工具
 * @author suyp
 *
 */
public class LogUtils {
	
	
	/**
	 * 在服务器log日志进行记录
	 * @param isSuccess
	 * @param code
	 * @param processName
	 * @param requestData
	 * @param responseData
	 * @param startDate
	 * @param endDate
	 * @return 
	 */
	public static Date log(Boolean isSuccess ,String code, String processName, String requestData, String responseData, Date startDate, Throwable e) {
		Log logger = LogFactory.getLog(LogUtils.class);
		Date nowDate = new Date();
		SimpleDateFormat sdf = new SimpleDateFormat();// 格式化时间 
        sdf.applyPattern("yyyy-MM-dd HH:mm:ss a");// a为am/pm的标记  
		String logModel = ""
				+ "===========================当前正在进行:"+ processName + "===========================" + "\r\n"
				+ "是否成功 ：\t" + isSuccess+ "\t\r\n"
				+ "接口参数 ：\t"	+ code +"\t\r\n"
				+ "当前时间 ：\t" + sdf.format(nowDate) + "\t\r\n" 
				+ (null == startDate?"":("开始时间：\t" + sdf.format(startDate) + "\t\r\n"
						+ "结束时间：\t" + sdf.format(nowDate) + "\t\r\n"
						+ "所用时间：\t" + getOverTime(startDate, nowDate) + "\t\r\n"))
				+ "报文数据 ：\t" + requestData + "\t\r\n"
				+ "响应数据 ：\t" + responseData + "\t\r\n"
				+ "错误 ：\t" + "\r\n" + ThrowableUtils.getStackTrace(e) + "\t\r\n"
				+ "===========================当前正在进行:"+ processName + "===========================" + "\r\n";
		logger.info(logModel);
		return nowDate;
	}
	/**
	 * 
	 * @param startDate
	 * @param endDate
	 * @return
	 */
	public static String getOverTime(Date startDate, Date endDate) {
		long overTime = endDate.getTime() - startDate.getTime();
		return overTime + "ms";
	}
}
