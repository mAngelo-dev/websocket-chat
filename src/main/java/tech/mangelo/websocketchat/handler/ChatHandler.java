package tech.mangelo.websocketchat.handler;

import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import java.util.Map;
import java.util.Queue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;

// Essa classe utiliza de TextWebSocketHandler pois esse handler é um handler mais apropriado para processamentos de textos. - @Miguel
public class ChatHandler extends TextWebSocketHandler {

    private final Queue<WebSocketSession> usersQueue = new ConcurrentLinkedQueue<>();
    private final Map<WebSocketSession, WebSocketSession> pairedUsers = new ConcurrentHashMap<>();

    @Override
    public void afterConnectionEstablished(WebSocketSession session) throws Exception {
        usersQueue.add(session);
        matchUsers();
    }

    private void matchUsers() throws Exception {
        if (usersQueue.size() > 1) {
            WebSocketSession userSessionOne = usersQueue.poll();
            WebSocketSession userSessionTwo = usersQueue.poll();
            if (userSessionOne != null && userSessionTwo != null) {
                userSessionOne.sendMessage(new TextMessage("Connection paired with someone."));
                userSessionTwo.sendMessage(new TextMessage("Connection paired with someone."));
            }
        }
    }

    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) throws Exception {
        WebSocketSession pairedSession = pairedUsers.get(session);
        if (pairedSession != null && pairedSession.isOpen()) {
            pairedSession.sendMessage(new TextMessage(message.getPayload()));
        } else {
            session.sendMessage(new TextMessage("The person you was talking has disconnected."));
        }

    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) throws Exception {
        WebSocketSession pairedSession = pairedUsers.remove(session);
        if (pairedSession != null) {
            pairedUsers.remove(pairedSession);
            if (pairedSession.isOpen()){
                session.sendMessage(new TextMessage("The person you were talking has disconnected."));
            }
        }
        usersQueue.remove(session);
    }


}