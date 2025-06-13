// 聊天应用的JavaScript代码
document.addEventListener('DOMContentLoaded', function() {
    // DOM元素
    const startButton = document.getElementById('startButton');
    const sendButton = document.getElementById('sendButton');
    const endButton = document.getElementById('endButton');
    const messageInput = document.getElementById('messageInput');
    const chatMessages = document.getElementById('chatMessages');
    const statusMessage = document.getElementById('statusMessage');
    const connectionStatus = document.getElementById('connectionStatus');
    
    // 表单元素
    const instanceIdInput = document.getElementById('instanceId');
    const contactFlowIdInput = document.getElementById('contactFlowId');
    const participantNameInput = document.getElementById('participantName');
    
    // WebSocket连接
    let webSocket = null;
    
    // 添加事件监听器
    startButton.addEventListener('click', startChatSession);
    sendButton.addEventListener('click', sendMessage);
    endButton.addEventListener('click', endChatSession);
    messageInput.addEventListener('keypress', function(e) {
        if (e.key === 'Enter') {
            sendMessage();
        }
    });
    
    // 开始聊天会话
    function startChatSession() {
        // 获取输入值
        const instanceId = instanceIdInput.value.trim();
        const contactFlowId = contactFlowIdInput.value.trim();
        const participantName = participantNameInput.value.trim();
        
        // 验证输入
        if (!instanceId || !contactFlowId || !participantName) {
            addSystemMessage('请填写所有必填字段');
            return;
        }
        
        // 更新UI状态
        updateStatus('正在连接...', 'connecting');
        disableForm(true);
        
        // 调用API开始聊天会话
        fetch('/api/chat/start', {
            method: 'POST',
            headers: {
                'Content-Type': 'application/x-www-form-urlencoded',
            },
            body: `instanceId=${encodeURIComponent(instanceId)}&contactFlowId=${encodeURIComponent(contactFlowId)}&participantName=${encodeURIComponent(participantName)}`
        })
        .then(response => response.json())
        .then(data => {
            if (data.status === 'success') {
                // 连接WebSocket
                connectWebSocket(data.websocketUrl);
                
                // 更新UI
                updateStatus('已连接', 'connected');
                enableChatControls(true);
                addSystemMessage('聊天会话已开始');
            } else {
                updateStatus('连接失败', 'disconnected');
                disableForm(false);
                addSystemMessage(`错误: ${data.message}`);
            }
        })
        .catch(error => {
            console.error('Error:', error);
            updateStatus('连接失败', 'disconnected');
            disableForm(false);
            addSystemMessage(`发生错误: ${error.message}`);
        });
    }
    
    // 发送消息
    function sendMessage() {
        const message = messageInput.value.trim();
        
        if (!message) {
            return;
        }
        
        // 禁用发送按钮，防止重复发送
        sendButton.disabled = true;
        
        // 添加用户消息到聊天窗口
        addUserMessage(message);
        
        // 清空输入框
        messageInput.value = '';
        
        // 调用API发送消息
        fetch('/api/chat/send', {
            method: 'POST',
            headers: {
                'Content-Type': 'application/x-www-form-urlencoded',
            },
            body: `message=${encodeURIComponent(message)}`
        })
        .then(response => response.json())
        .then(data => {
            if (data.status !== 'success') {
                addSystemMessage(`发送失败: ${data.message}`);
            }
            // 重新启用发送按钮
            sendButton.disabled = false;
        })
        .catch(error => {
            console.error('Error:', error);
            addSystemMessage(`发送错误: ${error.message}`);
            sendButton.disabled = false;
        });
    }
    
    // 结束聊天会话
    function endChatSession() {
        // 调用API结束聊天会话
        fetch('/api/chat/end', {
            method: 'POST'
        })
        .then(response => response.json())
        .then(data => {
            // 关闭WebSocket连接
            if (webSocket) {
                webSocket.close();
                webSocket = null;
            }
            
            // 更新UI
            updateStatus('未连接', 'disconnected');
            disableForm(false);
            enableChatControls(false);
            addSystemMessage('聊天会话已结束');
        })
        .catch(error => {
            console.error('Error:', error);
            addSystemMessage(`结束会话错误: ${error.message}`);
        });
    }
    
    // 连接WebSocket
    function connectWebSocket(url) {
        try {
            webSocket = new WebSocket(url);
            
            webSocket.onopen = function() {
                console.log('WebSocket连接已建立');
                // 发送订阅消息
                const subscribeMessage = {
                    topic: "aws/subscribe",
                    content: {
                        topics: ["aws/chat"]
                    }
                };
                webSocket.send(JSON.stringify(subscribeMessage));
            };
            
            webSocket.onmessage = function(event) {
                console.log('收到WebSocket消息:', event.data);
                
                try {
                    const message = JSON.parse(event.data);
                    
                    // 处理不同类型的消息
                    if (message.content) {
                        const content = typeof message.content === 'string' 
                            ? JSON.parse(message.content) 
                            : message.content;
                        
                        if (content.ContentType === 'text/plain' && content.Content) {
                            // 文本消息
                            if (content.ParticipantRole !== 'CUSTOMER') {
                                addAgentMessage(content.Content, content.DisplayName);
                            }
                        } else if (content.ContentType === 'application/vnd.amazonaws.connect.event.typing') {
                            // 输入事件
                            if (content.ParticipantRole !== 'CUSTOMER') {
                                showTypingIndicator(content.DisplayName);
                            }
                        } else if (content.ContentType === 'application/vnd.amazonaws.connect.event.participant.joined') {
                            // 参与者加入
                            addSystemMessage(`${content.DisplayName} (${content.ParticipantRole}) 已加入聊天`);
                        } else if (content.ContentType === 'application/vnd.amazonaws.connect.event.participant.left') {
                            // 参与者离开
                            addSystemMessage(`${content.DisplayName} (${content.ParticipantRole}) 已离开聊天`);
                        } else if (content.ContentType === 'application/vnd.amazonaws.connect.event.chat.ended') {
                            // 聊天结束
                            addSystemMessage('聊天已结束');
                            updateStatus('未连接', 'disconnected');
                            disableForm(false);
                            enableChatControls(false);
                        }
                    }
                } catch (error) {
                    console.error('解析WebSocket消息失败:', error);
                }
            };
            
            webSocket.onclose = function() {
                console.log('WebSocket连接已关闭');
                webSocket = null;
            };
            
            webSocket.onerror = function(error) {
                console.error('WebSocket错误:', error);
                addSystemMessage('WebSocket连接错误');
            };
            
        } catch (error) {
            console.error('创建WebSocket连接失败:', error);
            addSystemMessage(`创建WebSocket连接失败: ${error.message}`);
        }
    }
    
    // 添加系统消息
    function addSystemMessage(message) {
        const messageElement = document.createElement('div');
        messageElement.className = 'system-message';
        messageElement.textContent = message;
        chatMessages.appendChild(messageElement);
        scrollToBottom();
    }
    
    // 添加用户消息
    function addUserMessage(message) {
        const messageElement = document.createElement('div');
        messageElement.className = 'message user-message';
        messageElement.textContent = message;
        chatMessages.appendChild(messageElement);
        scrollToBottom();
    }
    
    // 添加客服消息
    function addAgentMessage(message, name) {
        // 移除输入指示器
        removeTypingIndicator();
        
        const messageElement = document.createElement('div');
        messageElement.className = 'message agent-message';
        
        const nameSpan = document.createElement('div');
        nameSpan.className = 'agent-name';
        nameSpan.textContent = name || 'Agent';
        
        const contentSpan = document.createElement('div');
        contentSpan.textContent = message;
        
        messageElement.appendChild(nameSpan);
        messageElement.appendChild(contentSpan);
        chatMessages.appendChild(messageElement);
        scrollToBottom();
    }
    
    // 显示输入指示器
    function showTypingIndicator(name) {
        // 移除现有的输入指示器
        removeTypingIndicator();
        
        const indicatorElement = document.createElement('div');
        indicatorElement.className = 'typing-indicator system-message';
        indicatorElement.textContent = `${name || 'Agent'} 正在输入...`;
        indicatorElement.id = 'typingIndicator';
        chatMessages.appendChild(indicatorElement);
        scrollToBottom();
        
        // 5秒后自动移除输入指示器
        setTimeout(removeTypingIndicator, 5000);
    }
    
    // 移除输入指示器
    function removeTypingIndicator() {
        const indicator = document.getElementById('typingIndicator');
        if (indicator) {
            indicator.remove();
        }
    }
    
    // 滚动到底部
    function scrollToBottom() {
        chatMessages.scrollTop = chatMessages.scrollHeight;
    }
    
    // 更新状态
    function updateStatus(message, statusClass) {
        statusMessage.textContent = message;
        connectionStatus.className = `connection-status ${statusClass}`;
    }
    
    // 禁用表单
    function disableForm(disabled) {
        instanceIdInput.disabled = disabled;
        contactFlowIdInput.disabled = disabled;
        participantNameInput.disabled = disabled;
        startButton.disabled = disabled;
    }
    
    // 启用/禁用聊天控件
    function enableChatControls(enabled) {
        messageInput.disabled = !enabled;
        sendButton.disabled = !enabled;
        endButton.disabled = !enabled;
        
        if (enabled) {
            messageInput.focus();
        }
    }
});