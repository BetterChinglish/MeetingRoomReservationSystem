package com.exam.controller;

import com.exam.annotation.RequireAdmin;
import com.exam.common.result.ApiResponse;
import com.exam.entity.Department;
import com.exam.service.DepartmentService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/departments")
@RequiredArgsConstructor
public class DepartmentController {

    private final DepartmentService departmentService;

    @GetMapping
    public ApiResponse<List<Department>> list() {
        return ApiResponse.success(departmentService.getAllActive());
    }

    @PostMapping
    @RequireAdmin
    public ApiResponse<Department> create(@RequestBody Map<String, String> body) {
        String name = body.get("name");
        return ApiResponse.created(departmentService.create(name));
    }

    @DeleteMapping("/{id}")
    @RequireAdmin
    public ApiResponse<Void> delete(@PathVariable("id") Long id) {
        departmentService.delete(id);
        return ApiResponse.success();
    }
}
