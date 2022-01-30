package study.querydsl;

import com.querydsl.core.QueryResults;
import com.querydsl.core.Tuple;
import com.querydsl.core.types.ExpressionUtils;
import com.querydsl.core.types.Projections;
import com.querydsl.core.types.dsl.CaseBuilder;
import com.querydsl.core.types.dsl.Expressions;
import com.querydsl.core.types.dsl.Wildcard;
import com.querydsl.jpa.JPAExpressions;
import com.querydsl.jpa.impl.JPAQueryFactory;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;
import study.querydsl.controller.dto.MemberDto;
import study.querydsl.controller.dto.QMemberDto;
import study.querydsl.controller.dto.UserDto;
import study.querydsl.entity.Member;
import study.querydsl.entity.QMember;
import study.querydsl.entity.Team;

import javax.persistence.EntityManager;
import javax.persistence.EntityManagerFactory;
import javax.persistence.PersistenceUnit;
import javax.persistence.TypedQuery;

import java.util.List;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.hasItems;
import static org.hamcrest.Matchers.hasProperty;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.nullValue;
import static study.querydsl.entity.QMember.member;
import static study.querydsl.entity.QTeam.team;

@SpringBootTest
@Transactional
public class QuerydslBasicTest {

    @Autowired
    EntityManager em;

    @PersistenceUnit
    EntityManagerFactory emf;

    JPAQueryFactory queryFactory;


    @BeforeEach
    public void setUp() {
        queryFactory = new JPAQueryFactory(em);
        Team teamA = new Team("TeamA");
        Team teamB = new Team("TeamB");
        em.persist(teamA);
        em.persist(teamB);

        Member member1 = new Member("Member1", 10, teamA);
        Member member2 = new Member("Member2", 20, teamA);

        Member member3 = new Member("Member3", 30, teamB);
        Member member4 = new Member("Member4", 40, teamB);

        em.persist(member1);
        em.persist(member2);
        em.persist(member3);
        em.persist(member4);
    }

    @Test
    public void startJPQL() {
        // member1을 찾아라.
        Member member1WithJpql = em.createQuery("SELECT m FROM Member m WHERE m.username = :username", Member.class)
            .setParameter("username", "Member1")
            .getSingleResult();

        assertThat(member1WithJpql.getUsername(), is("Member1"));
    }

    @Test
    public void startQuerydsl() {

        Member memberWithQuerydsl = queryFactory
            .select(member)
            .from(member)
            .where(member.username.eq("Member1"))
            .fetchOne();

        assertThat(memberWithQuerydsl.getUsername(), is("Member1"));
    }

    @Test
    public void search() {
        Member findMember = queryFactory
            .selectFrom(QMember.member)
            .where(
                QMember.member.username.eq("Member1"),
                QMember.member.age.between(10, 20)
            )
            .fetchOne();

//        Member findMember = queryFactory
//            .selectFrom(QMember.member)
//            .fetchFirst();

//        Long count = queryFactory
////            .select(Wildcard.count)
//            .select(member.count())
//            .from(member)
//            .fetchOne();

        assertThat(findMember.getUsername(), is("Member1"));
    }

    @Test
    public void sort() {
        em.persist(new Member(null, 100));
        em.persist(new Member("Member5", 100));
        em.persist(new Member("Member6", 100));

        List<Member> result = queryFactory
            .selectFrom(member)
            .orderBy(member.age.desc(), member.username.asc().nullsLast())
            .fetch();

        Member member5 = result.get(0);
        Member member6 = result.get(1);
        Member memberNull = result.get(2);

        assertThat(member5.getUsername(), is("Member5"));
        assertThat(member6.getUsername(), is("Member6"));
        assertThat(memberNull.getUsername(), is(nullValue()));
    }

    @Test
    public void paging1() {
        List<Member> result = queryFactory
            .selectFrom(member)
            .orderBy(member.username.desc())
            .offset(1)
            .limit(2)
            .fetch();

        assertThat(result.size(), is(2));
    }

    @Test
    public void paging2() {
        QueryResults<Member> memberQueryResults = queryFactory
            .selectFrom(member)
            .orderBy(member.username.desc())
            .limit(2)
            .offset(1)
            .fetchResults();

        assertThat(memberQueryResults.getResults().size(), is(2));
        assertThat(memberQueryResults.getTotal(), is(4L));
        assertThat(memberQueryResults.getOffset(), is(1L));
        assertThat(memberQueryResults.getLimit(), is(2L));
    }

