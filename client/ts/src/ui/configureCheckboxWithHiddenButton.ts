
const configureCheckboxWithHiddenButton = (
  checkbox: HTMLInputElement,
  button: HTMLButtonElement,
  onCheckboxChange: (checked: boolean) => void,
  displayEditor: (onClose: () => void) => ({ forceClose: () => void }),
  getButtonDecoration: () => string | null,
) => {
  checkbox.checked = getButtonDecoration() !== null;
  let overrideEditorCloser: (() => void) | null = null;
  const refreshButton = () => {
    const decoration = getButtonDecoration();
    if (decoration === null) {
      button.style.display = 'none';
    } else {
      button.style.display = 'inline-block';
      button.innerText = decoration;
    }
  };
  refreshButton();

  button.onclick = () => {
    button.disabled = true;
    const { forceClose } = displayEditor(() => {
      button.disabled = false;
      overrideEditorCloser = null;
    });
    overrideEditorCloser = () => forceClose();
  };

  checkbox.oninput = (e) => {
    overrideEditorCloser?.();
    onCheckboxChange(checkbox.checked);
  }
  return { refreshButton };
}

export default configureCheckboxWithHiddenButton;
