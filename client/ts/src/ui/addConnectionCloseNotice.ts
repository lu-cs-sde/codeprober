const showConnectionCloseNotice = (didReceiveAtLeastOneMessage: boolean) => {
  const ch = document.createElement('div');
  const msg = didReceiveAtLeastOneMessage
    ? 'Lost connection to server, reload to reconnect'
    : 'Couldn\'t connect to server';
  ch.innerHTML = `
  <div style="
    position: absolute;
    top: 4rem;
    left: 20%;
    right: 20%;
    background: #300;
    color: white;
    border: 1px solid red;
    border-radius: 1rem;
    padding: 2rem;
    font-size: 2rem;
    z-index: ${Number.MAX_SAFE_INTEGER};
    text-align: center">
   ${msg}
  </div>
  `
  document.body.appendChild(ch);
};

export default showConnectionCloseNotice;
