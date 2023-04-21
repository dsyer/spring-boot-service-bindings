package org.springframework.boot.bindings;

import java.io.Closeable;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.lang.reflect.Field;
import java.net.ServerSocket;
import java.net.Socket;
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
	private final int targetPort;
	private final String namespace;
	private final String name;

	public RemoteService(PortForward forward, V1Pod pod, int targetPort) {
		this.forward = forward;
		this.namespace = pod.getMetadata().getNamespace();
		this.name = pod.getMetadata().getName();
		this.targetPort = targetPort;
	}

	public int getLocalPort() {
		return localPort;
	}

	@Override
	public void close() {
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
		return "RemoteService[pod: " + name + ", localPort: " + localPort + ", targetPort: "
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

				try (Socket socket = server.accept()) {
					PortForward.PortForwardResult result = forward.forward(namespace,
							name, Arrays.asList(targetPort));
					if (result == null) {
						throw new RuntimeException("PortForward failed!");
					}
					InputStream in = result.getInputStream(targetPort);
					OutputStream out = result.getOutboundStream(targetPort);
					current.set(new Current(result, socket));

					Thread output = new Thread(
							() -> {
								try {
									RemoteService.copy(socket.getInputStream(), out);
								} catch (Exception ex) {
									if (!socket.isClosed()) {
										ex.printStackTrace();
									}
								}
							}, name + " (" + targetPort + ":" + localPort + ") - output ");
					output.start();

					Thread input = new Thread(
							() -> {
								try {
									RemoteService.copy(in, socket.getOutputStream());
								} catch (Exception ex) {
									if (!socket.isClosed()) {
										ex.printStackTrace();
									}
								}
							}, name + " (" + targetPort + ":" + localPort + ") - input ");
					input.start();

					output.join();
					in.close();
					input.join();

					closeCurrent();

				} catch (Exception e) {
					running.set(false);
				}

			}

		}, "localhost:" + name + ":" + localPort).start();

		return this;
	}

	private void closeCurrent() {
		if (!running.get() || current.get() == null) {
			return;
		}
		try {
			if (!current.get().socket().isClosed()) {
				current.get().socket().close();
			}
		} catch (Exception e) {
		}
		try {
			InputStream in = current.get().result().getInputStream(targetPort);
			in.close();
		} catch (Exception e) {
		}
		try {
			OutputStream out = current.get().result().getOutboundStream(targetPort);
			out.close();
		} catch (Exception e) {
		}
		try {
			Field field = ReflectionUtils.findField(PortForwardResult.class, "handler");
			field.setAccessible(true);
			WebSocketStreamHandler handler = (WebSocketStreamHandler) field.get(current.get().result());
			handler.close();
		} catch (Exception e) {
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
