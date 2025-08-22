import { test, expect, Page } from '@playwright/test';

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
        expect(content).toContain('Result: 3');
        expect(content).not.toContain('Actual:');
      });

      test('text probe with assertion', async ({ page }) => {
        const { content } = await fillPageContent({ page, wantedContent: '(1+2) // [[Add.value=4]]', editor });
        expect(content).not.toContain('Result:');
        expect(content).toContain('Actual: 3');
      });
      test('run workspace tests', async ({ page, request }) => {
        await fillPageContent({ page, wantedContent: '(1+2)', editor });

        await page.click('#workspace-test-runner');
        await page.waitForLoadState('networkidle');

        await page.waitForTimeout(500); // Run tests

        const runResult = await page.locator(`div:has(+ .workspace-test-failure-log)`).textContent();
        const [_, numPass, numFail] = runResult!.match(/^.*?(\d+) pass, (\d+) fail/)!;

        const fetched = (await (await request.get('api/workspace/contents?f=expected_test_output.txt')).json()).text;
        const expectedStatusLine = fetched.split('\n').find((x: string) => x.startsWith('Done: '));

        expect(`Done: ${numPass} pass, ${numFail} fail`).toBe(expectedStatusLine);
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
    });
  });

  type FillArgs = {
    page: Page;
    wantedContent: string;
    editor: 'Monaco' | 'CodeMirror';
  }
  async function fillPageContent(args: FillArgs) {
    const { editor, page, wantedContent } = args;
    await page.goto(`/?editor=${editor}`);
    await page.waitForLoadState('networkidle');

    // Wait for Monaco editor to initialize, which is indicated by the input element disappearing
    await page.waitForSelector('#input-wrapper:not(:has(> #input))');

    try {
      // Click on the editor area
      await page.click('#input-wrapper');

      // Select all existing content (Ctrl+A / Cmd+A)
      const browserName = await page.evaluate(() => navigator.userAgent);

      // Select all text
      let forceControl = false;
      if (editor === 'Monaco') {
        forceControl = (process.platform === 'darwin'
            && (browserName.includes('Firefox') || browserName.includes('Chrome'))
        );
      }
      console.log('Doing select all with forceControl=', forceControl, 'for process.platform:', process.platform, 'and browserName:', browserName);
      await page.keyboard.press(
        // For some reason "Meta+KeyA" does not work on Firefox, we use "Control+KeyA" instead
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

    const actualContent = (await page.evaluate(() => document.querySelector('#input-wrapper')?.textContent || ''))
      .replaceAll(' ', ' ') // Normalize spaces
    ;

    // Verify that the initial text is not present in the editor
    expect(actualContent).not.toContain('Hello World!'); // Ensure we didn't leave old content

    return { content: actualContent };
  }
});
