
window.defineEditor(
  'CodeMirror6Alpha',
  () => ({
    script: [ 'cm6/cm6.js', ],
    style: [ 'cm6/cm6.css', ],
    predicate: () => 'CodeMirror6Alpha' in window,
  }),
  (value, onChange) => {
    const inw = document.getElementById('input-wrapper');
    inw.innerHTML = '';

    const { dom, setLocalState, getLocalState, markText, setLight } = CodeMirror6Alpha(value, onChange);

    inw.appendChild(dom);

    return {
      setLocalState,
      getLocalState,
      markText,
      themeToggler: setLight,
    };
  },
);
