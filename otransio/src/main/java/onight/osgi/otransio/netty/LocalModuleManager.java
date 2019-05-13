package onight.osgi.otransio.netty;

import io.netty.util.concurrent.EventExecutorGroup;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import onight.osgi.otransio.impl.NodeInfo;
import onight.tfw.async.CompleteHandler;
import onight.tfw.otransio.api.beans.FramePacket;
import onight.tfw.otransio.api.session.LocalModuleSession;
import onight.tfw.otransio.api.session.PSession;
import org.apache.commons.lang3.StringUtils;
import org.fc.zippo.dispatcher.IActorDispatcher;

@Slf4j
@AllArgsConstructor
public class LocalModuleManager extends PSessionManager<NodeInfo> {
    private NSessionSets nss;
    private IActorDispatcher dispatcher;
    private EventExecutorGroup eeg;

    @Override
    protected PSession createSession(String key, NodeInfo node) {
        return new LocalSessionWrapper(dispatcher, eeg, this, key);
    }

    public void setDispatcher(IActorDispatcher dispatcher){
        this.dispatcher = dispatcher;
    }

    private static class LocalSessionWrapper extends LocalModuleSession{
        private IActorDispatcher dispatcher;
        private EventExecutorGroup eeg;
        private LocalModuleManager lmm;

        public LocalSessionWrapper(IActorDispatcher dispatcher, EventExecutorGroup eeg, LocalModuleManager lmm, String module) {
            super(module);
            this.dispatcher = dispatcher;
            this.eeg = eeg;
            this.lmm = lmm;
        }

        @Override
        public void onPacket(FramePacket pack, CompleteHandler handler) {

            final Runnable runner = handler==null?
                    ()-> super.onPacket(pack, NSessionSets.NIL_COMPLETE_HANDLER)
                    :()->super.onPacket(pack, handler);
//            eeg.submit(runner);
            if(StringUtils.equalsIgnoreCase(pack.getModule(),module)) {
                log.debug("local module[{}] on packet: gcmd:{}, cmds:{}", module, pack.getModuleAndCMD(), serviceByCMD);
                if (pack.isSync()) {
                    if (pack.getFixHead().getPrio() == '8' || pack.getFixHead().getPrio() == '9') {
                        dispatcher.executeNow(pack, runner);
                    } else {
                        dispatcher.postWithTimeout(pack, runner, 60 * 1000, handler);
                    }
                } else {
                    if (pack.getFixHead().getPrio() == '8' || pack.getFixHead().getPrio() == '9') {
                        dispatcher.executeNow(pack, runner);
                    } else {
                        dispatcher.post(pack, runner);
                    }
                }
            }
            else{
                LocalModuleSession lms = (LocalModuleSession)lmm.get(pack.getModule());
                if(lms!=null){
                    lms.onPacket(pack, handler);
                }
            }
        }
    }
}
