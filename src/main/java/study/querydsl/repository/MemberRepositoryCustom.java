package study.querydsl.repository;

import study.querydsl.controller.dto.MemberSearchCondition;
import study.querydsl.controller.dto.MemberTeamDto;

import java.util.List;

public interface MemberRepositoryCustom {

    List<MemberTeamDto> search(MemberSearchCondition condition);

}
