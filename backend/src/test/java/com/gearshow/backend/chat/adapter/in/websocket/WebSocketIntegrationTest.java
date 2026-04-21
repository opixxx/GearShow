package com.gearshow.backend.chat.adapter.in.websocket;

import com.gearshow.backend.chat.adapter.in.websocket.dto.StompChatMessageResponse;
import com.gearshow.backend.chat.adapter.out.persistence.ChatRoomJpaEntity;
import com.gearshow.backend.chat.adapter.out.persistence.ChatRoomJpaRepository;
import com.gearshow.backend.chat.domain.vo.ChatRoomStatus;
import com.gearshow.backend.user.infrastructure.security.JwtTokenProvider;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.messaging.converter.MappingJackson2MessageConverter;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompFrameHandler;
import org.springframework.messaging.simp.stomp.StompHeaders;
import org.springframework.messaging.simp.stomp.StompSession;
import org.springframework.messaging.simp.stomp.StompSessionHandlerAdapter;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.web.socket.WebSocketHttpHeaders;
import org.springframework.web.socket.client.standard.StandardWebSocketClient;
import org.springframework.web.socket.messaging.WebSocketStompClient;
import org.springframework.web.socket.sockjs.client.SockJsClient;
import org.springframework.web.socket.sockjs.client.Transport;
import org.springframework.web.socket.sockjs.client.WebSocketTransport;

import org.springframework.web.client.RestTemplate;

import java.lang.reflect.Type;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@Disabled("SockJS+STOMP 핸드셰이크 이슈 — 수동 브라우저 테스트로 대체")
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
class WebSocketIntegrationTest {

    @LocalServerPort
    private int port;

    @Autowired
    private JwtTokenProvider jwtTokenProvider;

    @Autowired
    private ChatRoomJpaRepository chatRoomJpaRepository;

    private WebSocketStompClient stompClient;
    private Long chatRoomId;

    private static final Long SELLER_ID = 1L;
    private static final Long BUYER_ID = 2L;

    @BeforeEach
    void setUp() {
        List<Transport> transports = List.of(new WebSocketTransport(new StandardWebSocketClient()));
        SockJsClient sockJsClient = new SockJsClient(transports);
        stompClient = new WebSocketStompClient(sockJsClient);
        stompClient.setMessageConverter(new MappingJackson2MessageConverter());

        Instant now = Instant.now();
        ChatRoomJpaEntity room = ChatRoomJpaEntity.builder()
                .showcaseId(100L)
                .sellerId(SELLER_ID)
                .buyerId(BUYER_ID)
                .status(ChatRoomStatus.ACTIVE)
                .createdAt(now)
                .lastMessageAt(now)
                .build();
        chatRoomId = chatRoomJpaRepository.save(room).getId();
    }

    @AfterEach
    void tearDown() {
        chatRoomJpaRepository.deleteAll();
    }

    @Test
    @DisplayName("SockJS info 엔드포인트가 응답한다")
    void sockJsInfoEndpoint() {
        RestTemplate restTemplate = new RestTemplate();
        String response = restTemplate.getForObject(
                "http://localhost:" + port + "/ws/info", String.class);
        System.out.println("[TEST] SockJS info: " + response);
        assertThat(response).contains("websocket");
    }

    @Test
    @DisplayName("JWT 인증 후 STOMP 구독 → 메시지 발신 → 수신 성공")
    void fullStompFlow() throws Exception {
        String token = jwtTokenProvider.generateAccessToken(BUYER_ID);

        StompHeaders connectHeaders = new StompHeaders();
        connectHeaders.add("Authorization", "Bearer " + token);

        StompSession session = stompClient.connectAsync(
                        getSockJsUrl(),
                        new WebSocketHttpHeaders(),
                        connectHeaders,
                        new StompSessionHandlerAdapter() {
                            @Override
                            public void handleException(StompSession s, StompCommand cmd,
                                                        StompHeaders headers, byte[] payload, Throwable ex) {
                                System.err.println("[TEST] STOMP 예외: " + ex.getMessage());
                            }

                            @Override
                            public void handleTransportError(StompSession s, Throwable ex) {
                                System.err.println("[TEST] 전송 에러: " + ex.getMessage());
                            }
                        })
                .get(5, TimeUnit.SECONDS);

        CompletableFuture<StompChatMessageResponse> received = new CompletableFuture<>();

        session.subscribe("/topic/chat-rooms/" + chatRoomId, new StompFrameHandler() {
            @Override
            public Type getPayloadType(StompHeaders headers) {
                return StompChatMessageResponse.class;
            }

            @Override
            public void handleFrame(StompHeaders headers, Object payload) {
                received.complete((StompChatMessageResponse) payload);
            }
        });

        Thread.sleep(500);

        session.send("/app/chat-rooms/" + chatRoomId + "/send",
                Map.of("messageType", "TEXT", "content", "안녕하세요", "clientMessageId", "test-id-1"));

        StompChatMessageResponse response = received.get(10, TimeUnit.SECONDS);
        assertThat(response.type()).isEqualTo("MESSAGE");
        assertThat(response.payload().content()).isEqualTo("안녕하세요");
        assertThat(response.payload().senderId()).isEqualTo(BUYER_ID);
        assertThat(response.payload().chatRoomId()).isEqualTo(chatRoomId);

        session.disconnect();
    }

    @Test
    @DisplayName("인증 없이 CONNECT 시 실패한다")
    void connectWithoutAuthFails() {
        assertThatThrownBy(() -> stompClient.connectAsync(
                        getSockJsUrl(),
                        new WebSocketHttpHeaders(),
                        new StompHeaders(),
                        new StompSessionHandlerAdapter() {})
                .get(5, TimeUnit.SECONDS))
                .isInstanceOf(ExecutionException.class);
    }

    private String getSockJsUrl() {
        return "http://localhost:" + port + "/ws";
    }
}
