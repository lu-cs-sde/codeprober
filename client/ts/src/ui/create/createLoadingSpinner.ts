
const createLoadingSpinner = () => {
  const holder = document.createElement('div');
  holder.classList.add('lds-spinner');
  holder.innerHTML = `<div></div><div></div><div></div><div></div><div></div><div></div><div></div><div></div><div></div><div></div><div></div><div></div>`;
  return holder;
};
export default createLoadingSpinner;
