const { test, expect } = require('@playwright/test');
const { uniqueUsername, registerAndLogin } = require('./helpers');

test('로그인한 사용자는 글을 작성하면 목록과 상세에서 보인다', async ({ page }) => {
  const username = uniqueUsername('e2epost');
  const title = `E2E 테스트 제목 ${Date.now()}`;
  const content = 'E2E 테스트 본문 내용입니다.';

  await registerAndLogin(page, username);

  await page.locator('#btn-new-post').click();
  await page.locator('#new-post-title').fill(title);
  await page.locator('#new-post-content').fill(content);
  await page.locator('#new-post-submit').click();

  const listItem = page.locator('.post-item', { hasText: title }).first();
  await expect(listItem).toBeVisible();
  await expect(listItem).toContainText(username);

  await listItem.click();
  await expect(page.locator('.detail-title')).toHaveText(title);
  await expect(page.locator('.detail-content')).toHaveText(content);
});

test('로그인하지 않으면 글쓰기 버튼을 눌러도 로그인 폼으로 안내한다', async ({ page }) => {
  await page.goto('/');
  await page.locator('#btn-new-post').click();

  await expect(page.locator('#login-form')).toBeVisible();
  await expect(page.locator('#new-post-form')).toBeHidden();
});