    @Test
    public void aggregation() {
        List<Tuple> result = queryFactory
            .select(
                member.count(),
                member.age.sum(),
                member.age.avg(),
                member.age.max(),
                member.age.min()
            )
            .from(member)
            .fetch();

        Tuple tuple = result.get(0);
        assertThat(tuple.get(member.count()), is(4L));
        assertThat(tuple.get(member.age.sum()), is(100));
        assertThat(tuple.get(member.age.avg()), is(25.0));
        assertThat(tuple.get(member.age.max()), is(40));
        assertThat(tuple.get(member.age.min()), is(10));
    }

    @Test
    public void groupby() throws Exception {
        List<Tuple> result = queryFactory
            .select(team.name, member.age.avg())
            .from(member)
            .join(member.team, team)
            .groupBy(team.name)
            .fetch();

        Tuple teamA = result.get(0);
        Tuple teamB = result.get(1);

        assertThat(teamA.get(team.name), is("TeamA"));
        assertThat(teamA.get(member.age.avg()), is(15.0));

        assertThat(teamB.get(team.name), is("TeamB"));
        assertThat(teamB.get(member.age.avg()), is(35.0));

    }

    @Test
    public void join() {
        List<Member> result = queryFactory
            .selectFrom(member)
            .join(member.team, team)
            .where(team.name.eq("TeamA"))
            .fetch();

        Assertions.assertThat(result)
            .extracting("username")
            .containsExactly("Member1", "Member2");
    }

    @Test
    public void Theta_join() {
        em.persist(new Member("TeamA"));
        em.persist(new Member("TeamB"));

        List<Member> result = queryFactory
            .select(member)
            .from(member, team)
            .where(member.username.eq(team.name))
            .fetch();

        Assertions.assertThat(result)
            .extracting("username")
            .containsExactly("TeamA", "TeamB");
    }

    @Test
    public void join_on_filtering() {
        List<Tuple> result = queryFactory
            .select(member, team)
            .from(member)
            .leftJoin(member.team, team)
            .where(team.name.eq("TeamA"))
            .fetch();

        for (Tuple tuple : result) {
            System.out.println(tuple);
        }
    }

    @Test
    public void join_on_no_relation() {
        em.persist(new Member("TeamA"));
        em.persist(new Member("TeamB"));
        em.persist(new Member("TeamC"));

        List<Tuple> result = queryFactory
            .select(member, team)
            .from(member)
            .leftJoin(team).on(member.username.eq(team.name))
            .fetch();

        for (Tuple tuple : result) {
            System.out.println(tuple);
        }
    }

    @Test
    public void fetchJoinNo() {
        em.flush();
        em.clear();

        Member member = queryFactory
            .selectFrom(QMember.member)
            .join(QMember.member.team, team)
            .where(QMember.member.username.eq("Member1"))
            .fetchOne();

        boolean loaded = emf.getPersistenceUnitUtil().isLoaded(member.getTeam());
        assertThat(loaded, is(true));
    }

    @Test
    public void fetchJoinUse() {
        em.flush();
        em.clear();

        Member member = queryFactory
            .selectFrom(QMember.member)
            .join(QMember.member.team, team).fetchJoin()
            .where(QMember.member.username.eq("Member1"))
            .fetchOne();

        boolean loaded = emf.getPersistenceUnitUtil().isLoaded(member.getTeam());
        assertThat(loaded, is(true));
    }

    @Test
    public void subQuery() {
        QMember memberSub = new QMember("memberSub");

        List<Member> result = queryFactory
            .selectFrom(member)
            .where(
                member.age.eq(
                    JPAExpressions.select(memberSub.age.max()).from(memberSub)
                )
            )
            .fetch();

        Assertions.assertThat(result).extracting("age").containsExactly(40);
    }

    // 나이가 평균 이상인 회원 조회
    @Test
    public void subQueryGoe() {
        QMember memberSub = new QMember("memberSub");

        List<Member> result = queryFactory
            .selectFrom(member)
            .where(member.age.goe(
                JPAExpressions.select(memberSub.age.avg()).from(memberSub)
            ))
            .fetch();

        Assertions.assertThat(result).extracting("age").containsExactly(30, 40);
    }

    @Test
    public void subQueryIn() {
        QMember memberSub = new QMember("memberSub");

        List<Member> result = queryFactory
            .selectFrom(member)
            .where(member.age.in(
                JPAExpressions.select(memberSub.age).from(memberSub).where(memberSub.age.gt(10))
            ))
            .fetch();

        Assertions.assertThat(result).extracting("age").containsExactly(20, 30, 40);
    }

