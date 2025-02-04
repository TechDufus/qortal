package org.qortal.api.websocket;

import java.io.IOException;
import java.io.StringWriter;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import org.eclipse.jetty.websocket.api.Session;
import org.eclipse.jetty.websocket.api.WebSocketException;
import org.eclipse.jetty.websocket.api.annotations.OnWebSocketClose;
import org.eclipse.jetty.websocket.api.annotations.OnWebSocketConnect;
import org.eclipse.jetty.websocket.api.annotations.OnWebSocketError;
import org.eclipse.jetty.websocket.api.annotations.OnWebSocketMessage;
import org.eclipse.jetty.websocket.api.annotations.WebSocket;
import org.eclipse.jetty.websocket.servlet.WebSocketServletFactory;
import org.qortal.controller.ChatNotifier;
import org.qortal.data.chat.ChatMessage;
import org.qortal.data.transaction.ChatTransactionData;
import org.qortal.repository.DataException;
import org.qortal.repository.Repository;
import org.qortal.repository.RepositoryManager;

@WebSocket
@SuppressWarnings("serial")
public class ChatMessagesWebSocket extends ApiWebSocket {

	@Override
	public void configure(WebSocketServletFactory factory) {
		factory.register(ChatMessagesWebSocket.class);
	}

	@OnWebSocketConnect
	@Override
	public void onWebSocketConnect(Session session) {
		Map<String, List<String>> queryParams = session.getUpgradeRequest().getParameterMap();

		List<String> txGroupIds = queryParams.get("txGroupId");
		if (txGroupIds != null && txGroupIds.size() == 1) {
			int txGroupId = Integer.parseInt(txGroupIds.get(0));

			try (final Repository repository = RepositoryManager.getRepository()) {
				List<ChatMessage> chatMessages = repository.getChatRepository().getMessagesMatchingCriteria(
						null,
						null,
						txGroupId,
						null,
						null,
						null, null, null);

				sendMessages(session, chatMessages);
			} catch (DataException e) {
				// Not a good start
				session.close(4001, "Couldn't fetch initial messages from repository");
				return;
			}

			ChatNotifier.Listener listener = chatTransactionData -> onNotify(session, chatTransactionData, txGroupId);
			ChatNotifier.getInstance().register(session, listener);

			return;
		}

		List<String> involvingAddresses = queryParams.get("involving");
		if (involvingAddresses == null || involvingAddresses.size() != 2) {
			session.close(4001, "invalid criteria");
			return;
		}

		try (final Repository repository = RepositoryManager.getRepository()) {
			List<ChatMessage> chatMessages = repository.getChatRepository().getMessagesMatchingCriteria(
					null,
					null,
					null,
					null,
					involvingAddresses,
					null, null, null);

			sendMessages(session, chatMessages);
		} catch (DataException e) {
			// Not a good start
			session.close(4001, "Couldn't fetch initial messages from repository");
			return;
		}

		ChatNotifier.Listener listener = chatTransactionData -> onNotify(session, chatTransactionData, involvingAddresses);
		ChatNotifier.getInstance().register(session, listener);
	}

	@OnWebSocketClose
	@Override
	public void onWebSocketClose(Session session, int statusCode, String reason) {
		ChatNotifier.getInstance().deregister(session);
	}

	@OnWebSocketError
	public void onWebSocketError(Session session, Throwable throwable) {
		/* ignored */
	}

	@OnWebSocketMessage
	public void onWebSocketMessage(Session session, String message) {
		/* ignored */
	}

	private void onNotify(Session session, ChatTransactionData chatTransactionData, int txGroupId) {
		if (chatTransactionData == null)
			// There has been a group-membership change, but we're not interested
			return;

		// We only want group-based messages with our txGroupId
		if (chatTransactionData.getRecipient() != null || chatTransactionData.getTxGroupId() != txGroupId)
			return;

		sendChat(session, chatTransactionData);
	}

	private void onNotify(Session session, ChatTransactionData chatTransactionData, List<String> involvingAddresses) {
		if (chatTransactionData == null)
			return;

		// We only want direct/non-group messages where sender/recipient match our addresses
		String recipient = chatTransactionData.getRecipient();
		if (recipient == null)
			return;

		List<String> transactionAddresses = Arrays.asList(recipient, chatTransactionData.getSender());

		if (!transactionAddresses.containsAll(involvingAddresses))
			return;

		sendChat(session, chatTransactionData);
	}

	private void sendMessages(Session session, List<ChatMessage> chatMessages) {
		StringWriter stringWriter = new StringWriter();

		try {
			marshall(stringWriter, chatMessages);

			session.getRemote().sendStringByFuture(stringWriter.toString());
		} catch (IOException | WebSocketException e) {
			// No output this time?
		}
	}

	private void sendChat(Session session, ChatTransactionData chatTransactionData) {
		// Convert ChatTransactionData to ChatMessage
		ChatMessage chatMessage;
		try (final Repository repository = RepositoryManager.getRepository()) {
			chatMessage = repository.getChatRepository().toChatMessage(chatTransactionData);
		} catch (DataException e) {
			// No output this time?
			return;
		}

		sendMessages(session, Collections.singletonList(chatMessage));
	}

}
