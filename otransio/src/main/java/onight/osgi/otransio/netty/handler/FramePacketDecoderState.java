package onight.osgi.otransio.netty.handler;

/**
 * FramePacket解析状态
 */
public enum FramePacketDecoderState {
    /**
     * 解析头（初始状态）
     */
    READ_HEADER,
    /**
     * 解析扩展头
     */
    READ_EXT_HEADER,
    /**
     * 解析消息体
     */
    READ_CONTENT
}
