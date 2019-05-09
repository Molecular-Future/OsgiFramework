package onight.osgi.otransio.sm;

import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import onight.osgi.otransio.impl.NodeInfo;
import onight.tfw.otransio.api.beans.FrameBody;

@Data
@EqualsAndHashCode(callSuper=false)
@NoArgsConstructor
public class RemoteModuleBean extends FrameBody {

	NodeInfo nodeInfo = new NodeInfo();
	// 模块列表

}
