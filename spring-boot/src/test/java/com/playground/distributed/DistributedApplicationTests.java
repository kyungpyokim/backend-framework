package com.playground.distributed;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.playground.distributed.case4.ResilienceService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.data.redis.listener.RedisMessageListenerContainer;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
class DistributedApplicationTests {

    @Autowired private MockMvc mockMvc;

    @MockBean private RedisConnectionFactory redisConnectionFactory;

    @MockBean private RedisMessageListenerContainer redisMessageListenerContainer;

    @MockBean private StringRedisTemplate redisTemplate;

    @Autowired private ResilienceService resilienceService;

    @Test
    void testHealthEndpoint() throws Exception {
        mockMvc.perform(get("/health"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ok"));
    }

    @Test
    void testCase4CircuitBreakerTransition() throws Exception {
        // 1. Initial State: Closed
        resilienceService.setHealthy(true);
        mockMvc.perform(get("/case4/call"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.circuit_state").value("CLOSED"))
                .andExpect(jsonPath("$.result.status").value("SUCCESS"));

        // 2. Inject Fault
        mockMvc.perform(
                        post("/case4/external/fault")
                                .contentType(MediaType.APPLICATION_JSON_VALUE)
                                .content("{\"is_healthy\": false}"))
                .andExpect(status().isOk());

        // 3. Trigger 3 failures
        for (int i = 0; i < 3; i++) {
            mockMvc.perform(get("/case4/call"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.result.status").value("FALLBACK"));
        }

        // 4. State should be OPEN
        mockMvc.perform(get("/case4/circuit-status"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.state").value("OPEN"));

        // 5. Restore Health
        mockMvc.perform(
                        post("/case4/external/fault")
                                .contentType(MediaType.APPLICATION_JSON_VALUE)
                                .content("{\"is_healthy\": true}"))
                .andExpect(status().isOk());
    }

    @SuppressWarnings("unchecked")
    @Test
    void testCase1InventoryInit() throws Exception {
        ValueOperations<String, String> ops = mock(ValueOperations.class);
        when(redisTemplate.opsForValue()).thenReturn(ops);

        mockMvc.perform(
                        post("/case1/inventory/init")
                                .contentType(MediaType.APPLICATION_JSON_VALUE)
                                .content("{\"item_id\": \"item-spring-1\", \"stock\": 10}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.stock").value(10));
    }
}
