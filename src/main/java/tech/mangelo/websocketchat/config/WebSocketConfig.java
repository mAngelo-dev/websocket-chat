package tech.mangelo.websocketchat.config;


import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;
import tech.mangelo.websocketchat.handler.ChatHandler;

@Configuration
@EnableWebSocket
public class WebSocketConfig implements WebSocketConfigurer {
    @Override
    public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
        registry.addHandler(new ChatHandler(), "/chat")
                .setAllowedOrigins("*");
//        Usar AllowedOrigins com esse parâmetro é somente algo que fiz pra ver se está funcionando sem dar aqueles erros de CORS chatos, isso em produção não pode acontecer - @Miguel
    }
}
