// @ts-check
const { defineConfig, devices } = require('@playwright/test');

module.exports = defineConfig({
  testDir: './tests',
  fullyParallel: false,
  // 게시판 데이터(게시글 목록/검색)를 여러 테스트가 같은 DB에서 공유하므로,
  // 병렬로 돌리면 테스트끼리 서로의 게시글에 영향을 줄 수 있다 — 그래서 워커 1개로 순차 실행한다.
  workers: 1,
  retries: process.env.CI ? 1 : 0,
  reporter: process.env.CI ? [['html', { open: 'never' }], ['list']] : 'list',
  // 회원가입/로그인/글 작성은 실제 API 요청을 기다리는 단언이라, 컨테이너가 막 뜬 직후
  // 첫 요청처럼 순간적으로 느려질 때 기본 5초로는 부족할 수 있어 넉넉하게 잡는다.
  expect: { timeout: 10000 },
  use: {
    baseURL: process.env.E2E_BASE_URL || 'http://localhost:8080',
    trace: 'retain-on-failure',
    screenshot: 'only-on-failure',
  },
  projects: [
    { name: 'chromium', use: { ...devices['Desktop Chrome'] } },
  ],
});
