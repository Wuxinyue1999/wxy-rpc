package com.wxy.rpc.core.protocol;

import com.wxy.rpc.core.constant.ProtocolConstant;
import com.wxy.rpc.core.enums.MessageType;
import com.wxy.rpc.core.enums.SerializationType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 请求协议头部信息
 * <pre>
 *   ------------------------------------------------------------------
 *  | 魔数 (2byte) | 版本号 (1byte) | 序列化算法 (1byte) | 消息类型 (1byte) |
 *  -------------------------------------------------------------------
 *  |   消息序列号 (4byte)   |   对齐填充符 (1byte)   |   消息长度 (4byte)  |
 *  -------------------------------------------------------------------
 * </pre>
 *
 * @author Wuxy
 * @version 1.0.0
 * @Date 2023/1/4
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MessageHeader {

    /**
     * 2字节 魔数
     */
    private short magicNum;

    /**
     * 1字节 版本号
     */
    private byte version;

    /**
     * 1字节 序列化算法
     */
    private byte serializerType;

    /**
     * 1字节 消息类型
     */
    private byte messageType;

    /**
     * 4字节 消息的序列号 ID
     */
    private int sequenceId;

    /**
     * 1字节 对齐填充符号，无意义
     */
    private byte padding;

    /**
     * 4字节 数据内容长度
     */
    private int length;

    /**
     * 根据输入的序列化算法构造一个 MessageHeader 对象
     *
     * @param serializeName 序列化算法名称
     * @return 构造指定序列化算法的默认协议头对象
     */
    public static MessageHeader build(String serializeName) {
        return MessageHeader.builder()
                .magicNum(ProtocolConstant.MAGIC_NUM)
                .version(ProtocolConstant.VERSION)
                .serializerType(SerializationType.parseByName(serializeName).getType())
                .messageType(MessageType.REQUEST.getType())
                .sequenceId(ProtocolConstant.getSequenceId()) // 添加唯一 ID 生成
                .build();
    }
}
