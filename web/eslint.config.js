import js from '@eslint/js';
import pluginVue from 'eslint-plugin-vue';
import vueTsConfig from '@vue/eslint-config-typescript';
import prettierConfig from 'eslint-config-prettier';

export default [
  {
    ignores: ['dist/**', 'node_modules/**', 'coverage/**'],
  },
  js.configs.recommended,
  ...pluginVue.configs['flat/recommended'],
  ...vueTsConfig(),
  // 必须放在最后：关闭所有与 Prettier 冲突的格式类规则。
  // 分工是 Prettier 负责代码格式，ESLint 只负责代码质量，
  // 否则 `lint --fix` 与 `format` 会互相覆盖（本项目已实测到 vue/max-attributes-per-line 冲突）。
  prettierConfig,
  {
    rules: {
      'no-console': ['warn', { allow: ['warn', 'error'] }],
    },
  },
];
