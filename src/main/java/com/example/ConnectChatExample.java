package com.example;

import software.amazon.awssdk.auth.credentials.ProfileCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.connect.ConnectClient;
import software.amazon.awssdk.services.connect.model.ChatMessage;
import software.amazon.awssdk.services.connect.model.StartChatContactRequest;
import software.amazon.awssdk.services.connect.model.StartChatContactResponse;
import software.amazon.awssdk.services.connectparticipant.ConnectParticipantClient;
import software.amazon.awssdk.services.connectparticipant.model.ConnectionType;
import software.amazon.awssdk.services.connectparticipant.model.CreateParticipantConnectionRequest;
import software.amazon.awssdk.services.connectparticipant.model.CreateParticipantConnectionResponse;
import software.amazon.awssdk.services.connectparticipant.model.DisconnectParticipantRequest;
import software.amazon.awssdk.services.connectparticipant.model.SendMessageRequest;
import software.amazon.awssdk.services.connectparticipant.model.SendMessageResponse;
import software.amazon.awssdk.services.connectparticipant.model.SendEventRequest;
import software.amazon.awssdk.services.connectparticipant.model.SendEventResponse;

// 添加JSON解析库
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.JsonNode;

import java.net.URI;
import java.net.http.WebSocket;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Consumer;

import java.util.concurrent.CompletionStage;
import java.util.Arrays;

/**
 * 使用AWS SDK for Java调用Amazon Connect的StartChatContact API发起聊天
 */
public class ConnectChatExample {

