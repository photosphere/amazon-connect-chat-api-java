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

import java.io.IOException;
import java.net.URI;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

/**
 * 使用AWS SDK for Java调用Amazon Connect的StartChatContact API发起聊天
 */
public class ConnectChatExample {

    public static void main(String[] args) {
        // 替换为你的实际配置
        String instanceId = "b7e4b4ed-1bdf-4b14-b624-d9328f08725a"; // Amazon Connect实例ID
        String contactFlowId = "4fd58c6f-bfa4-40cc-8bb7-e82adab81d3a"; // 联系流ID
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
            String connectionToken = createParticipantConnection(participantToken, region);
            if (connectionToken == null) {
                System.err.println("无法获取connectionToken，退出程序");
                return;
            }

            // 步骤3: 使用connectionToken发送消息
            sendChatMessage(connectionToken, "您好，这是一条测试消息！", region);

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
            attributes.put("customerName", "API Test User");

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
    private static String createParticipantConnection(String participantToken, Region region) {
        System.out.println("正在创建参与者连接...");
        try {
            // 创建ConnectParticipantClient
            ConnectParticipantClient participantClient = ConnectParticipantClient.builder()
                    .region(region)
                    .build();

            // 构建创建连接请求
            CreateParticipantConnectionRequest connectionRequest = CreateParticipantConnectionRequest.builder()
                    .participantToken(participantToken)
                    .type(ConnectionType.CONNECTION_CREDENTIALS)
                    .build();

            // 发送请求
            CreateParticipantConnectionResponse connectionResponse = participantClient
                    .createParticipantConnection(connectionRequest);

            // 获取connectionToken
            String connectionToken = connectionResponse.connectionCredentials().connectionToken();

            System.out.println("参与者连接创建成功！");
            // 出于安全考虑，只打印token的一部分
            System.out.println("连接Token: " + connectionToken.substring(0, 10) + "...");

            return connectionToken;

        } catch (Exception e) {
            System.err.println("创建参与者连接时出错: " + e.getMessage());
            e.printStackTrace();
            return null;
        }
    }

    /**
     * 使用connectionToken发送聊天消息
     */
    private static void sendChatMessage(String connectionToken, String messageContent, Region region) {
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

        } catch (Exception e) {
            System.err.println("发送消息时出错: " + e.getMessage());
            e.printStackTrace();
        }
    }

}