
interface CheckboxConfig {
  checkbox: HTMLInputElement;
  initiallyChecked: boolean;
  onChange: (checked: boolean) => void;
}
const configureCheckboxWithHiddenCheckbox = (
  outer: CheckboxConfig,
  hidden: CheckboxConfig & { container: HTMLElement },
) => {
  outer.checkbox.checked = outer.initiallyChecked;
  hidden.checkbox.checked = hidden.initiallyChecked;

  const refreshHidden = () => {
    if (outer.checkbox.checked) {
      hidden.container.style.display = 'block';
    } else {
      hidden.container.style.display = 'none';
    }
  }
  refreshHidden();

  outer.checkbox.oninput = (e) => {
    refreshHidden();
    outer.onChange(outer.checkbox.checked);
  };
  hidden.checkbox.oninput = (e) => {
    hidden.onChange(hidden.checkbox.checked);
  };
}

export default configureCheckboxWithHiddenCheckbox;
