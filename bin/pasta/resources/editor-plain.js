window.defineEditor(
  'Plain',
  () => ({ script: [], style: [], predicate: () => true, }),
  (value, onChange) => {
    input = document.getElementById('input');
    input.value = value;

    input.oninput = () => onChange(input.value);
    return {
      setLocalState: (value) => input.value = value,
      getLocalState: () => input.value,
      themeToggler: light => input.setAttribute('data-theme-light', `${light}`),
    };
  },
);
