

window.CPR_REQUEST_HANDLER = {

    submit: async (message) => {
        const res = await fetch('http://localhost:8011/cpr', { method: 'POST', body: JSON.stringify(message) });
        if (!res.ok) {
            throw new Error('Req failed: ' + res.status);
        }
        return res.json();
        // await new Promise(res => setTimeout(res, 100));
        // console.log('incoming message:', message)
        // return {
        //     info: {
        //          version: 'foo', clean: true,
        //     },
        // };
        // // throw new Error("foo");

    },
    on: async (type, callback) => {
        while (true) {
            console.log('GOing to poll for', type);
            const res = await fetch(`http://localhost:8011/poll?type=${type}`);
            if (!res.ok) {
                throw new Error('Poll failed: ' + res.status);
            }
            if (res.status === 200) {
                try {
                    callback();
                } catch (e) {
                    console.warn('poll callback failed??', e);
                }
            } else if (res.status === 204) {
                // No content yet, try again
            } else {
                throw new Error(`Unexpected status from poll: ${res.status}`);
            }
        }
    },
}