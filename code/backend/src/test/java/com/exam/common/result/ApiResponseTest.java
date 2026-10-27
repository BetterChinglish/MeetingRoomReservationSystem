package com.exam.common.result;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

public class ApiResponseTest {

    @Test
    public void testSuccess() {
        ApiResponse<String> response = ApiResponse.success("test data");
        assertEquals(200, response.getCode());
        assertEquals("操作成功", response.getMessage());
        assertEquals("test data", response.getData());
    }

    @Test
    public void testCreated() {
        ApiResponse<Integer> response = ApiResponse.created(100);
        assertEquals(201, response.getCode());
        assertEquals("创建成功", response.getMessage());
        assertEquals(100, response.getData());
    }

    @Test
    public void testError() {
        ApiResponse<Object> response = ApiResponse.error(ResultCode.CONFLICT);
        assertEquals(409, response.getCode());
        assertTrue(response.getMessage().contains("冲突"));
    }
}
