package onight.osgi.otransio.netty;

import io.netty.util.concurrent.EventExecutorGroup;
import lombok.AllArgsConstructor;
import onight.osgi.otransio.impl.NodeInfo;
import onight.tfw.async.CompleteHandler;
import onight.tfw.otransio.api.beans.FramePacket;
import onight.tfw.otransio.api.session.LocalModuleSession;
import onight.tfw.otransio.api.session.PSession;
import org.fc.zippo.dispatcher.IActorDispatcher;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@AllArgsConstructor
public class LocalModuleManager extends PSessionManager<NodeInfo> {
    private NSessionSets nss;
    private IActorDispatcher dispatcher;
    private EventExecutorGroup eeg;

    @Override
    protected PSession createSession(String key, NodeInfo node) {
        return new LocalSessionWrapper(dispatcher, eeg, key);
    }

    private static class LocalSessionWrapper extends LocalModuleSession{
        private IActorDispatcher dispatcher;
        private EventExecutorGroup eeg;

        public LocalSessionWrapper(IActorDispatcher dispatcher, EventExecutorGroup eeg, String module) {
            super(module);
            this.dispatcher = dispatcher;
            this.eeg = eeg;
        }

        @Override
        public void onPacket(FramePacket pack, CompleteHandler handler) {
            final Runnable runner = ()->super.onPacket(pack, handler);
//            eeg.submit(runner);
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
    }
}
