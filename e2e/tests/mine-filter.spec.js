const { test, expect } = require('@playwright/test');
const { uniqueUsername, registerAndLogin, createPost } = require('./helpers');

test('내가 쓴 글만 필터를 켜면 다른 사람 글은 목록에서 사라진다', async ({ page }) => {
  const otherUsername = uniqueUsername('e2eother');
  const otherTitle = `다른사람글 ${Date.now()}`;

  // 먼저 다른 계정으로 글을 하나 남겨둔다.
  await registerAndLogin(page, otherUsername);
  await createPost(page, otherTitle, '다른 사람이 쓴 글입니다.');
  await page.locator('#btn-logout').click();

  // 내 계정으로 새로 가입해서 로그인 후 필터를 켠다 — 방금 로그아웃한 계정의
  // 글만 보이지 않아야 하고, 내가 이 시점에 쓴 글은 보여야 한다.
  const myUsername = uniqueUsername('e2eme');
  const myTitle = `내글 ${Date.now()}`;
  await registerAndLogin(page, myUsername);
  await createPost(page, myTitle, '내가 쓴 글입니다.');

  await page.locator('#mine-toggle').click();
  await expect(page.locator('#mine-toggle')).toHaveAttribute('aria-pressed', 'true');

  await expect(page.locator('.post-item', { hasText: myTitle })).toBeVisible();
  await expect(page.locator('.post-item', { hasText: otherTitle })).toHaveCount(0);

  await page.locator('#mine-toggle').click();
  await expect(page.locator('#mine-toggle')).toHaveAttribute('aria-pressed', 'false');
});

test('로그아웃하면 내가 쓴 글만 필터가 자동으로 풀린다', async ({ page }) => {
  const username = uniqueUsername('e2elogout');
  await registerAndLogin(page, username);

  await page.locator('#mine-toggle').click();
  await expect(page.locator('#mine-toggle')).toHaveAttribute('aria-pressed', 'true');

  await page.locator('#btn-logout').click();

  await expect(page.locator('#mine-toggle')).toBeHidden();
});
