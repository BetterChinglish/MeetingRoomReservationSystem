package com.exam.mapper;

import com.exam.entity.User;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface UserMapper {

    @Select("SELECT * FROM `user` WHERE `username` = #{username} LIMIT 1")
    User findByUsername(@Param("username") String username);

    @Select("SELECT * FROM `user` WHERE `id` = #{id} LIMIT 1")
    User findById(@Param("id") Long id);
}
