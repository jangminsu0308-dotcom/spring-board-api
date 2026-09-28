const { test, expect } = require('@playwright/test');
const { uniqueUsername, registerAndLogin, createPost } = require('./helpers');

test('본문에만 있는 단어로 검색해도 글이 찾아진다', async ({ page }) => {
  const username = uniqueUsername('e2esearch');
  const uniqueWord = `유니크워드${Date.now()}`;
  const title = '평범한 제목';
  const content = `이 글의 본문에는 ${uniqueWord} 라는 단어가 들어있습니다.`;

  await registerAndLogin(page, username);
  await createPost(page, title, content);

  await page.locator('#search-keyword').fill(uniqueWord);
  await page.locator('#search-submit').click();

  const listItem = page.locator('.post-item', { hasText: title }).first();
  await expect(listItem).toBeVisible();
  await expect(page.locator('#search-clear')).toBeVisible();
});

test('검색 결과가 없으면 안내 문구가 보인다', async ({ page }) => {
  await page.goto('/');
  await page.locator('#search-keyword').fill(`존재하지않는검색어${Date.now()}`);
  await page.locator('#search-submit').click();

  await expect(page.locator('#post-list')).toContainText('검색 결과가 없습니다');
});

test('검색 초기화를 누르면 전체 목록으로 돌아간다', async ({ page }) => {
  await page.goto('/');
  await page.locator('#search-keyword').fill(`존재하지않는검색어${Date.now()}`);
  await page.locator('#search-submit').click();
  await expect(page.locator('#search-clear')).toBeVisible();

  await page.locator('#search-clear').click();

  await expect(page.locator('#search-keyword')).toHaveValue('');
  await expect(page.locator('#search-clear')).toBeHidden();
});
