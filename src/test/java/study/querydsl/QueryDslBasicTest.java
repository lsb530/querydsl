package study.querydsl;

import static com.querydsl.jpa.JPAExpressions.*;
import static org.assertj.core.api.Assertions.*;
import static study.querydsl.entity.QMember.*;
import static study.querydsl.entity.QTeam.*;

import java.util.List;

import javax.persistence.EntityManager;
import javax.persistence.EntityManagerFactory;
import javax.persistence.PersistenceUnit;

import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.annotation.Commit;
import org.springframework.transaction.annotation.Transactional;

import com.querydsl.core.BooleanBuilder;
import com.querydsl.core.QueryResults;
import com.querydsl.core.Tuple;
import com.querydsl.core.types.ExpressionUtils;
import com.querydsl.core.types.Predicate;
import com.querydsl.core.types.Projections;
import com.querydsl.core.types.dsl.BooleanExpression;
import com.querydsl.core.types.dsl.CaseBuilder;
import com.querydsl.core.types.dsl.Expressions;
import com.querydsl.jpa.JPAExpressions;
import com.querydsl.jpa.impl.JPAQueryFactory;

import study.querydsl.dto.MemberDto;
import study.querydsl.dto.QMemberDto;
import study.querydsl.dto.UserDto;
import study.querydsl.entity.Member;
import study.querydsl.entity.QMember;
import study.querydsl.entity.QTeam;
import study.querydsl.entity.Team;

@SpringBootTest
@Transactional
public class QueryDslBasicTest {

  @Autowired
  EntityManager em; //멀티스레드환경에서도 동시성문제없이 동작하도록 되어있음

  JPAQueryFactory queryFactory; //em과 동일

  @BeforeEach
  public void before() {
    queryFactory = new JPAQueryFactory(em);
    Team teamA = new Team("teamA");
    Team teamB = new Team("teamB");
    em.persist(teamA);
    em.persist(teamB);
    Member member1 = new Member("member1", 10, teamA);
    Member member2 = new Member("member2", 20, teamA);
    Member member3 = new Member("member3", 30, teamB);
    Member member4 = new Member("member4", 40, teamB);
    em.persist(member1);
    em.persist(member2);
    em.persist(member3);
    em.persist(member4);
  }

  @Test
  public void startJPQL() { //런타임오류로만 찾아낼 수 있음
    //member1을 찾아라.
    String qlString = "select m from Member m where m.username = :username";
    Member findMember = em.createQuery(qlString, Member.class)
        .setParameter("username", "member1")
        .getSingleResult();
    assertThat(findMember.getUsername()).isEqualTo("member1");
  }

  @Test
  public void startQueryDsl() { //컴파일오류로도 찾아낼 수 있음
    // QMember m = new QMember("m");
    Member findMember = queryFactory
        .select(member)
        .from(member)
        .where(member.username.eq("member1")) //파라미터 바인딩 처리
        .fetchOne();

    assertThat(findMember.getUsername()).isEqualTo("member1");
  }

  @Test
  public void search() {
    Member findMember = queryFactory
        .selectFrom(member)
        .where(member.username.eq("member1")
            .and(member.age.eq(10)))
        .fetchOne();

    assertThat(findMember.getUsername()).isEqualTo("member1");
  }

  @Test
  public void searchAndParam() {
    Member findMember = queryFactory
        .selectFrom(member)
        .where( //where문이 predicate<...>로 들어오기때문에 ,로 가능하다.
            member.username.eq("member1"),
            member.age.between(10, 30)
        )
        .fetchOne();

    assertThat(findMember.getUsername()).isEqualTo("member1");
  }

  @Test
  public void resultFetch() {
    // List<Member> fetch = queryFactory
    // 	.selectFrom(member)
    // 	.fetch();

    // Member fetchOne = queryFactory
    // 	.selectFrom(member)
    // 	.fetchOne();

    // Member fetchFirst = queryFactory
    // 	.selectFrom(member)
    // 	.fetchFirst();

    // QueryResults<Member> results = queryFactory
    // 	.selectFrom(member)
    // 	.fetchResults();
    //
    // results.getTotal();
    // List<Member> content = results.getResults();

    long total = queryFactory
        .selectFrom(member)
        .fetchCount();
  }

