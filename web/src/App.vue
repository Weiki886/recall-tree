<script setup lang="ts">
import { ref } from 'vue';
import { fetchHealth, type HealthStatus } from './api/health';

const health = ref<HealthStatus | null>(null);
const error = ref<string | null>(null);

async function check() {
  error.value = null;
  try {
    health.value = await fetchHealth();
  } catch (e) {
    error.value = e instanceof Error ? e.message : String(e);
  }
}
</script>

<template>
  <main>
    <h1>RecallTree</h1>
    <p>面向 AI Agent 的长期记忆管理系统</p>

    <button type="button" @click="check">检查后端健康状态</button>

    <pre v-if="health">{{ JSON.stringify(health, null, 2) }}</pre>
    <p v-if="error" role="alert">请求失败：{{ error }}</p>
  </main>
</template>
