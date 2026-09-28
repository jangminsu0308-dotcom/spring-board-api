const { test, expect } = require('@playwright/test');
const { uniqueUsername } = require('./helpers');

test('회원가입 후 로그인하면 환영 메시지가 보인다', async ({ page }) => {
  const username = uniqueUsername('e2euser');

  await page.goto('/');
  await page.locator('#btn-register').click();
  await page.locator('#register-username').fill(username);
  await page.locator('#register-password').fill('password123');
  await page.locator('#register-security-answer').fill('테스트답변');
  await page.locator('#register-submit').click();
  await expect(page.locator('#register-form')).toBeHidden();

  await page.locator('#btn-login').click();
  await page.locator('#login-username').fill(username);
  await page.locator('#login-password').fill('password123');
  await page.locator('#login-submit').click();

  await expect(page.locator('#auth-who')).toHaveText(`${username}님 환영합니다`);
  await expect(page.locator('#btn-logout')).toBeVisible();
  await expect(page.locator('#btn-login')).toBeHidden();
});

test('비밀번호가 틀리면 로그인에 실패하고 에러 메시지가 보인다', async ({ page }) => {
  const username = uniqueUsername('e2ewrongpw');

  await page.goto('/');
  await page.locator('#btn-register').click();
  await page.locator('#register-username').fill(username);
  await page.locator('#register-password').fill('password123');
  await page.locator('#register-security-answer').fill('테스트답변');
  await page.locator('#register-submit').click();
  await expect(page.locator('#register-form')).toBeHidden();

  await page.locator('#btn-login').click();
  await page.locator('#login-username').fill(username);
  await page.locator('#login-password').fill('wrong-password');
  await page.locator('#login-submit').click();

  await expect(page.locator('#login-error')).toBeVisible();
  await expect(page.locator('#auth-who')).toHaveText('로그인이 필요합니다');
});
