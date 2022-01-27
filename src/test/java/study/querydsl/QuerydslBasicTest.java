package study.querydsl;

import com.querydsl.core.QueryResults;
import com.querydsl.core.Tuple;
import com.querydsl.core.types.dsl.Wildcard;
import com.querydsl.jpa.impl.JPAQueryFactory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;
import study.querydsl.entity.Member;
import study.querydsl.entity.QMember;
import study.querydsl.entity.QTeam;
import study.querydsl.entity.Team;

import javax.persistence.EntityManager;
import javax.persistence.TupleElement;

import java.util.List;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.nullValue;
import static study.querydsl.entity.QMember.member;
import static study.querydsl.entity.QTeam.team;

@SpringBootTest
@Transactional
public class QuerydslBasicTest {

    @Autowired
    EntityManager em;

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
            .having(member.age.avg().gt(20))
            .fetch();

        Tuple teamA = result.get(0);
        Tuple teamB = result.get(1);

        assertThat(teamA.get(team.name), is("TeamA"));
        assertThat(teamA.get(member.age.avg()), is(5));

        assertThat(teamB.get(team.name), is("TeamB"));
        assertThat(teamB.get(member.age.avg()), is(35));

    }
}
