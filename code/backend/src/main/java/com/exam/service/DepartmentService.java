package com.exam.service;

import com.exam.common.exception.BusinessException;
import com.exam.entity.Department;
import com.exam.mapper.DepartmentMapper;
import com.exam.mapper.ReservationMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class DepartmentService {

    private final DepartmentMapper departmentMapper;
    private final ReservationMapper reservationMapper;

    public List<Department> getAllActive() {
        return departmentMapper.findAllActive();
    }

    public Department getById(Long id) {
        Department dept = departmentMapper.findById(id);
        if (dept == null || dept.getDeletedAt() != null) {
            throw new BusinessException(404, "部门不存在");
        }
        return dept;
    }

    @Transactional
    public Department create(String name) {
        Department existing = departmentMapper.findByName(name);
        if (existing != null) {
            throw new BusinessException(400, "部门名称已存在");
        }
        Department dept = Department.builder()
                .name(name)
                .build();
        departmentMapper.insert(dept);
        return dept;
    }

    @Transactional
    public void delete(Long id) {
        Department dept = departmentMapper.findById(id);
        if (dept == null || dept.getDeletedAt() != null) {
            throw new BusinessException(404, "部门不存在");
        }
        // Soft delete
        departmentMapper.softDelete(id);
    }
}
