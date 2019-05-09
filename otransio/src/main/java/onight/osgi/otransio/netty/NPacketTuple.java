package onight.osgi.otransio.netty;

import lombok.Data;
import onight.tfw.async.CompleteHandler;
import onight.tfw.otransio.api.beans.FramePacket;

@Data
public class NPacketTuple {
    private FramePacket packet;
    private CompleteHandler handler;
    private int rewriteTimes = 0;
    private boolean writed = false;
    private boolean responsed = false;

    public boolean compareAndIncRewriteTImes(int max){
        if(rewriteTimes>=max){
            return false;
        }
        else {
            rewriteTimes++;
            return true;
        }
    }

    public NPacketTuple(FramePacket packet, CompleteHandler handler){
        this.packet = packet;
        this.handler = handler;
    }
    public NPacketTuple(FramePacket packet){
        this(packet, null);
    }
}
