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

        <section className={styles.flowSection}>
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
