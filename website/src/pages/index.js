import Link from '@docusaurus/Link';
import Layout from '@theme/Layout';
import styles from './index.module.css';

const documents = [
  {
    number: '01',
    tag: 'SYSTEM',
    title: 'Batch Architecture',
    description: '거래일의 시작부터 EOD와 랭킹 확정까지, 전체 실행 순서와 장애 전파를 추적합니다.',
    link: '/docs/batch/BATCH_ARCHITECTURE',
    accent: 'gold',
  },
  {
    number: '02',
    tag: 'DOMAIN',
    title: 'Ranking Service',
    description: '이벤트 투영, 일별 순위 확정, Redis fallback과 멱등성 경계를 설명합니다.',
    link: '/docs/ranking/RANKING_SERVICE',
    accent: 'violet',
  },
  {
    number: '03',
    tag: 'DOMAIN',
    title: 'Trading Service',
    description: '주문과 예약, 체결, 계좌 상태가 서비스 내부에서 연결되는 흐름을 확인합니다.',
    link: '/docs/TRADING_SERVICE',
    accent: 'coral',
  },
];

const principles = [
  ['01', 'Service-owned data', '각 서비스가 자신의 상태와 트랜잭션을 소유하고 다른 서비스 DB를 직접 읽지 않습니다.'],
  ['02', 'Explicit contracts', '동기 호출은 gRPC, 비동기 변경 전파는 Kafka 이벤트로 계약을 분리합니다.'],
  ['03', 'Safe by design', '멱등성 키와 Outbox, 재시작 가능한 Batch로 중복과 부분 실패를 통제합니다.'],
];

const projectSummary = [
  {
    label: 'WHY',
    title: '흩어진 서비스의 맥락을 한곳으로',
    description: '13개 도메인 서비스의 설계와 운영 지식을 연결해, 새로운 팀원도 전체 흐름을 빠르게 이해합니다.',
  },
  {
    label: 'HOW',
    title: '계약과 이벤트로 연결된 구조',
    description: '서비스는 독립적인 데이터를 소유하고 gRPC, Kafka와 Outbox로 안전하게 협력합니다.',
  },
  {
    label: 'RESULT',
    title: '실행 가능한 엔지니어링 문서',
    description: '아키텍처 그림에서 실제 RPC, 테이블, 테스트와 장애 복구 방법까지 바로 추적할 수 있습니다.',
  },
];

const serviceGroups = [
  {
    index: 'A',
    label: 'INVESTMENT CORE',
    title: '투자 판단부터 자산 성과까지',
    description: '시세 탐색, 주문 체결, 보유 자산과 랭킹으로 이어지는 핵심 투자 흐름을 담당합니다.',
    services: [
      ['MARKET', '실시간 시장', '현재가·호가·시장 상태·종목 랭킹과 실시간 시세 스트림'],
      ['STOCK', '종목과 차트', '종목 마스터 검색, 일봉·분봉 차트, 전일 종가와 데이터 동기화'],
      ['TRADING', '주문과 계좌', '잔고 조회, 주문·취소·정정, 예약 주문과 체결 처리'],
      ['PORTFOLIO', '보유 자산', '실시간 보유 수량, 자산 요약, EOD 스냅샷과 수익률 히스토리'],
      ['RANKING', '성과 순위', '일별 수익률 랭킹 확정, TOP 순위와 내 순위 조회'],
      ['WISHLIST', '관심 종목', '관심 종목 추가·삭제·조회와 가격 알림 흐름'],
      ['NEWS', '투자 뉴스', '종목별 뉴스 수집과 조회를 위한 콘텐츠 연결'],
    ],
  },
  {
    index: 'B',
    label: 'EXPERIENCE PLATFORM',
    title: '사용자의 성장과 참여 경험',
    description: '인증과 프로필부터 학습, 미션, 알림과 커뮤니티까지 투자 경험의 외연을 담당합니다.',
    services: [
      ['AUTH', '인증과 토큰', 'OAuth 로그인, 사용자 인증 정보와 서비스 접근 토큰'],
      ['USER', '사용자 프로필', '내 정보 조회, 닉네임과 프로필 변경 및 상태 관리'],
      ['LEARNING', '투자 학습', '학습 콘텐츠 탐색·추천, 진도·완료·즐겨찾기와 학습 통계'],
      ['MISSION', '미션과 보상', '미션 목록, 참여와 보상 수령을 위한 도메인 계약'],
      ['NOTIFICATION', '알림 전달', '기기 토큰, 알림 생성·조회·읽음 처리와 FCM 전달 상태'],
      ['CHATTING', '실시간 소통', 'WebSocket 기반 채팅방, 메시지와 사용자 연결'],
    ],
  },
];

