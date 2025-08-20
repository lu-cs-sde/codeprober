
const createSquigglyCheckbox = (args: {
  onInput: (checked: boolean) => void;
  initiallyChecked: boolean;
  id?: string;
}) => {
  const squigglyCheckboxWrapper = document.createElement('div');
  squigglyCheckboxWrapper.style.flexDirection = 'column';

  const squigglyCheckbox = document.createElement('input');
  squigglyCheckbox.type = 'checkbox';
  squigglyCheckbox.checked = args.initiallyChecked;
  if (args.id) squigglyCheckbox.id = args.id;
  squigglyCheckboxWrapper.appendChild(squigglyCheckbox);
  squigglyCheckbox.onmousedown = (e) => {
    e.preventDefault();
    e.stopPropagation();
  }
  squigglyCheckbox.oninput = (e) => {
    e.preventDefault();
    e.stopPropagation();
    args.onInput(squigglyCheckbox.checked);
  }

  // Based on https://stackoverflow.com/a/27764538
  const squigglyDemo = document.createElement('div');
  squigglyDemo.classList.add('squigglyLineHolder')
  const addTiny = (type: string) => {
    const tiny = document.createElement('div');
    tiny.classList.add('tinyLine');
    tiny.classList.add(type);
    squigglyDemo.appendChild(tiny);
  }
  addTiny('tinyLine1');
  addTiny('tinyLine2');
  squigglyCheckboxWrapper.appendChild(squigglyDemo);
  return squigglyCheckboxWrapper;
}

export default createSquigglyCheckbox;
