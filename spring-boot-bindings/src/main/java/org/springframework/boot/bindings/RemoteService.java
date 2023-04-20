package org.springframework.boot.bindings;

import java.io.Closeable;
import java.io.IOException;
import java.net.ServerSocket;

public class RemoteService implements Closeable {

	private final ServerSocket server;
	private final int localPort;

	public RemoteService(ServerSocket server, int localPort) {
		this.server = server;
		this.localPort = localPort;
	}

	public int getLocalPort() {
		return localPort;
	}

	@Override
	public void close() throws IOException {
			try {
				server.close();
			} catch (Exception ex) {
				ex.printStackTrace();
			} finally {
			}
	}

	@Override
	public String toString() {
		return "RemoteService[localPort: " + localPort + "]";
	}

}
