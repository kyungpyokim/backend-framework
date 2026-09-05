import { Test, TestingModule } from '@nestjs/testing';
import { INestApplication } from '@nestjs/common';
import { WsAdapter } from '@nestjs/platform-ws';
import * as request from 'supertest';
import RedisMock from 'ioredis-mock';
import { AppModule } from '../src/app.module';
import { RedisService } from '../src/core/redis/redis.service';
import { WorkerService } from '../src/cases/case2-queue/worker.service';

describe('NestJS Distributed Playground E2E', () => {
  let app: INestApplication;
  let fakeRedis: any;

  beforeAll(async () => {
    fakeRedis = new RedisMock();

    const moduleFixture: TestingModule = await Test.createTestingModule({
      imports: [AppModule],
    })
      .overrideProvider(RedisService)
      .useValue({
        getClient: () => fakeRedis,
        onModuleDestroy: async () => {},
      })
      .compile();

    app = moduleFixture.createNestApplication();
    app.useWebSocketAdapter(new WsAdapter(app));
    await app.init();
  });

  afterAll(async () => {
    await app.close();
  });

  it('GET /health should return 200 ok', async () => {
    const res = await request(app.getHttpServer()).get('/health');
    expect(res.status).toBe(200);
    expect(res.body.status).toBe('ok');
  });

  it('Case 1: Lock safe purchase concurrency test', async () => {
    // 1. Initialize stock = 5
    await request(app.getHttpServer())
      .post('/case1/inventory/init')
      .send({ item_id: 'nest-ticket', stock: 5 })
      .expect(201);

    // 2. Concurrently call purchase/safe 5 times
    const tasks = Array.from({ length: 5 }).map(() =>
      request(app.getHttpServer())
        .post('/case1/purchase/safe')
        .send({ item_id: 'nest-ticket', quantity: 1 }),
    );
    const results = await Promise.all(tasks);
    for (const r of results) {
      expect(r.status).toBe(201);
      expect(r.body.success).toBe(true);
    }

    // 3. Final stock should be 0
    const check = await request(app.getHttpServer()).get('/case1/inventory/nest-ticket');
    expect(check.body.stock).toBe(0);

    // 4. 6th call should fail with Out of stock
    const outOfStock = await request(app.getHttpServer())
      .post('/case1/purchase/safe')
      .send({ item_id: 'nest-ticket', quantity: 1 });
    expect(outOfStock.body.success).toBe(false);
    expect(outOfStock.body.message).toBe('Out of stock');
  });

  it('Case 2: Queue job submission and status check', async () => {
    const res = await request(app.getHttpServer())
      .post('/case2/jobs')
      .send({ task_type: 'nest_compute', payload: { duration_sec: 0 } })
      .expect(201);

    expect(res.body.status).toBe('PENDING');
    const jobId = res.body.job_id;

    const check = await request(app.getHttpServer()).get(`/case2/jobs/${jobId}`);
    expect(check.status).toBe(200);
    expect(check.body.status).toBe('PENDING');
  });

  it('Case 3: Broadcast HTTP trigger', async () => {
    const res = await request(app.getHttpServer())
      .post('/case3/broadcast')
      .send({ room_id: 'lobby', sender: 'admin', content: 'hello from nest' });
    expect(res.status).toBe(201);
    expect(res.body.status).toBe('published');
  });

  it('Case 4: Circuit breaker state transition and fallback', async () => {
    // 1. Normal call
    const normal = await request(app.getHttpServer()).get('/case4/call');
    expect(normal.status).toBe(200);
    expect(normal.body.result.status).toBe('SUCCESS');
    expect(normal.body.circuit_state).toBe('CLOSED');

    // 2. Inject fault
    await request(app.getHttpServer())
      .post('/case4/external/fault')
      .send({ is_healthy: false });

    // 3. 3 failures
    for (let i = 0; i < 3; i++) {
      const failed = await request(app.getHttpServer()).get('/case4/call');
      expect(failed.body.result.status).toBe('FALLBACK');
    }

    // 4. Check state -> OPEN
    const status = await request(app.getHttpServer()).get('/case4/circuit-status');
    expect(status.body.state).toBe('OPEN');

    // 5. Restore health
    await request(app.getHttpServer())
      .post('/case4/external/fault')
      .send({ is_healthy: true });
  });
});
