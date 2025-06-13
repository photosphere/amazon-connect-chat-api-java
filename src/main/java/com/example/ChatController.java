package com.example;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.connectparticipant.model.CreateParticipantConnectionResponse;
import software.amazon.awssdk.services.connectparticipant.model.SendMessageResponse;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@RestController
@RequestMapping("/api/chat")
public class ChatController {

    @Autowired
    private ChatService chatService;

    // 存储会话信息，使用ConcurrentHashMap保证线程安全
    private final Map<String, String> participantTokens = new ConcurrentHashMap<>();
    private final Map<String, String> connectionTokens = new ConcurrentHashMap<>();
    private Region region = Region.US_EAST_1; // 默认区域

    @PostMapping("/start")
    public Map<String, String> startChatSession(@RequestParam String instanceId,
                                               @RequestParam String contactFlowId,
                                               @RequestParam String participantName) {
        Map<String, String> response = new HashMap<>();
        
        try {
            // 调用现有的startChatSession方法
            String participantToken = ConnectChatExample.startChatSession(instanceId, contactFlowId, region, participantName);
            
            if (participantToken != null) {
                // 创建参与者连接
                CreateParticipantConnectionResponse connectionResponse = 
                    ConnectChatExample.createParticipantConnection(participantToken, region);
                
                if (connectionResponse != null) {
                    String connectionToken = connectionResponse.connectionCredentials().connectionToken();
                    String websocketUrl = connectionResponse.websocket().url();
                    
                    // 生成唯一会话ID
                    String sessionId = java.util.UUID.randomUUID().toString();
                    
                    // 存储会话信息
                    participantTokens.put(sessionId, participantToken);
                    connectionTokens.put(sessionId, connectionToken);
                    
                    response.put("status", "success");
                    response.put("message", "聊天会话已创建");
                    response.put("websocketUrl", websocketUrl);
                    response.put("sessionId", sessionId);
                    return response;
                }
            }
            
            response.put("status", "error");
            response.put("message", "无法创建聊天会话");
            return response;
            
        } catch (Exception e) {
            response.put("status", "error");
            response.put("message", "创建聊天会话时出错: " + e.getMessage());
            return response;
        }
    }
    
    @GetMapping("/session")
    public Map<String, String> getSessionStatus(@RequestParam String sessionId) {
        Map<String, String> response = new HashMap<>();
        
        if (connectionTokens.containsKey(sessionId)) {
            response.put("status", "success");
            response.put("message", "会话有效");
            response.put("isActive", "true");
        } else {
            response.put("status", "error");
            response.put("message", "会话无效或已过期");
            response.put("isActive", "false");
        }
        
        return response;
    }

    @PostMapping("/send")
    public Map<String, String> sendMessage(@RequestParam String message, @RequestParam String sessionId) {
        Map<String, String> response = new HashMap<>();
        
        String connectionToken = connectionTokens.get(sessionId);
        if (connectionToken == null) {
            response.put("status", "error");
            response.put("message", "未建立聊天会话，请先开始聊天");
            return response;
        }
        
        try {
            // 调用现有的sendChatMessage方法
            SendMessageResponse messageResponse = 
                ConnectChatExample.sendChatMessage(connectionToken, message, region);
            
            if (messageResponse != null) {
                response.put("status", "success");
                response.put("message", "消息已发送");
                response.put("messageId", messageResponse.id());
                return response;
            } else {
                response.put("status", "error");
                response.put("message", "发送消息失败");
                return response;
            }
            
        } catch (Exception e) {
            response.put("status", "error");
            response.put("message", "发送消息时出错: " + e.getMessage());
            return response;
        }
    }

    @PostMapping("/end")
    public Map<String, String> endChatSession(@RequestParam String sessionId) {
        Map<String, String> response = new HashMap<>();
        
        String connectionToken = connectionTokens.get(sessionId);
        if (connectionToken == null) {
            response.put("status", "error");
            response.put("message", "未建立聊天会话");
            return response;
        }
        
        try {
            // 调用现有的disconnectParticipant方法
            ConnectChatExample.disconnectParticipant(connectionToken, region);
            
            // 移除会话信息
            connectionTokens.remove(sessionId);
            participantTokens.remove(sessionId);
            
            response.put("status", "success");
            response.put("message", "聊天会话已结束");
            return response;
            
        } catch (Exception e) {
            response.put("status", "error");
            response.put("message", "结束聊天会话时出错: " + e.getMessage());
            return response;
        }
    }
}