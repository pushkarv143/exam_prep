package com.examprep.user.mapper;

import com.examprep.user.dto.UserDto;
import com.examprep.user.entity.User;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper
public interface UserMapper {

    @Mapping(target = "roles", expression = "java(user.roleNames())")
    UserDto toDto(User user);
}