  /**
   * 회원 정렬 순서 1. 회원 나이 내림차순(desc) 2. 회원 이름 올림차순(asc) 단 2에서 회원 이름이 없으면 마지막에 출력(nulls last)
   */
  @Test
  public void sort() {
    em.persist(new Member(null, 100));
    em.persist(new Member("member5", 100));
    em.persist(new Member("member6", 100));

    List<Member> result = queryFactory
        .selectFrom(member)
        .where(member.age.eq(100))
        .orderBy(member.age.desc(), member.username.asc().nullsLast())
        .fetch();

    Member member5 = result.get(0);
    Member member6 = result.get(1);
    Member memberNull = result.get(2);
    assertThat(member5.getUsername()).isEqualTo("member5");
    assertThat(member6.getUsername()).isEqualTo("member6");
    assertThat(memberNull.getUsername()).isNull();
  }

  @Test
  public void paging1() {
    List<Member> result = queryFactory
        .selectFrom(member)
        .orderBy(member.username.desc())
        .offset(1) //앞에 몇개를 스킵할건지
        .limit(2) //가져올 데이터수
        .fetch();

    assertThat(result.size()).isEqualTo(2);
  }

  @Test // 전체 조회가 필요한 경우
  public void paging2() {
    QueryResults<Member> queryResults = queryFactory
        .selectFrom(member)
        .orderBy(member.username.desc())
        .offset(1) //앞에 몇개를 스킵할건지
        .limit(2) //가져올 데이터수
        .fetchResults();

    assertThat(queryResults.getTotal()).isEqualTo(4);
    assertThat(queryResults.getLimit()).isEqualTo(2);
    assertThat(queryResults.getOffset()).isEqualTo(1);
    assertThat(queryResults.getResults().size()).isEqualTo(2);
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
    assertThat(tuple.get(member.count())).isEqualTo(4);
    assertThat(tuple.get(member.age.sum())).isEqualTo(100);
    assertThat(tuple.get(member.age.avg())).isEqualTo(25);
    assertThat(tuple.get(member.age.max())).isEqualTo(40);
    assertThat(tuple.get(member.age.min())).isEqualTo(10);
  }

  /**
   * 팀의 이름과 각 팀의 평균 연령을 구해라.
   *
   * @author Boki
   * @since 2021-04-13 오전 10:42
   */
  @Test
  public void group() throws Exception {
    List<Tuple> result = queryFactory
        .select(team.name, member.age.avg())
        .from(member)
        .join(member.team, team)
        .groupBy(team.name)
        // .having()
        .fetch();

    Tuple teamA = result.get(0);
    Tuple teamB = result.get(1);

    assertThat(teamA.get(team.name)).isEqualTo("teamA");
    assertThat(teamA.get(member.age.avg())).isEqualTo(15); //(10+20) / 2

    assertThat(teamB.get(team.name)).isEqualTo("teamB");
    assertThat(teamB.get(member.age.avg())).isEqualTo(35); //(30+40) / 2
  }

  /**
   * 팀 A에 소속된 모든 회원
   */
  @Test
  public void join() {
    List<Member> result = queryFactory
        .selectFrom(member)
        .join(member.team, team)
        // .leftJoin(member.team, team)
        .where(team.name.eq("teamA"))
        .fetch();

    assertThat(result)
        .extracting("username")
        .containsExactly("member1", "member2");
  }

  /**
   * 세타 조인(연관관계가 없어도 조인할수있음) 회원의 이름이 팀 이름과 같은 회원 조회
   */
  @Test
  public void theta_join() {
    em.persist(new Member("teamA"));
    em.persist(new Member("teamB"));
    em.persist(new Member("teamC"));

    List<Member> result = queryFactory
        .select(member)
        .from(member, team)
        .where(member.username.eq(team.name))
        .fetch();

    assertThat(result)
        .extracting("username")
        .containsExactly("teamA", "teamB");
  }

