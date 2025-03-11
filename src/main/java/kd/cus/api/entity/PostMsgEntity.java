package kd.cus.api.entity;

import kd.bos.dataentity.entity.DynamicObject;

/**
 * 发送消息类
 */
public class PostMsgEntity {
    private String title;//标题
    private String senderName;//发送方名称
    private String tag;//标签
    private String entityNumber;//单据标识名称
    private String[] pkids;//日志发送的单据pkid
    private String msg;//发送的信息
    private DynamicObject[] users;//日志的接收人

//    public PostMsgEntity(String msg, DynamicObject[] users){
//        this.msg = msg;
//        this.users = users;
//    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getSenderName() {
        return senderName;
    }

    public void setSenderName(String senderName) {
        this.senderName = senderName;
    }

    public String getTag() {
        return tag;
    }

    public void setTag(String tag) {
        this.tag = tag;
    }

    public String getEntityNumber() {
        return entityNumber;
    }

    public void setEntityNumber(String entityNumber) {
        this.entityNumber = entityNumber;
    }

    public String[] getPkids() {
        return pkids;
    }

    public void setPkids(String[] pkids) {
        this.pkids = pkids;
    }

    public String getMsg() {
        return msg;
    }

    public void setMsg(String msg) {
        this.msg = msg;
    }

    public DynamicObject[] getUsers() {
        return users;
    }

    public void setUsers(DynamicObject[] users) {
        this.users = users;
    }
}
