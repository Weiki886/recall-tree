export interface HealthStatus {
  status: 'UP' | 'DEGRADED' | 'DOWN';
  checks: {
    database: 'UP' | 'DOWN';
    chatProvider: 'UP' | 'DEGRADED' | 'DOWN' | 'DISABLED';
    embeddingProvider: 'UP' | 'DEGRADED' | 'DOWN' | 'DISABLED';
    pendingMemoryTasks: number;
  };
}

export async function fetchHealth(): Promise<HealthStatus> {
  const res = await fetch('/v1/health');
  if (!res.ok) {
    throw new Error(`Health check failed: ${res.status} ${res.statusText}`);
  }
  return res.json();
}