  /**
   * 예) 회원과 팀을 조인하면서, 팀 이름이 teamA인 팀만 조인, 회원은 모두 조회 JQPL: select m, t from Member m left join m.team
   * t on t.name = 'teamA'
   */
  @Test
  public void join_on_filtering() {
    List<Tuple> result = queryFactory
        .select(member, team)
        .from(member)
        // .join(member.team, team)
        .leftJoin(member.team, team).on(team.name.eq("teamA"))
        // .where(team.name.eq("teamA"))
        .fetch();

    for (Tuple tuple : result) {
      System.out.println("tuple = " + tuple);
    }
  }

  /**
   * 연관관계 없는 엔티티 외부조인 회원의 이름이 팀 이름과 같은 대상 외부 조인
   */
  @Test
  public void join_on_no_relation() {
    em.persist(new Member("teamA"));
    em.persist(new Member("teamB"));
    em.persist(new Member("teamC"));

    List<Tuple> result = queryFactory
        .select(member, team)
        .from(member)
        // .leftJoin(team).on(member.username.eq(team.name)) //보통은 leftJoin(member.team, team)이런식으로 쓴다
        .join(team).on(member.username.eq(team.name))
        .fetch();

    for (Tuple tuple : result) {
      System.out.println("tuple = " + tuple);
    }
  }

  @PersistenceUnit
  EntityManagerFactory emf; // 이미 로딩된 객체인지 아닌지 검증하기 위해 씀

  @Test
  public void fetchJoinNo() {
    //fetch조인할때는 데이터를 동기화시키고 영속성 컨텍스트를 날려야 깔끔하게 실행된다.
    em.flush();
    em.clear();

    Member findMember = queryFactory //LAZY로 만들어놔서 DB에서는 team은 조회 안된다
        .selectFrom(member)
        .where(member.username.eq("member1"))
        .fetchOne();

    boolean loaded = emf.getPersistenceUnitUtil().isLoaded(findMember.getTeam());
    assertThat(loaded).as("페치 조인 미적용").isFalse();
  }

  @Test
  public void fetchJoinUse() {
    //fetch조인할때는 데이터를 동기화시키고 영속성 컨텍스트를 날려야 깔끔하게 실행된다.
    em.flush();
    em.clear();

    Member findMember = queryFactory //LAZY로 만들어놔서 DB에서는 team은 조회 안된다
        .selectFrom(member)
        .join(member.team, team).fetchJoin()
        .where(member.username.eq("member1"))
        .fetchOne();

    boolean loaded = emf.getPersistenceUnitUtil().isLoaded(findMember.getTeam());
    assertThat(loaded).as("페치 조인 적용").isTrue();
  }

  /**
   * 나이가 가장 많은 회원 조회
   */
  @Test
  public void subQuery() {

    QMember memberSub = new QMember("memberSub"); //alias를 겹치지 않게하기위해 새로 생성해줌

    List<Member> result = queryFactory
        .selectFrom(member)
        .where(member.age.eq(
            select(memberSub.age.max())
                .from(memberSub)
        ))
        .fetch();
    assertThat(result).extracting("age")
        .containsExactly(40);
  }

  /**
   * 나이가 평균 이상인 회원
   */
  @Test
  public void subQueryGoe() {

    QMember memberSub = new QMember("memberSub"); //alias를 겹치지 않게하기위해 새로 생성해줌

    List<Member> result = queryFactory
        .selectFrom(member)
        .where(member.age.goe(
            select(memberSub.age.avg())
                .from(memberSub)
        ))
        .fetch();
    assertThat(result).extracting("age")
        .containsExactly(30, 40);
  }

  /**
   * 나이가 10살보다 초과하는 영역에 속하는 회원
   */
  @Test
  public void subQueryIn() {

    QMember memberSub = new QMember("memberSub"); //alias를 겹치지 않게하기위해 새로 생성해줌

    List<Member> result = queryFactory
        .selectFrom(member)
        .where(member.age.in(
            select(memberSub.age)
                .from(memberSub)
                .where(memberSub.age.gt(10))
        ))
        .fetch();
    assertThat(result).extracting("age")
        .containsExactly(20, 30, 40);
  }

  @Test
  public void selectSubQuery() {

    QMember memberSub = new QMember("memberSub"); //alias를 겹치지 않게하기위해 새로 생성해줌

    List<Tuple> result = queryFactory
        .select(member.username,
            select(memberSub.age.avg())
                .from(memberSub))
        .from(member)
        .fetch();
    for (Tuple tuple : result) {
      System.out.println("tuple = " + tuple);
    }
  }

