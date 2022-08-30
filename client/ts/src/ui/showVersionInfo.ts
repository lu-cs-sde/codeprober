import repositoryUrl from "../model/repositoryUrl";


const showVersionInfo = (elem: HTMLDivElement, ourHash: string, ourClean: boolean) => {

  elem.innerHTML = `Version: ${ourHash}${ourClean ? '' : ' [DEV]'}`;
  if (!ourClean) {
    // No need to poll for new versions, 'DEV' label already shown
    return;
  }

  // const pollNewVersion = async (): Promise<'done' | 'again'> => new Promise((resolve, reject) => {
  //   const iframe = document.createElement('iframe');
  //   iframe.src = `${repositoryUrl}/-/raw/master/client/public/versionCheckerFragment.html`;
  //   iframe.width = '0px';
  //   iframe.height = '0px';
  //   iframe.style.borderWidth = '0px';
  //   document.body.appendChild(iframe);

  //   iframe.onload = () => {
  //     const nonce = `v-${(Math.random()*Number.MAX_SAFE_INTEGER)|0}`;
  //     const listener = (event: MessageEvent) => {
  //       switch (event.data.type) {
  //         case 'version-fetch-result': {
  //           if (event.data.nonce === nonce) {
  //             window.removeEventListener('message', listener);
  //             clearTimeout(timeout);
  //             iframe.remove();

  //             if (event.data.error) {
  //               console.warn('Error when fetching version:', event.data.error);
  //               reject('Timeout');
  //             } else if (ourHash === event.data.result) {
  //               // Status is clean.. for now.
  //               // Check again (much) later
  //               resolve('again');
  //             } else {
  //               console.log('New version available:', event.data.result);
  //               const a = document.createElement('a');
  //               a.href = `${repositoryUrl}/-/blob/master/code-prober.jar`;
  //               a.target = '_blank';
  //               a.text = 'New version available';
  //               elem.appendChild(document.createElement('br'));
  //               elem.appendChild(a);
  //               resolve('done');
  //             }
  //           }
  //           break;
  //         }
  //       }
  //     };
  //     iframe.contentWindow?.postMessage({
  //       type: 'get-version-please',
  //       nonce,
  //     });
  //     window.addEventListener('message', listener)

  //     let timeout = setTimeout(() => {
  //       console.log('Timed out checking version')
  //       reject('Timeout');
  //       window.removeEventListener('message', listener)
  //       iframe.remove();
  //     }, 60 * 1000);
  //   }
  // });

  const pollNewVersion = async (): Promise<'done' | 'again'> => {
    const header = await fetch(`https://code-prober.s3.eu-central-1.amazonaws.com/VERSION`);
    if (header.status !== 200) {
      console.warn('Unexpected response code when fetching version info: ', header.status);
      return 'done';
    }
    const text = (await header.text());
    console.log('Newest version hash:', text);

    if (ourHash === text) {
      // Status is clean.. for now.
      // Check again (much) later
      return 'again';
    }

    const a = document.createElement('a');
    a.href = `${repositoryUrl}/-/blob/master/code-prober.jar`;
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
