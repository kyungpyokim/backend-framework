/**
 * 애플리케이션 환경 변수 및 공통 설정 객체.
 */
export const config = {
  // HTTP 리스닝 포트
  port: parseInt(process.env.PORT || '3000', 10),
  // 분산 클러스터 내에서 현재 NestJS 인스턴스를 식별하는 고유 노드 ID
  nodeId: process.env.NODE_ID || 'node-nest-default',
  // 분산 락, 스트림 큐, Pub/Sub 연동에 사용할 Redis URL
  redisUrl: process.env.REDIS_URL || 'redis://localhost:6379',
};
