

const registerNodeSelector = (elem: HTMLElement, getLocator: () => NodeLocator) => {
  elem.classList.add('nodeLocatorContainer');
  elem.addEventListener('click', (e) => {
    if (!window.ActiveLocatorRequest) {
      return;
    }
    e.stopImmediatePropagation();
    e.stopPropagation();
    e.preventDefault();
    window.ActiveLocatorRequest.submit(getLocator());
  });
};

export default registerNodeSelector;
