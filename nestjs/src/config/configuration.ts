export const config = {
  port: parseInt(process.env.PORT || '3000', 10),
  nodeId: process.env.NODE_ID || 'node-nest-default',
  redisUrl: process.env.REDIS_URL || 'redis://localhost:6379',
};
