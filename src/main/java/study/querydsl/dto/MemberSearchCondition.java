package study.querydsl.dto;

import lombok.Data;

@Data
public class MemberSearchCondition {

    //회원명, 팀명, 나이(ageGoe, ageLoe)
    private String username;
    private String teamName;
    private Integer ageGoe; // null일수도 있기때문에 Integer를 사용
    private Integer ageLoe;
}