const productJourneys = [
  {
    number: '01',
    title: 'Discover',
    korean: '시장을 발견합니다',
    description: 'Market과 Stock의 시세·차트에 News와 Wishlist를 결합해 투자 대상을 탐색합니다.',
    path: 'MARKET → STOCK → NEWS → WISHLIST',
    color: 'mintJourney',
  },
  {
    number: '02',
    title: 'Invest',
    korean: '거래하고 자산을 기록합니다',
    description: 'Trading이 주문과 체결을 소유하고 Portfolio가 이벤트를 반영해 보유 자산을 구성합니다.',
    path: 'TRADING → EVENT → PORTFOLIO',
    color: 'coralJourney',
  },
  {
    number: '03',
    title: 'Measure',
    korean: '성과를 측정합니다',
    description: '장 마감 후 EOD 자산을 확정하고 Ranking이 같은 거래일의 성과 순위를 생성합니다.',
    path: 'BATCH → EOD → RANKING',
    color: 'violetJourney',
  },
  {
    number: '04',
    title: 'Engage',
    korean: '배우고 함께 성장합니다',
    description: 'Learning과 Mission, Chatting, Notification이 지속적인 참여 경험을 연결합니다.',
    path: 'LEARNING → MISSION → COMMUNITY',
    color: 'goldJourney',
  },
];

