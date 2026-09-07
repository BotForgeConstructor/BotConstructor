package org.demchenko.auth.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.demchenko.api.web.error.ApiErrorMapper;
import org.demchenko.api.web.error.RestError;
import org.demchenko.api.web.trace.CorrelationIdFilter;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.List;
import java.util.UUID;

@Component
@RequiredArgsConstructor
public class SecurityErrorWriter {
    private final ApiErrorMapper mapper;
    private final ObjectMapper objectMapper;

    public void write(HttpServletRequest request, HttpServletResponse response, int status, String code, String message)
            throws IOException {
        Object value = request.getAttribute(CorrelationIdFilter.REQUEST_ATTRIBUTE);
        String traceId = value instanceof String id && !id.isBlank() ? id : UUID.randomUUID().toString();
        response.setStatus(status);
        response.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
        objectMapper.writeValue(response.getOutputStream(), mapper.toApi(new RestError(code, message, traceId, List.of())));
    }
}
