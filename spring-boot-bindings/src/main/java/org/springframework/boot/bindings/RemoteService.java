package org.springframework.boot.bindings;

import java.io.Closeable;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.lang.reflect.Field;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketException;
import java.util.Arrays;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import org.springframework.util.ReflectionUtils;

import io.kubernetes.client.PortForward;
import io.kubernetes.client.PortForward.PortForwardResult;
import io.kubernetes.client.openapi.models.V1Pod;
import io.kubernetes.client.util.WebSocketStreamHandler;

public class RemoteService implements Closeable {

	private ServerSocket server;
	private int localPort;
	private AtomicBoolean running = new AtomicBoolean();
	private AtomicReference<Current> current = new AtomicReference<>();
	private final PortForward forward;
	private final V1Pod pod;
	private final int targetPort;

	public RemoteService(PortForward forward, V1Pod pod, int targetPort) {
		this.forward = forward;
		this.pod = pod;
		this.targetPort = targetPort;
	}

	public int getLocalPort() {
		return localPort;
	}

	@Override
	public void close() throws IOException {
		closeCurrent();
		running.set(false);
		localPort = 0;
		if (server == null) {
			return;
		}
		try {
			server.close();
		} catch (Exception ex) {
			throw new IllegalStateException(ex);
		} finally {
		}
	}

	@Override
	public String toString() {
		return "RemoteService[pod: " + pod.getMetadata().getName() + ", localPort: " + localPort + ", targetPort: "
				+ targetPort + "]";
	}

	public RemoteService start() {

		if (running.get()) {
			return this;
		}

		try {
			server = new ServerSocket(0);
		} catch (IOException e) {
			throw new IllegalStateException(e);
		}
		localPort = server.getLocalPort();

		new Thread(() -> {

			running.set(true);
			while (running.get()) {

				try (final Socket socket = server.accept()) {
					final PortForward.PortForwardResult result = forward.forward(pod.getMetadata().getNamespace(),
							pod.getMetadata().getName(), Arrays.asList(targetPort));
					current.set(new Current(result, socket));

					Thread input = new Thread(
							() -> {
								try {
									RemoteService.copy(result.getInputStream(targetPort), socket.getOutputStream());
								} catch (Exception ex) {
									if (!socket.isClosed()) {
										ex.printStackTrace();
									}
								}
							}, pod.getMetadata().getName() + " (" + targetPort + ":" + localPort + ") - input ");
					input.start();

					try {
						RemoteService.copy(socket.getInputStream(), result.getOutboundStream(targetPort));
					} catch (Exception ex) {
						if (!socket.isClosed()) {
							ex.printStackTrace();
						}
					}
					input.join();

					closeCurrent();
				} catch (Exception e) {
					running.set(false);
				}

			}

		}, "localhost:" + pod.getMetadata().getName() + ":" + localPort).start();

		return this;
	}

	private void closeCurrent() {
		if (!running.get() || current.get() == null) {
			return;
		}
		try {
			PortForwardResult result = current.get().result();
			result.getInputStream(targetPort).close();
			Field field = ReflectionUtils.findField(PortForwardResult.class, "handler");
			field.setAccessible(true);
			WebSocketStreamHandler handler = (WebSocketStreamHandler) field.get(result);
			handler.close();
			if (!current.get().socket().isClosed()) {
				current.get().socket().close();
			}
		} catch (Exception e) {
			throw new IllegalStateException(e);
		}
		current.set(null);
	}

	private static void copy(InputStream in, OutputStream out) throws IOException {
		byte[] buffer = new byte[4096];
		int bytesRead;
		while ((bytesRead = in.read(buffer)) != -1) {
			out.write(buffer, 0, bytesRead);
		}
		out.flush();
	}

	private record Current(PortForwardResult result, Socket socket) {
	}

}
