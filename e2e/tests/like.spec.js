const { test, expect } = require('@playwright/test');
const { uniqueUsername, registerAndLogin, createPost } = require('./helpers');

test('좋아요를 누르면 카운트가 오르고, 다시 누르면 취소된다', async ({ page }) => {
  const username = uniqueUsername('e2elike');
  const title = `좋아요테스트 ${Date.now()}`;

  await registerAndLogin(page, username);
  await createPost(page, title, '좋아요 토글 테스트용 글입니다.');

  const listItem = page.locator('.post-item', { hasText: title }).first();
  const likeBtn = listItem.locator('.like-btn');
  const likeCount = likeBtn.locator('span');

  await expect(likeCount).toHaveText('0');
  await expect(likeBtn).not.toHaveClass(/liked/);

  await likeBtn.click();
  await expect(likeCount).toHaveText('1');
  await expect(likeBtn).toHaveClass(/liked/);

  await likeBtn.click();
  await expect(likeCount).toHaveText('0');
  await expect(likeBtn).not.toHaveClass(/liked/);
});

test('로그인하지 않으면 좋아요를 눌러도 로그인 안내가 뜬다', async ({ page }) => {
  const username = uniqueUsername('e2elikeauthor');
  const title = `비로그인좋아요테스트 ${Date.now()}`;

  await registerAndLogin(page, username);
  await createPost(page, title, '비로그인 좋아요 클릭 테스트용 글입니다.');
  await page.locator('#btn-logout').click();

  const listItem = page.locator('.post-item', { hasText: title }).first();
  await listItem.locator('.like-btn').click();

  await expect(page.locator('#login-form')).toBeVisible();
});