    public static void main(String[] args) {
        // 替换为你的实际配置
        String instanceId = "b7e4b4ed-1bdf-4b14-b624-d9328f08725a"; // Amazon Connect实例ID
        String contactFlowId = "4fd58c6f-bfa4-40cc-8bb7-e82adab81d3a"; // 联系流ID Sample Inbound Flow Test
        String participantName = "Customer"; // 参与者名称
        Region region = Region.US_EAST_1; // 替换为您的AWS区域

        try {
            // 步骤1: 创建聊天会话
            String participantToken = startChatSession(instanceId, contactFlowId, region, participantName);
            if (participantToken == null) {
                System.err.println("无法获取participantToken，退出程序");
                return;
            }

            // 步骤2: 使用participantToken创建参与者连接
            CreateParticipantConnectionResponse connectionResponse = createParticipantConnection(participantToken,
                    region);

            String connectionToken = connectionResponse.connectionCredentials().connectionToken();
            if (connectionToken == null) {
                System.err.println("无法获取connectionToken，退出程序");
                return;
            }

            String webSocketUrl = connectionResponse.websocket().url();
            if (webSocketUrl == null) {
                System.err.println("无法获取websocketUrl，退出程序");
                return;
            }

            // 步骤3: 创建WebSocket连接以获取实时更新
            // 从participantConnection响应中获取WebSocket URL
            // 获取WebSocket URL
            System.out.println("获取到WebSocket URL: " + webSocketUrl);

            // 创建WebSocket连接
            WebSocket webSocket = createWebSocket(
                    webSocketUrl,
                    // 处理接收到的消息
                    (message) -> {
                        System.out.println("\n收到新的WebSocket消息: ");
                        System.out.println("--------------------");
                        System.out.println(message);
                        System.out.println("--------------------");

                        // 解析消息
                        try {
                            ObjectMapper mapper = new ObjectMapper();
                            JsonNode jsonMessage = mapper.readTree(message);

                            // 检查消息类型
                            if (jsonMessage.has("content")) {
                                String contentValue = jsonMessage.get("content").asText();
                                jsonMessage = mapper.readTree(contentValue);
                                if (jsonMessage.has("ContentType")) {
                                    String contentType = jsonMessage.get("ContentType").asText();
                                    String participantRole = jsonMessage.has("ParticipantRole")
                                            ? jsonMessage.get("ParticipantRole").asText()
                                            : "";
                                    String displayName = jsonMessage.has("DisplayName")
                                            ? jsonMessage.get("DisplayName").asText()
                                            : "";

                                    System.out.println("消息类型: " + contentType);

                                    switch (contentType) {
                                        case "application/vnd.amazonaws.connect.event.typing":
                                            System.out.println(displayName + " (" + participantRole + ") 正在输入...");
                                            break;
                                        case "application/vnd.amazonaws.connect.event.participant.joined":
                                            System.out.println(displayName + " (" + participantRole + ") 已加入聊天");
                                            break;
                                        case "application/vnd.amazonaws.connect.event.participant.left":
                                            System.out.println(displayName + " (" + participantRole + ") 已离开聊天");
                                            break;
                                        case "application/vnd.amazonaws.connect.event.chat.ended":
                                            System.out.println("聊天已结束");
                                            break;
                                        case "text/plain":
                                            if (jsonMessage.has("Content")) {
                                                String content = jsonMessage.get("Content").asText();
                                                System.out.println(
                                                        displayName + " (" + participantRole + "): " + content);
                                            }
                                            break;
                                        case "application/vnd.amazonaws.connect.event.message.delivered":
                                            System.out.println("消息已送达");
                                            break;
                                        case "application/vnd.amazonaws.connect.event.message.read":
                                            System.out.println("消息已读");
                                            break;
                                        default:
                                            System.out.println("未知消息类型: " + contentType);
                                    }
                                } else if (jsonMessage.has("topic")) {
                                    // 处理订阅确认消息
                                    String topic = jsonMessage.get("topic").asText();
                                    if ("aws/subscribe".equals(topic)) {
                                        System.out.println("成功订阅主题");
                                    }
                                }
                            }
                        } catch (Exception e) {
                            System.err.println("解析消息失败: " + e.getMessage());
                            e.printStackTrace();
                        }
                    },
                    // 连接失败处理
                    (error) -> {
                        System.err.println("WebSocket连接失败: " + error);
                    },
                    // 连接成功后执行
                    () -> {
                        System.out.println("WebSocket连接已建立，开始接收消息...");
                    });

            System.out.println("WebSocket连接创建成功，准备发送测试消息...");

            // 步骤4: 发送typing事件
            sendTypingEvent(connectionToken, region);

            // 等待1秒
            try {
                Thread.sleep(5000);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }

            // 步骤5: 使用connectionToken发送消息
            SendMessageResponse messageResponse = sendChatMessage(connectionToken, "您好，这是一条测试消息！", region);

            // 保存消息ID，用于后续发送已读回执
            String messageId = messageResponse != null ? messageResponse.id() : null;

            // 等待3秒后发送已读回执
            if (messageId != null) {
                try {
                    Thread.sleep(3000);
                    // 发送已读回执
                    sendReadReceipt(connectionToken, messageId, region);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }

            // 保持程序运行一段时间，以便接收WebSocket消息
            System.out.println("等待接收消息...");
            try {
                // 等待120秒，以便接收消息
                Thread.sleep(120000);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                System.err.println("等待被中断: " + e.getMessage());
            }

            // 断开参与者连接
            System.out.println("断开参与者连接...");
            disconnectParticipant(connectionToken, region);

            // 关闭WebSocket连接
            webSocket.abort();
            System.out.println("WebSocket连接已关闭，程序即将退出。");

        } catch (Exception e) {
            System.err.println("处理过程中发生错误: " + e.getMessage());
            e.printStackTrace();
        }
    }

    /**
     * 创建聊天会话并获取participantToken
     */
    private static String startChatSession(String instanceId, String contactFlowId, Region region,
            String participantName) {
        System.out.println("正在创建聊天会话...");
        try {
            // 创建ConnectClient
            ConnectClient connectClient = ConnectClient.builder()
                    .region(region)
                    .credentialsProvider(ProfileCredentialsProvider.create())
                    .build();

            // 创建聊天属性
            Map<String, String> attributes = new HashMap<>();
            attributes.put("Test", "True");

            // 构建请求
            StartChatContactRequest chatRequest = StartChatContactRequest.builder()
                    .instanceId(instanceId)
                    .contactFlowId(contactFlowId)
                    .participantDetails(builder -> builder.displayName(participantName))
                    .attributes(attributes)
                    .initialMessage(ChatMessage.builder()
                            .contentType("text/plain")
                            .content("初始化聊天会话")
                            .build())
                    .build();

            // 发送请求
            StartChatContactResponse response = connectClient.startChatContact(chatRequest);

            // 输出响应信息
            System.out.println("聊天会话创建成功！");
            System.out.println("联系ID: " + response.contactId());
            System.out.println("参与者ID: " + response.participantId());

            // 返回participantToken用于后续操作
            return response.participantToken();

        } catch (Exception e) {
            System.err.println("创建聊天会话时出错: " + e.getMessage());
            e.printStackTrace();
            return null;
        }
    }

    /**
     * 使用participantToken创建参与者连接并获取connectionToken
     */
    private static CreateParticipantConnectionResponse createParticipantConnection(String participantToken,
            Region region) {
        System.out.println("正在创建参与者连接...");
        try {
            // 创建ConnectParticipantClient
            ConnectParticipantClient participantClient = ConnectParticipantClient.builder()
                    .region(region)
                    .build();

            // 构建创建连接请求
            CreateParticipantConnectionRequest connectionRequest = CreateParticipantConnectionRequest.builder()
                    .participantToken(participantToken)
                    .type(Arrays.asList(ConnectionType.CONNECTION_CREDENTIALS, ConnectionType.WEBSOCKET))
                    .build();

            // 发送请求
            CreateParticipantConnectionResponse connectionResponse = participantClient
                    .createParticipantConnection(connectionRequest);

            // 获取connectionToken
            String connectionToken = connectionResponse.connectionCredentials().connectionToken();

            // 获取websocketUrl
            String websocketUrl = connectionResponse.websocket().url();

            System.out.println("参与者连接创建成功！");
            // 出于安全考虑，只打印token的一部分
            System.out.println("连接Token: " + connectionToken.substring(0, 10) + "...");
            System.out.println("连接WebSocket Url: " + websocketUrl);

            return connectionResponse;

        } catch (Exception e) {
            System.err.println("创建参与者连接时出错: " + e.getMessage());
            e.printStackTrace();
            return null;
        }
    }

    /**
     * 使用connectionToken发送聊天消息
     * 
     * @return 发送消息的响应，包含消息ID
     */
    private static SendMessageResponse sendChatMessage(String connectionToken, String messageContent, Region region) {
        System.out.println("正在发送消息...");
        try {
            // 创建ConnectParticipantClient
            ConnectParticipantClient participantClient = ConnectParticipantClient.builder()
                    .region(region)
                    .build();

            // 构建发送消息请求
            SendMessageRequest messageRequest = SendMessageRequest.builder()
                    .connectionToken(connectionToken) // 使用之前获取的connectionToken
                    .contentType("text/plain")
                    .content(messageContent)
                    .build();

            // 发送消息
            SendMessageResponse messageResponse = participantClient.sendMessage(messageRequest);

            // 输出结果
            System.out.println("消息发送成功！");
            System.out.println("消息ID: " + messageResponse.id());
            System.out.println("消息时间戳: " + messageResponse.absoluteTime());

            return messageResponse;

        } catch (Exception e) {
            System.err.println("发送消息时出错: " + e.getMessage());
            e.printStackTrace();
            return null;
        }
    }

    /**
     * 创建一个WebSocket 连接
     */
    private static WebSocket createWebSocket(
            String url,
            Consumer<String> onMessageReceived,
            Consumer<String> onConnectionFailed,
            Runnable onConnectionOpen) {

        // 参数验证
        if (url == null || url.trim().isEmpty()) {
            throw new IllegalArgumentException("WebSocket URL cannot be empty");
        }
        if (onMessageReceived == null) {
            throw new IllegalArgumentException("Message receiver callback cannot be null");
        }

        try {
            // 创建WebSocket监听器
            java.net.http.WebSocket.Listener listener = new java.net.http.WebSocket.Listener() {
                StringBuilder messageBuilder = new StringBuilder();

                @Override
                public CompletionStage<?> onText(java.net.http.WebSocket webSocket,
                        CharSequence data,
                        boolean last) {
                    messageBuilder.append(data);

                    // 如果是消息的最后部分，处理完整消息
                    if (last) {
                        String message = messageBuilder.toString();
                        System.out.println("WebSocket: Message received");
                        onMessageReceived.accept(message);
                        messageBuilder.setLength(0); // 清空缓冲区
                    }

                    // 请求更多数据
                    webSocket.request(1);
                    return null;
                }

                @Override
                public void onOpen(java.net.http.WebSocket webSocket) {
                    System.out.println("WebSocket: Connection established");

                    // 重要：发送订阅消息
                    String subscribeMessage = "{\"topic\": \"aws/subscribe\", \"content\": {\"topics\": [\"aws/chat\"]}}";
                    System.out.println("发送订阅消息: " + subscribeMessage);
                    webSocket.sendText(subscribeMessage, true);

                    // 如果提供了连接成功的回调，则执行
                    if (onConnectionOpen != null) {
                        onConnectionOpen.run();
                    }

                    // 请求数据
                    webSocket.request(1);
                }

                @Override
                public CompletionStage<?> onClose(java.net.http.WebSocket webSocket,
                        int statusCode,
                        String reason) {
                    System.out.println("WebSocket: Connection closed: " + reason);
                    return null;
                }

                @Override
                public void onError(java.net.http.WebSocket webSocket,
                        Throwable error) {
                    String errorMessage = error.getMessage() != null ? error.getMessage() : "Unknown error";
                    System.err.println("WebSocket: Connection failed: " + errorMessage);
                    error.printStackTrace();
                    onConnectionFailed.accept(errorMessage);
                }
            };

            // 创建HttpClient
            java.net.http.HttpClient client = java.net.http.HttpClient.newHttpClient();

            // 创建并返回WebSocket
            return client.newWebSocketBuilder()
                    .buildAsync(URI.create(url), listener)
                    .join();

        } catch (Exception e) {
            System.err.println("创建WebSocket时出错: " + e.getMessage());
            e.printStackTrace();
            onConnectionFailed.accept(e.getMessage() != null ? e.getMessage() : "Unknown error");
            throw new RuntimeException("创建WebSocket连接失败", e);
        }
    }

    /**
     * 发送正在输入事件
     */
    private static void sendTypingEvent(String connectionToken, Region region) {
        System.out.println("正在发送输入事件...");
        try {
            // 创建ConnectParticipantClient
            ConnectParticipantClient participantClient = ConnectParticipantClient.builder()
                    .region(region)
                    .build();

            // 构建发送事件请求
            SendEventRequest eventRequest = SendEventRequest.builder()
                    .connectionToken(connectionToken)
                    .contentType("application/vnd.amazonaws.connect.event.typing")
                    .build();

            // 发送事件
            SendEventResponse eventResponse = participantClient.sendEvent(eventRequest);

            // 输出结果
            System.out.println("输入事件发送成功！");
            System.out.println("事件ID: " + eventResponse.id());
            System.out.println("事件时间戳: " + eventResponse.absoluteTime());

        } catch (Exception e) {
            System.err.println("发送输入事件时出错: " + e.getMessage());
            e.printStackTrace();
        }
    }

    /**
     * 发送消息已读事件
     */
    private static void sendReadReceipt(String connectionToken, String messageId, Region region) {
        System.out.println("正在发送已读回执...");
        try {
            // 创建ConnectParticipantClient
            ConnectParticipantClient participantClient = ConnectParticipantClient.builder()
                    .region(region)
                    .build();

            // 构建发送事件请求
            SendEventRequest eventRequest = SendEventRequest.builder()
                    .connectionToken(connectionToken)
                    .contentType("application/vnd.amazonaws.connect.event.message.read")
                    .content("{\"messageId\": \"" + messageId + "\"}")
                    .build();

            // 发送事件
            SendEventResponse eventResponse = participantClient.sendEvent(eventRequest);

            // 输出结果
            System.out.println("已读回执发送成功！");
            System.out.println("事件ID: " + eventResponse.id());
            System.out.println("事件时间戳: " + eventResponse.absoluteTime());

        } catch (Exception e) {
            System.err.println("发送已读回执时出错: " + e.getMessage());
            e.printStackTrace();
        }
    }

    /**
     * 断开参与者连接
     */
    private static void disconnectParticipant(String connectionToken, Region region) {
        System.out.println("正在断开参与者连接...");
        try {
            // 创建ConnectParticipantClient
            ConnectParticipantClient participantClient = ConnectParticipantClient.builder()
                    .region(region)
                    .build();

            // 构建断开连接请求
            DisconnectParticipantRequest disconnectRequest = DisconnectParticipantRequest.builder()
                    .connectionToken(connectionToken)
                    .build();

            // 发送请求
            participantClient.disconnectParticipant(disconnectRequest);

            System.out.println("参与者连接已成功断开！");

        } catch (Exception e) {
            System.err.println("断开参与者连接时出错: " + e.getMessage());
            e.printStackTrace();
        }
    }
}