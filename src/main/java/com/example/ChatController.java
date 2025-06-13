package com.example;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.connectparticipant.model.CreateParticipantConnectionResponse;
import software.amazon.awssdk.services.connectparticipant.model.SendMessageResponse;

import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/chat")
public class ChatController {

    @Autowired
    private ChatService chatService;

    // 存储会话信息
    private String participantToken;
    private String connectionToken;
    private Region region = Region.US_EAST_1; // 默认区域

    @PostMapping("/start")
    public Map<String, String> startChatSession(@RequestParam String instanceId,
                                               @RequestParam String contactFlowId,
                                               @RequestParam String participantName) {
        Map<String, String> response = new HashMap<>();
        
        try {
            // 调用现有的startChatSession方法
            participantToken = ConnectChatExample.startChatSession(instanceId, contactFlowId, region, participantName);
            
            if (participantToken != null) {
                // 创建参与者连接
                CreateParticipantConnectionResponse connectionResponse = 
                    ConnectChatExample.createParticipantConnection(participantToken, region);
                
                if (connectionResponse != null) {
                    connectionToken = connectionResponse.connectionCredentials().connectionToken();
                    String websocketUrl = connectionResponse.websocket().url();
                    
                    response.put("status", "success");
                    response.put("message", "聊天会话已创建");
                    response.put("websocketUrl", websocketUrl);
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

    @PostMapping("/send")
    public Map<String, String> sendMessage(@RequestParam String message) {
        Map<String, String> response = new HashMap<>();
        
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
    public Map<String, String> endChatSession() {
        Map<String, String> response = new HashMap<>();
        
        if (connectionToken == null) {
            response.put("status", "error");
            response.put("message", "未建立聊天会话");
            return response;
        }
        
        try {
            // 调用现有的disconnectParticipant方法
            ConnectChatExample.disconnectParticipant(connectionToken, region);
            
            // 重置会话信息
            connectionToken = null;
            participantToken = null;
            
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