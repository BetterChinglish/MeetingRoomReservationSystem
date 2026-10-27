package com.exam.mapper;

import com.exam.dto.DashboardStatsVO;
import com.exam.entity.Reservation;
import org.apache.ibatis.annotations.*;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

@Mapper
public interface ReservationMapper {

    @Select("SELECT * FROM `reservation` " +
            "WHERE `room_id` = #{roomId} AND `reserve_date` = #{reserveDate} AND `status` != 'CANCELLED' " +
            "FOR UPDATE")
    List<Reservation> findActiveByRoomAndDateForUpdate(@Param("roomId") Long roomId, @Param("reserveDate") LocalDate reserveDate);

    @Insert("INSERT INTO `reservation` (`room_id`, `user_id`, `reserve_date`, `start_time`, `end_time`, `dept_id`, `title`, " +
            "`room_name`, `dept_name`, `user_name`, `status`, `created_at`, `updated_at`) " +
            "VALUES (#{roomId}, #{userId}, #{reserveDate}, #{startTime}, #{endTime}, #{deptId}, #{title}, " +
            "#{roomName}, #{deptName}, #{userName}, #{status}, NOW(), NOW())")
    @Options(useGeneratedKeys = true, keyProperty = "id")
    int insert(Reservation reservation);

    @Select("SELECT * FROM `reservation` WHERE `id` = #{id} LIMIT 1")
    Reservation findById(@Param("id") Long id);

    List<Reservation> queryList(
            @Param("roomId") Long roomId,
            @Param("reserveDate") LocalDate reserveDate,
            @Param("deptId") Long deptId,
            @Param("userId") Long userId,
            @Param("status") String status,
            @Param("offset") int offset,
            @Param("limit") int limit
    );

    long countList(
            @Param("roomId") Long roomId,
            @Param("reserveDate") LocalDate reserveDate,
            @Param("deptId") Long deptId,
            @Param("userId") Long userId,
            @Param("status") String status
    );

    @Update("UPDATE `reservation` SET `status` = #{status}, `updated_at` = #{updatedAt} WHERE `id` = #{id}")
    int updateStatus(@Param("id") Long id, @Param("status") String status, @Param("updatedAt") LocalDateTime updatedAt);

    @Select("SELECT COUNT(1) FROM `reservation` WHERE `room_id` = #{roomId}")
    long countByRoomId(@Param("roomId") Long roomId);

    @Select("SELECT COUNT(1) FROM `reservation` WHERE `dept_id` = #{deptId}")
    long countByDeptId(@Param("deptId") Long deptId);

    List<DashboardStatsVO.RoomStat> findRoomStatsByDate(@Param("date") LocalDate date);

    List<DashboardStatsVO.RoomStat> findRoomStatsByMonth(@Param("startDate") LocalDate startDate, @Param("endDate") LocalDate endDate);

    List<DashboardStatsVO.DeptStat> findDeptStatsByDate(@Param("date") LocalDate date);

    List<DashboardStatsVO.DeptStat> findDeptStatsByMonth(@Param("startDate") LocalDate startDate, @Param("endDate") LocalDate endDate);
}
