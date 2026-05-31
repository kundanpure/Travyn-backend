package com.travyn.chat.dto;

import com.travyn.chat.entity.MessageType;
import lombok.Data;

@Data
public class SendDirectMessageRequest {
    private String content;
    private MessageType messageType;
}
