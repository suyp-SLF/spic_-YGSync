package kd.cus.api;

import java.util.Date;

/**
 * 数对实体类（当前暂存日志的唯一参数以及时间）
 * @author suyp
 *
 */

public class LogEntity {
	private Object logPkid;
	private Date nowDate;
	
	public LogEntity(Object logPkid, Date nowDate) {
		this.logPkid = logPkid;
		this.nowDate = nowDate;
	}
	
	public Object getLogPkid() {
		return logPkid;
	}
	public Date getNowDate() {
		return nowDate;
	}
}
