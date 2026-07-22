package com.example.productassistant.user;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

@Mapper
public interface AppUserMapper extends BaseMapper<AppUserEntity> {

    default AppUserEntity findByEmail(String email) {
        return selectOne(new LambdaQueryWrapper<AppUserEntity>()
                .eq(AppUserEntity::getEmail, email)
                .last("LIMIT 1"));
    }

    @Update("""
            UPDATE app_user
            SET points = points - 1
            WHERE id = #{userId}
              AND enabled = 1
              AND points > 0
            """)
    int debitOnePoint(@Param("userId") long userId);

    @Update("""
            UPDATE app_user
            SET points = points + 1
            WHERE id = #{userId}
            """)
    int refundOnePoint(@Param("userId") long userId);

    @Select("SELECT points FROM app_user WHERE id = #{userId} AND enabled = 1")
    Integer findCurrentBalance(@Param("userId") long userId);

    @Select("SELECT enabled FROM app_user WHERE id = #{userId}")
    Boolean findEnabled(@Param("userId") long userId);
}
