package study.querydsl.controller.dto;

import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
public class UserDto {

    public String name;
    public int age;

    public UserDto(String name, int age) {
        this.name = name;
        this.age = age;
    }

}
