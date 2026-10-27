package com.exam.mapper;

import com.exam.entity.MeetingRoom;
import org.apache.ibatis.annotations.*;

import java.util.List;

@Mapper
public interface MeetingRoomMapper {

    @Select("SELECT * FROM `meeting_room` WHERE `status` = 'ENABLED' AND `deleted_at` IS NULL ORDER BY `id` ASC")
    List<MeetingRoom> findAllActive();

    List<MeetingRoom> findAll(@Param("status") String status, @Param("keyword") String keyword);

    @Select("SELECT * FROM `meeting_room` WHERE `id` = #{id} LIMIT 1")
    MeetingRoom findById(@Param("id") Long id);

    @Select("SELECT * FROM `meeting_room` WHERE `id` = #{id} FOR UPDATE")
    MeetingRoom findByIdForUpdate(@Param("id") Long id);

    @Insert("INSERT INTO `meeting_room` (`name`, `location`, `capacity`, `status`, `created_at`, `updated_at`, `version`) " +
            "VALUES (#{name}, #{location}, #{capacity}, #{status}, NOW(), NOW(), 0)")
    @Options(useGeneratedKeys = true, keyProperty = "id")
    int insert(MeetingRoom meetingRoom);

    @Update("UPDATE `meeting_room` SET `name` = #{name}, `location` = #{location}, `capacity` = #{capacity}, " +
            "`updated_at` = NOW(), `version` = `version` + 1 WHERE `id` = #{id} AND `version` = #{version}")
    int update(MeetingRoom meetingRoom);

    @Update("UPDATE `meeting_room` SET `status` = #{status}, `updated_at` = NOW() WHERE `id` = #{id}")
    int updateStatus(@Param("id") Long id, @Param("status") String status);

    @Update("UPDATE `meeting_room` SET `deleted_at` = NOW() WHERE `id` = #{id} AND `deleted_at` IS NULL")
    int softDelete(@Param("id") Long id);
}
