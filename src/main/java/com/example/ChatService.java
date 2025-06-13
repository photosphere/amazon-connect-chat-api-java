package com.example;

import org.springframework.stereotype.Service;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.connectparticipant.model.CreateParticipantConnectionResponse;
import software.amazon.awssdk.services.connectparticipant.model.SendMessageResponse;

@Service
public class ChatService {
    
    // 这个服务类可以用来封装ConnectChatExample中的方法
    // 目前我们直接在控制器中调用静态方法，但在实际应用中，
    // 最好将这些方法封装在服务类中，以便更好地管理依赖和测试
    
    public String startChatSession(String instanceId, String contactFlowId, Region region, String participantName) {
        return ConnectChatExample.startChatSession(instanceId, contactFlowId, region, participantName);
    }
    
    public CreateParticipantConnectionResponse createParticipantConnection(String participantToken, Region region) {
        return ConnectChatExample.createParticipantConnection(participantToken, region);
    }
    
    public SendMessageResponse sendChatMessage(String connectionToken, String messageContent, Region region) {
        return ConnectChatExample.sendChatMessage(connectionToken, messageContent, region);
    }
    
    public void disconnectParticipant(String connectionToken, Region region) {
        ConnectChatExample.disconnectParticipant(connectionToken, region);
    }
}