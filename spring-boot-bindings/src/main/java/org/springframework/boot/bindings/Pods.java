package org.springframework.boot.bindings;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.lang.reflect.Field;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;

import org.springframework.util.ReflectionUtils;

import io.kubernetes.client.PortForward;
import io.kubernetes.client.PortForward.PortForwardResult;
import io.kubernetes.client.openapi.ApiClient;
import io.kubernetes.client.openapi.ApiException;
import io.kubernetes.client.openapi.apis.CoreV1Api;
import io.kubernetes.client.openapi.models.V1LabelSelector;
import io.kubernetes.client.openapi.models.V1Pod;
import io.kubernetes.client.openapi.models.V1Service;
import io.kubernetes.client.util.WebSocketStreamHandler;
import io.kubernetes.client.util.labels.LabelSelector;

public class Pods {

	private final CoreV1Api api;

	private Collection<ServerSocket> servers = new ArrayList<>();

	public Pods(CoreV1Api api) {
		this.api = api;
	}

	public V1Pod byName(String namespace, String name) throws ApiException {
		List<V1Pod> items = api.listNamespacedPod(namespace, null, null, null, null, null, null, null, null,
				null, null).getItems();
		return items.isEmpty() ? null : items.get(0);
	}

	public List<V1Pod> forService(String namespace, String name) throws ApiException {
		V1Service service = api.readNamespacedService(name, namespace, null);
		V1LabelSelector v1LabelSelector = new V1LabelSelector().matchLabels(service.getSpec().getSelector());
		LabelSelector labelSelector = LabelSelector.parse(v1LabelSelector);
		return api.listNamespacedPod(namespace, null, null, null, null, labelSelector.toString(), null, null, null,
				null, null).getItems();
	}

	public RemoteService portForward(V1Pod pod, int targetPort) throws ApiException, IOException {

		ApiClient client = api.getApiClient();
		PortForward forward = new PortForward(client);
		List<Integer> ports = Arrays.asList(targetPort);

		final ServerSocket server = new ServerSocket(0);
		servers.add(server);
		int localPort = server.getLocalPort();

		new Thread(() ->

		{

			boolean running = true;
			while (running) {

				try (final Socket socket = server.accept()) {
					final PortForward.PortForwardResult result = forward.forward(pod.getMetadata().getNamespace(),
							pod.getMetadata().getName(), ports);

					new Thread(
							() -> {
								try {
									Pods.copy(result.getInputStream(targetPort), socket.getOutputStream());
								} catch (Exception ex) {
									ex.printStackTrace();
								}
							}, pod.getMetadata().getName() + " (" + targetPort + ":" + localPort + ") - input ")
							.start();

					try {
						Pods.copy(socket.getInputStream(), result.getOutboundStream(targetPort));
					} catch (Exception ex) {
						ex.printStackTrace();
					}

					close(socket, result, targetPort);
				} catch (Exception e) {
					running = false;
				}

			}

		}, "localhost:" + pod.getMetadata().getName() + ":" + localPort).start();

		return new RemoteService(server, localPort);
	}

	private void close(Socket socket, final PortForward.PortForwardResult result, int target) {
		try {
			result.getInputStream(target).close();
			Field field = ReflectionUtils.findField(PortForwardResult.class, "handler");
			field.setAccessible(true);
			WebSocketStreamHandler handler = (WebSocketStreamHandler) field.get(result);
			handler.close();
			socket.close();
		} catch (Exception e) {
			throw new IllegalStateException(e);
		}
	}

	private static final int BUFFER_SIZE = 4096;

	private static void copy(InputStream in, OutputStream out) throws IOException {
		byte[] buffer = new byte[BUFFER_SIZE];
		int bytesRead;
		while ((bytesRead = in.read(buffer)) != -1) {
			out.write(buffer, 0, bytesRead);
		}
		out.flush();
	}

}
