import repositoryUrl from "../model/repositoryUrl";


const showVersionInfo = (elem: HTMLDivElement, ourHash: string, ourClean: boolean) => {

  elem.innerHTML = `Version: ${ourHash}${ourClean ? '' : ' [DEV]'}`;
  if (!ourClean) {
    // No need to poll for new versions, 'DEV' label already shown
    return;
  }

  const pollNewVersion = async (): Promise<'done' | 'again'> => {
    const header = await fetch(`${repositoryUrl}/-/raw/master/VERSION`);
    if (header.status !== 200) {
      console.warn('Unexpected response code when fetching version info: ', header.status);
      return 'done';
    }
    const text = (await header.text()).trim().split('\n').slice(-1)[0];

    if (ourHash === text) {
      // Status is clean.. for now.
      // Check again (much) later
      return 'again';
    }

    const a = document.createElement('a');
    a.href = `${repositoryUrl}/-/blob/master/pasta-server.jar`;
    a.target = '_blank';
    a.text = 'New version available';
    elem.appendChild(document.createElement('br'));
    elem.appendChild(a);
    return 'done';
  };

  (async () => {
    while (true) {
      const status = await pollNewVersion();
      if (status === 'done') { return; }

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
