package com.travyn.chat.dto;

import com.travyn.chat.entity.MessageType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class SendMessageRequest {

    @NotBlank(message = "Message content is required")
    @Size(max = 2000, message = "Message must be at most 2000 characters")
    private String content;

    /** Defaults to TEXT if not provided. Use IMAGE for photo messages. */
    private MessageType messageType;
}
