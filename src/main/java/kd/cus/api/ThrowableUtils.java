package kd.cus.api;

import java.io.PrintWriter;
import java.io.StringWriter;

/**
 * 用于获得完整的错误报告
 * @author suyp
 *
 */

public class ThrowableUtils {
	
	//将报错的方法的所有报错转成string
	public static String getStackTrace(Throwable throwable){
		if (null != throwable) {
			StringWriter stringWriter=new StringWriter();
			PrintWriter printWriter=new PrintWriter(stringWriter);
			try {
				throwable.printStackTrace(printWriter);
				return throwable.getMessage() + stringWriter.toString();
			}finally {
				printWriter.close();
			}
		} else {
			return "";
		}
	}
}
