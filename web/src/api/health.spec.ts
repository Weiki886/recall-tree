import { afterEach, describe, expect, it, vi } from 'vitest';
import { fetchHealth } from './health';

describe('fetchHealth', () => {
  afterEach(() => {
    vi.unstubAllGlobals();
  });

  it('解析契约定义的 HealthStatus 结构', async () => {
    const payload = {
      status: 'UP',
      checks: {
        database: 'UP',
        chatProvider: 'DISABLED',
        embeddingProvider: 'DISABLED',
        pendingMemoryTasks: 0,
      },
    };
    vi.stubGlobal(
      'fetch',
      vi.fn().mockResolvedValue({ ok: true, json: () => Promise.resolve(payload) }),
    );

    await expect(fetchHealth()).resolves.toEqual(payload);
  });

  it('非 2xx 响应抛出带状态码的错误', async () => {
    vi.stubGlobal(
      'fetch',
      vi.fn().mockResolvedValue({ ok: false, status: 503, statusText: 'Service Unavailable' }),
    );

    await expect(fetchHealth()).rejects.toThrow('503');
  });
});
