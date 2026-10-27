package com.exam.mapper;

import com.exam.entity.Department;
import org.apache.ibatis.annotations.*;

import java.util.List;

@Mapper
public interface DepartmentMapper {

    @Select("SELECT * FROM `department` WHERE `deleted_at` IS NULL ORDER BY `id` ASC")
    List<Department> findAllActive();

    @Select("SELECT * FROM `department` WHERE `id` = #{id} LIMIT 1")
    Department findById(@Param("id") Long id);

    @Select("SELECT * FROM `department` WHERE `name` = #{name} AND `deleted_at` IS NULL LIMIT 1")
    Department findByName(@Param("name") String name);

    @Insert("INSERT INTO `department` (`name`, `created_at`, `updated_at`, `version`) VALUES (#{name}, NOW(), NOW(), 0)")
    @Options(useGeneratedKeys = true, keyProperty = "id")
    int insert(Department department);

    @Update("UPDATE `department` SET `name` = #{name}, `updated_at` = NOW(), `version` = `version` + 1 WHERE `id` = #{id} AND `version` = #{version}")
    int update(Department department);

    @Update("UPDATE `department` SET `deleted_at` = NOW() WHERE `id` = #{id} AND `deleted_at` IS NULL")
    int softDelete(@Param("id") Long id);
}
