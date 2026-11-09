import { defineConfig } from 'vitest/config';

// Тесты логики идут в node: компоненты и DOM здесь не нужны. Для тестов форм добавьте environment: 'jsdom'.
export default defineConfig({
  test: { environment: 'node', include: ['src/**/*.test.ts'] },
});
