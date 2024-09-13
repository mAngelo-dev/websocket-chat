package tech.mangelo.websocketchat.handler;

import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import java.util.Map;
import java.util.Queue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;

//A conexão não é de fato aleatoria e sim por ordem de chegada já que estamos puxando sempre as primeiras duas sessões da fila.

public class ChatHandler extends TextWebSocketHandler {

    private final Queue<WebSocketSession> usersQueue = new ConcurrentLinkedQueue<>();
    private final Map<WebSocketSession, WebSocketSession> pairedUsers = new ConcurrentHashMap<>();
    private Map<WebSocketSession, WebSocketSession> previousConnection = new ConcurrentHashMap<>();

    private void reconnectUsers(WebSocketSession userSessionOne, WebSocketSession userSessionTwo) throws Exception {
        pairedUsers.put(userSessionOne, userSessionTwo);
        pairedUsers.put(userSessionTwo, userSessionOne);

        userSessionOne.sendMessage(new TextMessage("Reconnected with your previous chat partner!"));
        userSessionTwo.sendMessage(new TextMessage("Your previous chat partner has reconnected!"));
    }

    @Override
    public void afterConnectionEstablished(WebSocketSession session) throws Exception {
        WebSocketSession previousPartner = getPreviousPartner(session);
        if (previousPartner != null && previousPartner.isOpen()) {
            reconnectUsers(session, previousPartner);
//            Remove sessões já conectadas devido a reconexão das duas
            previousConnection.remove(session);
            previousConnection.remove(previousPartner);
        } else {
            usersQueue.add(session);
            matchUsers();
        }
    }

    private WebSocketSession getPreviousPartner(WebSocketSession session) {
        for (Map.Entry<WebSocketSession, WebSocketSession> entry : previousConnection.entrySet()) {
            if (entry.getKey().equals(session)) {
                return entry.getValue();
            } else if (entry.getValue().equals(session)) {
                return entry.getKey();
            }
        }
        return null;
    }

    private void matchUsers() throws Exception {
        if (usersQueue.size() >= 2) {
//            Poll é um metodo para regatar o primeiro da fila
            WebSocketSession userSessionOne = usersQueue.poll();
            WebSocketSession userSessionTwo = usersQueue.poll();
            if (userSessionOne != null && userSessionTwo != null) {
                pairedUsers.put(userSessionOne, userSessionTwo);
                pairedUsers.put(userSessionTwo, userSessionOne);
                userSessionOne.sendMessage(new TextMessage("Connected with a random user."));
                userSessionTwo.sendMessage(new TextMessage("Connected with a random user."));
            }
        }
    }

    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) throws Exception {
        WebSocketSession pairedSession = pairedUsers.get(session);
        if (pairedSession != null && pairedSession.isOpen()) {
            pairedSession.sendMessage(new TextMessage(message.getPayload()));
        } else {
//            Aqui a sessão é encerrada corretamente.
            session.sendMessage(new TextMessage("Your partner has disconnected, closing sessions."));
            session.close(CloseStatus.NORMAL);
        }
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) throws Exception {
        WebSocketSession pairedSession = pairedUsers.remove(session);
        if (pairedSession != null) {
            pairedUsers.remove(pairedSession);
//            Aqui salva o pair de sessão antiga, para caso um dos dois saiam, eles serão reconectados novamente.
//            Por algum motivo quando tento a reconexão essa função não restaura a sessão, talvez porque os dados da requisição mudem?
            previousConnection.put(session, pairedSession);
            if (pairedSession.isOpen()) {
                pairedSession.sendMessage(new TextMessage("The person you were talking to has disconnected."));
                session.close(CloseStatus.NORMAL);
            }
        }
        usersQueue.remove(session);
    }
}