    @Test
    public void selectSubQuery() {
        QMember memberSub = new QMember("memberSub");

        List<Tuple> result = queryFactory
            .select(
                member.username,
                JPAExpressions.select(memberSub.age.avg()).from(memberSub))
            .from(member)
            .fetch();

        for (Tuple tuple : result) {
            System.out.println(tuple);
        }
    }

    @Test
    public void basicCase() {
        List<String> result = queryFactory
            .select(member.age
                .when(10).then("열살")
                .when(20).then("스무살")
                .otherwise("기타"))
            .from(member)
            .fetch();

        for (String s : result) {
            System.out.println(s);
        }
    }

    @Test
    public void complexCase() {
        List<String> result = queryFactory
            .select(
                new CaseBuilder()
                    .when(member.age.between(0, 20)).then("0 ~ 20살")
                    .when(member.age.between(21, 30)).then("21살 ~ 30살")
                    .otherwise("기타")
            )
            .from(member)
            .fetch();

        for (String s : result) {
            System.out.println(s);
        }
    }

    @Test
    public void constant() {
        List<Tuple> result = queryFactory
            .select(member.username, Expressions.constant("A"))
            .from(member)
            .fetch();

        for (Tuple tuple : result) {
            System.out.println(tuple);
        }
    }

    @Test
    public void concat() {
        List<String> result = queryFactory
            .select(member.username.concat("_").concat(member.age.stringValue()))
            .from(member)
            .where(member.username.eq("Member1"))
            .fetch();

        for (String s : result) {
            System.out.println(s);
        }
    }

    @Test
    public void projectionTypeOne() {
        List<Integer> result = queryFactory
            .select(member.age)
            .from(member)
            .fetch();
    }

    @Test
    public void tupleProjection() {
        List<Tuple> result = queryFactory
            .select(member.age, member.username)
            .from(member)
            .fetch();

        for (Tuple tuple : result) {
            String username = tuple.get(member.username);
            Integer age = tuple.get(member.age);
            System.out.println("username = " + username);
            System.out.println("age = " + age);
        }
    }

    @Test
    public void findDtoByJPQL() {
        List<MemberDto> result = em.createQuery(""
            + "SELECT "
            + "     new study.querydsl.controller.dto.MemberDto(m.username, m.age) "
            + "FROM "
            + "     Member m", MemberDto.class).getResultList();

        for (MemberDto memberDto : result) {
            System.out.println("memberDto = " + memberDto);
        }
    }

    @Test
    public void findDtoBySetter() {
        List<MemberDto> result = queryFactory
            .select(Projections.bean(
                MemberDto.class,
                member.username,
                member.age)
            )
            .from(member)
            .fetch();

        for (MemberDto memberDto : result) {
            System.out.println("memberDto = " + memberDto);
        }
    }

    @Test
    public void findDtoByField() {
        List<MemberDto> result = queryFactory
            .select(Projections.fields(
                MemberDto.class,
                member.age,
                member.username)
            )
            .from(member)
            .fetch();

        for (MemberDto memberDto : result) {
            System.out.println("memberDto = " + memberDto);
        }
    }

    @Test
    public void findDtoByConstructor() {
        List<MemberDto> result = queryFactory
            .select(Projections.constructor(
                MemberDto.class,
                member.username,
                member.age)
            )
            .from(member)
            .fetch();

        for (MemberDto memberDto : result) {
            System.out.println("memberDto = " + memberDto);
        }
    }

    @Test
    public void findUserDtoByField() {
        QMember memberSub = new QMember("memberSub");

        List<UserDto> result = queryFactory
            .select(Projections.fields(
                    UserDto.class,
                    member.username.as("name"),
                    ExpressionUtils.as(
                        JPAExpressions.select(memberSub.age.max()).from(memberSub), "age"
                    )
                )
            )
            .from(member)
            .fetch();

        for (UserDto userDto : result) {
            System.out.println("userDto = " + userDto);
        }
    }


    @Test
    public void findUserDtoByConstructor() {
        QMember memberSub = new QMember("memberSub");

        List<UserDto> result = queryFactory
            .select(Projections.constructor(
                    UserDto.class,
                    member.username.as("name"),
                    JPAExpressions.select(memberSub.age.max()).from(memberSub)
                )
            )
            .from(member)
            .fetch();

        for (UserDto userDto : result) {
            System.out.println("userDto = " + userDto);
        }
    }

    @Test
    public void findDtoByQueryProjection() {
        List<MemberDto> result = queryFactory
            .select(new QMemberDto(member.username, member.age))
            .from(member)
            .fetch();

        for (MemberDto memberDto : result) {
            System.out.println("memberDto = " + memberDto);
        }
    }
}
