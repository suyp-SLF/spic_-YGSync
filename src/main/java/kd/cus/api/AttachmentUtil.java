package kd.cus.api;

import kd.bos.cache.CacheFactory;
import kd.bos.cache.TempFileCache;
import kd.bos.context.RequestContext;
import kd.bos.dataentity.entity.DynamicObject;
import kd.bos.entity.MainEntityType;
import kd.bos.fileservice.FileItem;
import kd.bos.fileservice.FileService;
import kd.bos.fileservice.FileServiceFactory;
import kd.bos.form.control.AttachmentPanel;
import kd.bos.servicehelper.AttachmentDto;
import kd.bos.servicehelper.AttachmentServiceHelper;
import kd.bos.servicehelper.MetadataServiceHelper;
import kd.bos.session.EncreptSessionUtils;
import kd.bos.util.FileNameUtils;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.sql.Timestamp;
import java.util.List;
import java.util.Map;


/**
 * @Description 附件操作工具类
 * @Author : XZR
 * @CreatedDate : 2020/10/14 19:38
 */
public class AttachmentUtil {

	/**
	 * 附件拷贝
	 * 
	 * @param targetBillNo
	 *            目标单标识
	 * @param targetPkid
	 *            目标单pkid
	 * @param attachKey
	 *            目标单附件面板标识
	 * @param attachments
	 *            源附件信息集合
	 * @throws IOException 
	 */
	public static void copyAttachments(String targetBillNo, Object targetPkid, String attachKey,
			List<Map<String, Object>> attachments, InputStream inputStream) throws IOException {

		for (Map<String, Object> attachItem : attachments) {
			// 获取临时文件缓存 ***
			TempFileCache cache = CacheFactory.getCommonCacheFactory().getTempFileCache();
//			int tem;
//			while ((tem = inputStream.read()) != -1) {
//				System.out.print((char) tem);
//			}
			// 将文件流存入临时文件缓存（拷贝完成）（最后一个参数为缓存有效期，7200秒）***
			String tempUrl = cache.saveAsUrl((String) attachItem.get("name"), inputStream, 7200);
			// 获取文件的缓存路径
			tempUrl = EncreptSessionUtils.encryptSession(tempUrl);
			// 获取域名前缀
//			String address = RequestContext.get().getClientFullContextPath();
//			if (!address.endsWith("/")) {
//				address = address + "/";
//			}
		    // 拼接url路径:=当前环境域名前缀+缓存路径 
			//String tempUrl3 = address + tempUrl;	
			String tempUrl3 =  tempUrl;		
			String name = (String) attachItem.get("name");
			// 获取appId
			MainEntityType dataEntityType = MetadataServiceHelper.getDataEntityType(targetBillNo);
			String appId = dataEntityType.getAppId();
			// 将文件缓存中的附件文件上传到正式文件服务器
			String  path = AttachmentServiceHelper.saveTempToFileService(tempUrl3, appId, targetBillNo, targetPkid,
					name);
			// 将新文件的物理路径存入map
			attachItem.put("url", path);
		}
		// 维护单据和附件的关系（非文件上传）	
		AttachmentServiceHelper.upload(targetBillNo, targetPkid, attachKey, attachments);
	}

}
