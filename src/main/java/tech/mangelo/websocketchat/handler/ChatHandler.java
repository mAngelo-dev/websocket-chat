package tech.mangelo.websocketchat.handler;

import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import java.util.Map;
import java.util.Queue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;

/**
 * Handler para gerenciar conexões WebSocket e chats entre usuários.
 * A conexão é feita por ordem de chegada, puxando sempre as duas primeiras sessões da fila.
 *
 * @author @Miguel
 * @author @Claude
 */
public class ChatHandler extends TextWebSocketHandler {

    private final Queue<WebSocketSession> usersQueue = new ConcurrentLinkedQueue<>();
    private final Map<WebSocketSession, WebSocketSession> pairedUsers = new ConcurrentHashMap<>();
    private final Map<WebSocketSession, WebSocketSession> previousConnection = new ConcurrentHashMap<>();

    /**
     * Reconecta dois usuários que se desconectaram e estavam em uma conversa.
     *
     * @param userSessionOne A primeira sessão de usuário.
     * @param userSessionTwo A segunda sessão de usuário.
     * @throws Exception Se ocorrer um erro ao enviar as mensagens.
     *
     * @author @Miguel
     */
    private void reconnectUsers(WebSocketSession userSessionOne, WebSocketSession userSessionTwo) throws Exception {
        pairedUsers.put(userSessionOne, userSessionTwo);
        pairedUsers.put(userSessionTwo, userSessionOne);

        userSessionOne.sendMessage(new TextMessage("Reconnected with your previous chat partner!"));
        userSessionTwo.sendMessage(new TextMessage("Your previous chat partner has reconnected!"));
    }

    /**
     * Após a conexão ser estabelecida, tenta reconectar com um parceiro anterior ou adiciona à fila para pareamento.
     *
     * @param session A sessão do WebSocket que foi estabelecida.
     * @throws Exception Se ocorrer um erro ao enviar as mensagens ou ao parear usuários.
     *
     * @author @Miguel
     */
    @Override
    public void afterConnectionEstablished(WebSocketSession session) throws Exception {
        WebSocketSession previousPartner = getPreviousPartner(session);
        if (previousPartner != null && previousPartner.isOpen()) {
            reconnectUsers(session, previousPartner);
            // Limpa a conexão anterior após a reconexão bem-sucedida
            previousConnection.remove(session);
            previousConnection.remove(previousPartner);
        } else {
            usersQueue.add(session);
            matchUsers();
        }
    }

    /**
     * Obtém o parceiro anterior de um usuário, se existir.
     *
     * @param session A sessão do WebSocket para a qual procuramos o parceiro anterior.
     * @return O parceiro anterior ou null se não existir.
     *
     * @author @Miguel
     */
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

    /**
     * Faz o pareamento de dois usuários da fila.
     *
     * @throws Exception Se ocorrer um erro ao enviar as mensagens.
     *
     * @author @Miguel
     */
    private void matchUsers() throws Exception {
        if (usersQueue.size() >= 2) {
            // Poll é um método para resgatar o primeiro da fila
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

    /**
     * Manipula a mensagem de texto recebida de um usuário e a encaminha para o usuário pareado.
     *
     * @param session A sessão do WebSocket do usuário que enviou a mensagem.
     * @param message A mensagem de texto recebida.
     * @throws Exception Se ocorrer um erro ao enviar a mensagem.
     *
     * @author @Miguel
     */
    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) throws Exception {
        WebSocketSession pairedSession = pairedUsers.get(session);
        if (pairedSession != null && pairedSession.isOpen()) {
            pairedSession.sendMessage(new TextMessage(message.getPayload()));
        } else {
            session.sendMessage(new TextMessage("The person you were talking to has disconnected."));
        }
    }

    /**
     * Após a conexão ser fechada, remove o usuário da fila e do pareamento e tenta reestabelecer a conexão com o parceiro.
     *
     * @param session A sessão do WebSocket que foi fechada.
     * @param status O status de fechamento da conexão.
     * @throws Exception Se ocorrer um erro ao enviar a mensagem.
     *
     * @author @Miguel
     */
    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) throws Exception {
        WebSocketSession pairedSession = pairedUsers.remove(session);
        if (pairedSession != null) {
            pairedUsers.remove(pairedSession);
            previousConnection.put(session, pairedSession);
            if (pairedSession.isOpen()) {
                pairedSession.sendMessage(new TextMessage("The person you were talking to has disconnected."));
                // Fechar a sessão pareada se necessário
                pairedSession.close(CloseStatus.NORMAL);
            }
        }
        usersQueue.remove(session);
        // Fechar a sessão atual
        session.close(CloseStatus.NORMAL);
    }
}
