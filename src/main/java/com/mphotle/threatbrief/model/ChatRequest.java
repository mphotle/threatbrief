package com.mphotle.threatbrief.model;

import java.util.List;

public record ChatRequest(
    List<Message> messages
) {}
