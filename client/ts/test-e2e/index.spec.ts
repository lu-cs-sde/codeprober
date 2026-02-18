import { test, expect, Page } from '@playwright/test';
import { mkdirSync, statSync, writeFileSync, rmSync } from 'node:fs';

test.describe('CodeProber Integration Tests', () => {
  test('should load index.html and receive a response', async ({ page }) => {
    // Navigate to the home page
    const response = await page.goto('/');

    // Check that we get a successful response (page loads)
    expect(response?.status()).toBe(200);

    // Wait for page to be ready
    await page.waitForLoadState('networkidle');

    // Verify the page title
    const title = await page.title();
    expect(title).toBe('CodeProber');

    // Verify that some expected content is present
    const bodyContent = await page.textContent('body');
    expect(bodyContent).toContain('Input');
    expect(bodyContent).toContain('Settings');
  });

  const editors: FillArgs['editor'][] = ['CodeMirror'];
  if (!process.env.CI) {
    // Monaco is buggy in CI, so we only run it locally
    editors.push('Monaco');
  }
  editors.forEach((editor) => {
    test.describe(`Using editor=${editor}`, () => {
      test('can edit text', async ({ page }) => {
        const { content } = await fillPageContent({ page, wantedContent: 'Hello from test!', editor });
        expect(content).toContain('Hello from test!');
      });

      test('text probes', async ({ page }) => {
        const { content } = await fillPageContent({ page, wantedContent: '(1+2) // [[Add.value]]', editor });
        expect(content).toContain('= 3');
        expect(content).not.toContain('Actual:');
      });
      test('timeout error is visible', async ({ page }) => {
        await fillPageContent({ page, wantedContent: '(1+2) // [[Program.sleep(1000)=]]', editor})
        await new Promise(res => setTimeout(res, 200));
        const content = await extractCurrentContent(page);
        expect(content).toContain('Timeout');
        expect(content).not.toContain('Slept for');
      });
      test('timeout error is not visible', async ({ page }) => {
        await fillPageContent({ page, wantedContent: '(1+2) // [[Program.sleep(10)=]]', editor})
        await new Promise(res => setTimeout(res, 100));
        const content = await extractCurrentContent(page);
        expect(content).toContain('Slept for 10');
      });

      [true, false].forEach((tilde) => {
        test(`text probe with assertion, tilde=${tilde}`, async ({ page }) => {
          const { content } = await fillPageContent({ page, wantedContent: `(1+2) // [[Add.value${tilde ? '~' : ''}=4]]`, editor });
          expect(content).not.toContain('= 3');
          expect(content).toContain('Actual: 3');
        });
      })
      test('run workspace tests', async ({ page, request }) => {
        await fillPageContent({ page, wantedContent: '(1+2)', editor });

        await page.click('#workspace-test-runner');
        await page.waitForLoadState('networkidle');

        await page.waitForTimeout(1000); // Run tests

        const runResult = await page.locator(`div:has(+ .workspace-test-failure-log)`).textContent();
        const [_, numPass, numFail] = runResult!.match(/^.*?(\d+) pass, (\d+) fail/)!;

        const fetched = (await (await request.get('api/workspace/contents?f=expected_test_output.txt')).json()).text;
        const expectedStatusLine = fetched.split('\n').find((x: string) => x.startsWith('Done: '));

        expect(`Done: ${numPass} pass, ${numFail} fail`).toBe(expectedStatusLine);
      });
      // Generate a name in 2-length chunks. This is to avoid generating a large number like "11123123"
      // which contains "111", and conflicts with the tests that check for precence of the text "111".
      const rngNameChunk = () => `_${Math.abs((Math.random() * 1000)|0)}`.slice(0, 3)

      // For updates that require file syncing, set a generous timeout.
      // This is mainly needed for OSX, where the WatchService is slow.
      const fileSyncTimeout = 30_000;

      const generateWorkspaceEntryName = () => `dynamic_entry${rngNameChunk()}${rngNameChunk()}${rngNameChunk()}`;
      test('reacts to workspace changes', async ({ page, request }) => {
        const wsEntryName = generateWorkspaceEntryName();

        await fillPageContent({ page, wantedContent: '(1+2)', editor });


        await expect(page.getByText(wsEntryName)).toBeHidden();

        const basePath = `../../addnum/workspace/`;
        try {
          mkdirSync(`${basePath}${wsEntryName}`);
          await expect(page.getByText(wsEntryName)).toBeVisible({ timeout: fileSyncTimeout });

          await page.getByText(wsEntryName).click();

          const locateNewFile = () => page.getByText('NewFileName');
          const locateContents = () => page.getByText('NewFileContents');
          const newFilePath = `${basePath}${wsEntryName}/NewFileName`;

          // No file exists, nor its contents
          await expect(locateNewFile()).toBeHidden();
          await expect(locateContents()).toBeHidden();

          // After file syncing, the file is visible. Contents still invisible until clicked
          console.log('== Creating NewFile')
          writeFileSync(newFilePath, 'NewFileContents');
          const mstat1 = statSync(newFilePath).mtime;
          await expect(locateNewFile()).toBeVisible({ timeout: fileSyncTimeout });
          await expect(locateContents()).toBeHidden();

          // Click -> contents visible
          console.log('== Clicking row')
          await locateNewFile().click();
          await expect(locateContents()).toBeVisible({ timeout: 1000 });

          // The client should not re-write the same content to the file upon loading it.
          // We can detect this by comparing mtime's before and after the client loaded the file.
          const mstat2 = statSync(newFilePath).mtime;
          expect(mstat2).toEqual(mstat1);

          // Change underlying file -> update visible in UI
          console.log('== Changing path to different contents')
          writeFileSync(newFilePath, 'ChangedFileContents_v1');
          writeFileSync(newFilePath +"_v2", 'ChangedFileContents_v2');
          await expect(page.getByText('ChangedFileContents_v1')).toBeVisible({ timeout: fileSyncTimeout });
          await expect(locateContents()).toBeHidden();

          // If we remove the file, it should default back to the temp file, which we previously initialized with "(1+2)"
          rmSync(newFilePath);
          await expect(page.getByText('(1+2)')).toBeVisible({ timeout: fileSyncTimeout });

          await page.getByText('NewFileName_v2').click();
          await expect(page.getByText('ChangedFileContents_v2')).toBeVisible({ timeout: 1000 });

          // Similarly, if we remove the parent of the active file, it should also go back to the temp file.
          rmSync(`${basePath}${wsEntryName}`, { recursive: true, force: true });
          await expect(page.getByText('(1+2)')).toBeVisible({ timeout: fileSyncTimeout });

        } finally {
          rmSync(`${basePath}${wsEntryName}`, { recursive: true, force: true });
        }
      });

      test('create a probe', async ({ page,  }) => {
        await fillPageContent({ page, wantedContent: '(111+222\n)', editor });

        // Right click
        await page.getByText('111').click({ button: 'right' });

        // 'Create Probe'
        if (editor === 'Monaco') {
          // Monaco delays the context menu quite a bit, must artificially wait
          await page.waitForTimeout(100);
        }
        await page.getByText('Create Probe').click();
        await page.waitForTimeout(50);

        // 'Add'
        await page.locator('.modalWindow .nodeLocatorContainer', { hasText: 'Add'}).click();
        await page.waitForTimeout(50);

        // 'value'
        await page.locator('.modalWindow .syntax-attr-dim-focus', { hasText: 'value' }).click();

        expect(await page.textContent('.modalWindow pre')).toContain('333');
      });
      test('create a new empty file', async ({ page }) => {
        await fillPageContent({ page, wantedContent: '(1+2)', editor });

        // The temp file should be active and contain (1+2)
        const activeTempFile = page.locator('.workspace-row.workspace-unsaved.workspace-row-active');
        await expect(activeTempFile).toBeVisible();
        await expect(page.getByText('(1+2)')).toBeVisible();

        const basePath = `../../addnum/workspace/`;
        const wsEntryName = generateWorkspaceEntryName();
        try {
          page.on('dialog', dia => dia.accept(wsEntryName));
          await page.click('.workspace-addfile');

          await new Promise(res => setTimeout(res, 10));
          await expect(page.getByText('(1+2)')).not.toBeVisible();

          // // Wait a while for the new file to load in
          await new Promise(res => setTimeout(res, fileSyncTimeout));

          // The contents in the default (temp) file should not follow us into the new file
          await expect(activeTempFile).not.toBeVisible();
          await expect(page.getByText('(1+2)')).not.toBeVisible();
        } finally {
          rmSync(`${basePath}/${wsEntryName}`);
        }
      });
    });
  });

  type FillArgs = {
    page: Page;
    wantedContent: string;
    editor: 'Monaco' | 'CodeMirror';
  }
  async function fillPageContent(args: FillArgs) {
    const { editor, page, wantedContent } = args;
    // Listen for all console logs
    page.on('console', msg => console.log('[C]', msg.text()));
    await page.goto(`/?editor=${editor}`);
    await page.waitForLoadState('networkidle');

    // Wait for Monaco editor to initialize, which is indicated by the input element disappearing
    await page.waitForSelector('#input-wrapper:not(:has(> #input))');

    try {
      // Click on the editor area
      await page.click('#input-wrapper');

      let forceControl = false;
      if (editor === 'Monaco') {
        const browserName = await page.evaluate(() => navigator.userAgent);
        forceControl = (process.platform === 'darwin'
          && (browserName.includes('Firefox') || browserName.includes('Chrome'))
        );
      }
      // Select all text
      await page.keyboard.press(
        forceControl
          ? 'Control+KeyA'
          : 'ControlOrMeta+KeyA'
      );

      // Type our test content (this will replace the selected text)
      await page.keyboard.type(wantedContent);
    } catch (error) {
      throw new Error(`Failed to interact with editor: ${error.message}`);
    }

    // Wait for the content to be processed
    await page.waitForTimeout(500);

    const actualContent = await extractCurrentContent(page);
    // Verify that the initial text is not present in the editor
    expect(actualContent).not.toContain('Hello World!'); // Ensure we didn't leave old content

    return { content: actualContent };
  }
});

const extractCurrentContent = async (page) => (await page.evaluate(() => document.querySelector('#input-wrapper')?.textContent || ''))
  .replaceAll(' ', ' '); // Normalize spaces
