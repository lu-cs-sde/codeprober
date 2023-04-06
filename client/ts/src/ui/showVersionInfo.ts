import ModalEnv from '../model/ModalEnv';
import { repositoryUrl, rawUrl } from "../model/repositoryUrl";
import { FetchReq, FetchRes } from '../protocol';

const showVersionInfo = (elem: HTMLDivElement, ourHash: string, ourClean: boolean, ourBuildTime: number | undefined, sendRequest: ModalEnv['performTypedRpc']) => {

  const innerPrefix = `Version: ${ourHash}${ourClean ? '' : ' [DEV]'}`;
  if (ourBuildTime !== undefined) {
    const d = new Date(ourBuildTime * 1000);
    elem.innerText = `${innerPrefix}, ${d.toLocaleDateString()}`;
  } else {
    elem.innerText = innerPrefix;
  }
  if ('false' === localStorage.getItem('enable-version-checker')) {
    // In case somebody wants to stay on an old version for a long time,
    // then the "new version available" popup can become annoying.
    // This flag allows you to disable version checking.
    // Don't tell anybody about it though! 🤫 We want people staying updated.
    return;
  }

  if (!ourClean) {
    // No need to poll for new versions, 'DEV' label already shown
    return;
  }

  const pollNewVersion = async (): Promise<'done' | 'again'> => {
    let fetched: string | undefined;
    try {
      fetched = (await sendRequest<FetchReq, FetchRes>({
        type: 'Fetch',
        url: rawUrl('VERSION'),
      }))?.result;
    } catch (e) {
      console.warn('Error when fetching version', e);
      return 'done';
    }
    if (!fetched) {
      console.warn('Unexpected response:', fetched);
      return 'done';
    }
    const hash = fetched.trim().split('\n').slice(-1)[0];

    if (ourHash === hash) {
      // Status is clean.. for now.
      // Check again (much) later
      return 'again';
    }

    const a = document.createElement('a');
    a.href = `${repositoryUrl}/blob/master/code-prober.jar`;
    a.target = '_blank';
    a.text = 'New version available';
    elem.appendChild(document.createElement('br'));
    elem.appendChild(a);
    return 'done';
  };

  (async () => {
    while (true) {
      const status = await pollNewVersion();
      if (status === 'done') { return; }

      // Sleep for 12 hours..
      // In the unlikely (but flattering!) scenario that somebody keeps the tool
      // active on their computer for several days in a row, we will re-check version
      // info periodically so they don't miss new releases.
      await (new Promise((res) => setTimeout(res, 12 * 60 * 60 * 1000)))
    }
  })()
    .catch(err => console.warn('Error when polling for new versions', err));

};

export default showVersionInfo;
