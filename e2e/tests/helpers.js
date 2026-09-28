const { expect } = require('@playwright/test');

// 매 테스트가 겹치지 않는 계정으로 가입/로그인하도록, 매번 새 아이디를 만든다 —
// 그래야 같은 DB를 공유해도 테스트끼리 서로의 데이터에 영향을 주지 않는다.
function uniqueUsername(prefix) {
  return `${prefix}${Date.now()}${Math.floor(Math.random() * 1000)}`;
}

async function registerAndLogin(page, username, password = 'password123') {
  await page.goto('/');
  await page.locator('#btn-register').click();
  await page.locator('#register-username').fill(username);
  await page.locator('#register-password').fill(password);
  await page.locator('#register-security-answer').fill('테스트답변');
  await page.locator('#register-submit').click();
  await page.locator('#register-form').waitFor({ state: 'hidden' });

  await page.locator('#btn-login').click();
  await page.locator('#login-username').fill(username);
  await page.locator('#login-password').fill(password);
  await page.locator('#login-submit').click();
  await expect(page.locator('#auth-who')).toHaveText(`${username}님 환영합니다`);
}

async function createPost(page, title, content) {
  await page.locator('#btn-new-post').click();
  await page.locator('#new-post-title').fill(title);
  await page.locator('#new-post-content').fill(content);
  await page.locator('#new-post-submit').click();
  await page.locator('.post-item', { hasText: title }).first().waitFor();
}

module.exports = { uniqueUsername, registerAndLogin, createPost };
