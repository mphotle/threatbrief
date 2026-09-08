package com.mphotle.threatbrief.exception;

import java.time.LocalDateTime;

public record ErrorResponse (
    LocalDateTime timestamp,
    int status,
    String error,
    String message
) {}