  //여기 밑에 부분들은 프레젠테이션계층을 위해서 하는부분이라
  //DB는 DB안의 데이터를 최적화해서 내려줘야되지. 이건 프레젠테이션 부분의 영역이라 생각함
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
      System.out.println("s = " + s);
    }
  }

  @Test
  public void complexCase() {
    List<String> result = queryFactory
        .select(new CaseBuilder()
            .when(member.age.between(0, 20)).then("0~20살")
            .when(member.age.between(21, 30)).then("21~30살")
            .otherwise("기타"))
        .from(member)
        .fetch();
    for (String s : result) {
      System.out.println("s = " + s);
    }
  }

  //상수, 문자 더하기
  @Test
  public void constant() {
    List<Tuple> result = queryFactory
        .select(member.username, Expressions.constant("A"))
        .from(member)
        .fetch();
    for (Tuple tuple : result) {
      System.out.println("tuple = " + tuple);
    }
  }

  @Test
  public void concat() {

    //{username}_{age}
    List<String> result = queryFactory
        .select(member.username.concat("_").concat(member.age.stringValue()))
        .from(member)
        .where(member.username.eq("member1"))
        .fetch();

    for (String s : result) {
      System.out.println("s = " + s);
    }
  }

  //프로젝션 단일
  @Test
  public void simpleProjection() {
    List<String> result = queryFactory //List<Member>도 프로젝션 대상 1개
        .select(member.username)
        .from(member)
        .fetch();

    for (String s : result) {
      System.out.println("s = " + s);
    }
  }

  //프로젝션 튜플은 repository까지만 쓰고, service단까지 끌여들이진 않는게 좋다. => DTO로
  @Test
  public void tupleProjection() {
    List<Tuple> result = queryFactory
        .select(member.username, member.age)
        .from(member)
        .fetch();

    for (Tuple tuple : result) {
      String username = tuple.get(member.username);
      Integer age = tuple.get(member.age);
      System.out.println("username = " + username);
      System.out.println("age = " + age);
    }
  }

  //DTO JPQL
  @Test
  public void findDtoByJPQL() {
    // em.createQuery("select m from Member m", MemberDto.class); // 타입이 안맞기때문에 new opertation을 써야된다.
    List<MemberDto> result = em.createQuery(
        "select new study.querydsl.dto.MemberDto(m.username, m.age) from Member m",
        MemberDto.class) // 타입이 안맞기때문에 new opertation을 써야된다.
        .getResultList();
    for (MemberDto memberDto : result) {
      System.out.println("memberDto = " + memberDto);
    }
  }

  //DTO queryDSL
  @Test
  public void findDtoBySetter() { //property(Setter) 접근방식=>@Data가 붙어있어야 작동함(setter)
    List<MemberDto> result = queryFactory
        .select(Projections.bean(MemberDto.class, //QBean.newInstance 에러 => 기본 생성자가 없어서
            member.username,
            member.age))
        .from(member)
        .fetch();
    for (MemberDto memberDto : result) {
      System.out.println("memberDto = " + memberDto);
    }
  }

  @Test
  public void findDtoByField() { //field 접근방식(롬복이 없어도 가능)
    List<MemberDto> result = queryFactory
        .select(Projections.fields(MemberDto.class, //QBean.newInstance 에러 => 기본 생성자가 없어서
            member.username,
            member.age))
        .from(member)
        .fetch();
    for (MemberDto memberDto : result) {
      System.out.println("memberDto = " + memberDto);
    }
  }

  @Test
  public void findDtoByConstructor() { //Constructor 방식=> 타입들이 모두 일치해야된다
    List<MemberDto> result = queryFactory
        .select(Projections.constructor(MemberDto.class, //QBean.newInstance 에러 => 기본 생성자가 없어서
            member.username, //타입일치필요
            member.age))
        .from(member)
        .fetch();
    for (MemberDto memberDto : result) {
      System.out.println("memberDto = " + memberDto);
    }
  }

  @Test
  public void findUserDto() { //자주 쓸 일은 없다(이름을 맞춰주자)
    QMember memberSub = new QMember("memberSub");
    List<UserDto> result = queryFactory
        .select(Projections.fields(UserDto.class, //QBean.newInstance 에러 => 기본 생성자가 없어서
            member.username.as("name"), //필드명이 맞아야 동작한다

            ExpressionUtils.as(JPAExpressions // 서브쿼리
                .select(memberSub.age.max())
                .from(memberSub), "age")))
        .from(member)
        .fetch();
    for (UserDto userDto : result) {
      System.out.println("userDto = " + userDto);
    }
  }

  @Test
  public void findUserDtoByConstructor() { //자주 쓸 일은 없다(이름을 맞춰주자)
    List<UserDto> result = queryFactory
        .select(Projections.constructor(UserDto.class, //QBean.newInstance 에러 => 기본 생성자가 없어서
            member.username,
            // member.id //런타임으로만 오류를 찾아낸다.
            member.age))
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

  //동적쿼리
  @Test
  public void dynamicQuery_BooleanBuilder() {
    String usernameParam = "member1";
    Integer ageParam = 10;

    List<Member> result = searchMember1(usernameParam, ageParam);
    assertThat(result.size()).isEqualTo(1);
  }

  private List<Member> searchMember1(String usernameCond, Integer ageCond) {
    BooleanBuilder builder = new BooleanBuilder();
    if (usernameCond != null) {
      builder.and(member.username.eq(usernameCond));
    }

    if (ageCond != null) {
      builder.and(member.age.eq(ageCond));
    }

    return queryFactory
        .selectFrom(member)
        .where(builder)
        .fetch();
  }

  @Test
  public void dynamicQuery_WhereParam() {
    String usernameParam = "member1";
    Integer ageParam = 10;

    List<Member> result = searchMember2(usernameParam, ageParam);
    assertThat(result.size()).isEqualTo(1);
  }

  private List<Member> searchMember2(String usernameCond, Integer ageCond) {
    return queryFactory
        .selectFrom(member)
        .where(usernameEq(usernameCond), ageEq(ageCond))
//			 .where(allEq(usernameCond,ageCond))
        .fetch();
  }

  private BooleanExpression usernameEq(String usernameCond) {
    return usernameCond != null ? member.username.eq(usernameCond) : null;
  }

  private BooleanExpression ageEq(Integer ageCond) {
    return ageCond != null ? member.age.eq(ageCond) : null;
  }

  //광고 샅애 isValid, 날짜가 IN: isServicable
  private BooleanExpression allEq(String usernameCond, Integer ageCond) {
    return usernameEq(usernameCond).and(ageEq(ageCond));
  }

  @Test
  @Commit
  public void bulkUpdate() {

    //member1 = 10 -> member1
    //member2 = 20 -> member2
    //member3 = 30 -> member3
    //member4 = 40 -> member4

    long count = queryFactory
        .update(member)
        .set(member.username, "비회원")
        .where(member.age.lt(28))
        .execute();

    em.flush();
    em.clear();

    //1 member1 = 10 -> 비회원
    //2 member2 = 20 -> 비회원
    //3 member3 = 30 -> member3
    //4 member4 = 40 -> member4

    List<Member> result = queryFactory
        .selectFrom(member)
        .fetch();
    for (Member member1 : result) {
      System.out.println("member = " + member1);
    }
  }

  @Test
  public void bulkAdd() {
    long count = queryFactory
        .update(member)
        .set(member.age, member.age.add(1))
        .execute();
  }

  @Test
  public void bukdDelete() {
    long count = queryFactory
        .delete(member)
        .where(member.age.gt(18))
        .execute();
  }

  @Test
  public void sqlFunction() {
    List<String> result = queryFactory
        .select(Expressions.stringTemplate(
            "function('replace', {0}, {1}, {2})",
            member.username, "member", "M"))
        .from(member)
        .fetch();
    for (String s : result) {
      System.out.println("s = " + s);
    }
  }

  @Test
  public void sqlFunction2() {
    List<String> result = queryFactory
        .select(member.username)
        .from(member)
//        .where(member.username
//            .eq(Expressions.stringTemplate("function('lower', {0})", member.username)))
        .where(member.username.eq(member.username.lower()))
        .fetch();
    for (String s : result) {
      System.out.println("s = " + s);
    }
  }

}