export default function Home() {
  return (
    <Layout
      title="Engineering Documentation"
      description="Candle 마이크로서비스의 아키텍처, 도메인 흐름과 운영 문서">
      <main className={styles.main}>
        <section className={styles.hero}>
          <div className={styles.heroGlow} />
          <div className={styles.heroGrid} />
          <div className={styles.heroContent}>
            <div className={styles.statusLine}>
              <span className={styles.statusDot} />
              ENGINEERING KNOWLEDGE BASE
              <span className={styles.statusVersion}>V1.0</span>
            </div>
            <h1>
              투자 경험을 연결하는
              <span>Candle의 설계 지도.</span>
            </h1>
            <p className={styles.heroDescription}>
              주문 한 건이 체결되고, 자산이 기록되며, 랭킹으로 이어지는 모든 흐름.
              실제 코드와 운영 정책을 하나의 살아있는 문서로 연결합니다.
            </p>
            <div className={styles.actions}>
              <Link className={styles.primaryAction} to="/docs/batch/BATCH_ARCHITECTURE">
                전체 아키텍처 보기 <span aria-hidden="true">↗</span>
              </Link>
              <Link className={styles.secondaryAction} to="/docs/CONVENTIONS">
                구현 규칙 살펴보기 <span aria-hidden="true">→</span>
              </Link>
            </div>
          </div>

          <div className={styles.systemMap} aria-label="Candle 핵심 서비스 연결 구조">
            <div className={styles.mapHeader}>
              <span>LIVE SYSTEM MAP</span>
              <span>SEOUL · KST</span>
            </div>
            <div className={styles.orbit}>
              <div className={`${styles.serviceNode} ${styles.tradingNode}`}>
                <small>01 · COMMAND</small>
                <strong>TRADING</strong>
                <span>orders · fills</span>
              </div>
              <div className={`${styles.serviceNode} ${styles.stockNode}`}>
                <small>02 · MARKET</small>
                <strong>STOCK</strong>
                <span>quotes · candles</span>
              </div>
              <div className={`${styles.serviceNode} ${styles.portfolioNode}`}>
                <small>03 · READ MODEL</small>
                <strong>PORTFOLIO</strong>
                <span>holdings · EOD</span>
              </div>
              <div className={`${styles.serviceNode} ${styles.rankingNode}`}>
                <small>04 · INSIGHT</small>
                <strong>RANKING</strong>
                <span>history · cache</span>
              </div>
              <div className={styles.orbitLineOne} />
              <div className={styles.orbitLineTwo} />
              <div className={styles.core}>
                <span className={styles.flame}>◈</span>
                <strong>CANDLE</strong>
                <small>EVENT CORE</small>
              </div>
            </div>
            <div className={styles.mapFooter}>
              <span><i className={styles.grpcDot} /> gRPC</span>
              <span><i className={styles.kafkaDot} /> Kafka / Outbox</span>
              <span><i className={styles.batchDot} /> Spring Batch</span>
            </div>
          </div>
        </section>

        <section className={styles.metrics} aria-label="프로젝트 기술 요약">
          <div><strong>13</strong><span>DOMAIN SERVICES</span></div>
          <div><strong>gRPC</strong><span>SYNCHRONOUS CONTRACT</span></div>
          <div><strong>KAFKA</strong><span>EVENT &amp; OUTBOX</span></div>
          <div><strong>BATCH 6</strong><span>DAILY ORCHESTRATION</span></div>
        </section>

        <section id="overview" className={styles.summarySection} aria-labelledby="project-summary-title">
          <div className={styles.summaryTitle}>
            <span className={styles.kicker}>PROJECT AT A GLANCE</span>
            <h2 id="project-summary-title">Candle은 금융 도메인의 복잡한 흐름을<br />명확한 서비스 경계로 풀어냅니다.</h2>
          </div>
          <div className={styles.summaryGrid}>
            {projectSummary.map((item) => (
              <article key={item.label}>
                <span>{item.label}</span>
                <h3>{item.title}</h3>
                <p>{item.description}</p>
              </article>
            ))}
          </div>
        </section>

        <section id="services" className={styles.servicesSection} aria-labelledby="domain-services-title">
          <div className={styles.servicesHeading}>
            <div>
              <span className={styles.kicker}>DOMAIN SERVICES</span>
              <h2 id="domain-services-title">13개의 독립적인 서비스가<br />하나의 투자 경험을 만듭니다.</h2>
            </div>
            <p>
              각 서비스는 자신의 데이터와 업무 규칙을 소유합니다. 필요한 정보만 명시적인 계약으로
              교환하기 때문에 기능을 독립적으로 개발하고 장애를 격리할 수 있습니다.
            </p>
          </div>

          <div className={styles.serviceGroups}>
            {serviceGroups.map((group) => (
              <article key={group.index} className={styles.serviceGroup}>
                <header>
                  <span>{group.index}</span>
                  <div>
                    <small>{group.label}</small>
                    <h3>{group.title}</h3>
                    <p>{group.description}</p>
                  </div>
                </header>
                <div className={styles.serviceList}>
                  {group.services.map(([name, role, features]) => (
                    <div key={name} className={styles.serviceRow}>
                      <strong>{name}</strong>
                      <span>{role}</span>
                      <p>{features}</p>
                    </div>
                  ))}
                </div>
              </article>
            ))}
          </div>
        </section>

        <section id="features" className={styles.journeySection} aria-labelledby="product-journeys-title">
          <div className={styles.journeyHeading}>
            <div>
              <span className={styles.kicker}>CORE PRODUCT JOURNEYS</span>
              <h2 id="product-journeys-title">서비스 이름보다 먼저,<br />사용자 기능으로 이해합니다.</h2>
            </div>
            {/*<p>발표에서는 이 네 가지 여정을 따라가면 Candle의 전체 기능을 자연스럽게 설명할 수 있습니다.</p>*/}
          </div>
          <div className={styles.journeyGrid}>
            {productJourneys.map((journey) => (
              <article key={journey.number} className={`${styles.journeyCard} ${styles[journey.color]}`}>
                <div className={styles.journeyTop}>
                  <span>{journey.number}</span>
                  <span>{journey.path}</span>
                </div>
                <div>
                  <small>{journey.title}</small>
                  <h3>{journey.korean}</h3>
                  <p>{journey.description}</p>
                </div>
              </article>
            ))}
          </div>
        </section>

        <section className={styles.section}>
          <div className={styles.sectionHeading}>
            <div>
              <span className={styles.kicker}>EXPLORE THE SYSTEM</span>
              <h2>문서에서 구현까지,<br />한 번에 이어서 봅니다.</h2>
            </div>
            <p>
              추상적인 소개가 아니라 실제 클래스, RPC, 이벤트, 테이블과 테스트 명령을 기준으로
              작성된 엔지니어링 문서입니다.
            </p>
          </div>

          <div className={styles.documentGrid}>
            {documents.map((document) => (
              <Link
                key={document.title}
                className={`${styles.documentCard} ${styles[document.accent]}`}
                to={document.link}>
                <div className={styles.cardTop}>
                  <span>{document.number}</span>
                  <span>{document.tag}</span>
                </div>
                <div>
                  <h3>{document.title}</h3>
                  <p>{document.description}</p>
                </div>
                <span className={styles.cardLink}>READ DOCUMENT <b>↗</b></span>
              </Link>
            ))}
          </div>
        </section>

        <section id="flow" className={styles.flowSection}>
          <div className={styles.flowIntro}>
            <span className={styles.kicker}>ONE TRADING DAY</span>
            <h2>하루의 거래가<br />신뢰할 수 있는 기록이 되기까지.</h2>
            <p>
              각 단계는 자신의 데이터를 커밋합니다. 다음 단계가 실패해도 이미 확정된 상태는
              유지되고, 같은 키와 거래일로 안전하게 다시 시작합니다.
            </p>
          </div>
          <div className={styles.flowRail}>
            <div className={styles.flowItem}>
              <span>08:30 — 15:40</span>
              <strong>Trade &amp; settle</strong>
              <p>예약과 주문을 처리하고 체결 이벤트를 Outbox에 기록합니다.</p>
              <i>TRADING</i>
            </div>
            <div className={styles.flowItem}>
              <span>16:00 KST</span>
              <strong>Portfolio EOD</strong>
              <p>현금과 보유 종목의 확정 종가를 결합해 일별 자산을 저장합니다.</p>
              <i>PORTFOLIO</i>
            </div>
            <div className={styles.flowItem}>
              <span>16:20 KST</span>
              <strong>Finalize ranking</strong>
              <p>완료된 스냅샷을 기준으로 결정적 순위를 만들고 캐시를 갱신합니다.</p>
              <i>RANKING</i>
            </div>
          </div>
        </section>

        <section className={styles.principlesSection}>
          <div className={styles.principlesHeader}>
            <span className={styles.kicker}>ENGINEERING PRINCIPLES</span>
            <h2>빠르게 만들되,<br />경계는 선명하게.</h2>
          </div>
          <div className={styles.principlesList}>
            {principles.map(([number, title, description]) => (
              <article key={number}>
                <span>{number}</span>
                <h3>{title}</h3>
                <p>{description}</p>
              </article>
            ))}
          </div>
        </section>

        <section className={styles.finalCta}>
          <span className={styles.kicker}>START EXPLORING</span>
          <h2>코드의 맥락을 잃지 않는<br />Candle의 단일 문서 허브.</h2>
          <Link className={styles.primaryAction} to="/docs/CONVENTIONS">
            ENGINEERING DOCS <span aria-hidden="true">↗</span>
          </Link>
        </section>
      </main>
    </Layout>
  );
}
