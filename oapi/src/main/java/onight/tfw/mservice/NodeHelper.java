package onight.tfw.mservice;

import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.UnknownHostException;

import onight.tfw.outils.conf.PropHelper;

public class NodeHelper {

	private static PropHelper prop = new PropHelper(null);

	public static PropHelper getPropInstance() {
		return prop;
	}

	public static String getHostName() {
		try {
			return InetAddress.getLocalHost().getHostName();
		} catch (UnknownHostException e) {
		}
		return "localhost";
	}

	public static String envInEnv(String envid) {
		if (envid.startsWith("${") && envid.length() > 3) {
			envid = System.getProperty(envid.substring(2, envid.length() - 1), envid);
		} else if (envid.startsWith("$") && envid.length() > 2) {
			envid = System.getProperty(envid.substring(1), envid);
		}
		return envid;
	}

	public static String getCurrNodeName() {
		String def = getPropInstance().get("otrans.node.name",
				getCurrNodeListenOutAddr() + "." + getCurrNodeListenOutPort());
		String envid = System.getProperty("otrans.node.name", def);

		return envInEnv(envid);
	}

	// public static String getCurrNodeID() {
	// return String.valueOf(getCurrNodeIdx());
	// }
	public static String getCurrNodeID() {
		String def = getPropInstance().get("otrans.node.id",
				getCurrNodeListenOutAddr() + "." + getCurrNodeListenOutPort());
		String envid = System.getProperty("otrans.node.id", def);
		return envInEnv(envid);
	}

	public static String getCurrNodeListenInAddr() {
		String def = getPropInstance().get("otrans.addr.in", "0.0.0.0");
		String envid = System.getProperty("otrans.addr.in", def);
		return envInEnv(envid);
	}

	static String _outAddr = null;

	public static String getCurrNodeListenOutAddr() {
		if (_outAddr != null) {
			return _outAddr;
		}
		String def;
		try {
			def = getPropInstance().get("otrans.addr.out", InetAddress.getLocalHost().getHostAddress());
			String envid = System.getProperty("otrans.addr.out", def);
			_outAddr = envInEnv(envid);
			return _outAddr;
		} catch (UnknownHostException e) {
			e.printStackTrace();
		}
		_outAddr = "127.0.0.1";
		return _outAddr;
	}

	static int listeninport = -1;

	public synchronized static int getCurrNodeListenInPort() {
		if (listeninport > 0)
			return listeninport;
		String def = getPropInstance().get("otrans.port.in", "[5100,5200]");
		String envid = System.getProperty("otrans.port.in", def);

		try {
			return Integer.parseInt(envInEnv(envid));
		} catch (NumberFormatException e) {
			String range[] = def.split("\\[|,|\\]");
			if (range.length == 3)
				try {
					for (int pf = Integer.parseInt(range[1]); pf <= Integer.parseInt(range[2]); pf++) {
						try (ServerSocket serverSocket = new ServerSocket(pf);) {
							listeninport = pf;
							return listeninport;
						} catch (Exception ee) {
						}
					}
				} catch (Exception ee) {
				}

		}
		listeninport = 5100;
		return listeninport;

	}

	public static void main(String[] args) {
		String range[] = "[5100,5200]".split("\\[|,|\\]");
		try {
			System.out.println(Integer.parseInt("[5100,5200]"));
		} catch (NumberFormatException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		System.out.println("from:" + Integer.parseInt(range[1].trim()) + ",to==>" + Integer.parseInt(range[2].trim()));
		try {
			System.out.println(InetAddress.getLocalHost().getHostAddress());
		} catch (UnknownHostException e) {
			e.printStackTrace();
		}
	}

	static int _outPort = -1;

	public static int getCurrNodeListenOutPort() {
		if (_outPort > 0)
		{
			return _outPort;
		}
		String def = getPropInstance().get("otrans.port.out", null);
		String envid = System.getProperty("otrans.port.out", def);
		if (envid == null) {
			_outPort = getCurrNodeListenInPort();
			return _outPort;
		}
		try {
			_outPort = Integer.parseInt(envInEnv(envid));
			return _outPort;
		} catch (NumberFormatException e) {
			return 5100;
		}
	}
}
