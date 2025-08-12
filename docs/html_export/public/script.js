document.addEventListener("DOMContentLoaded", () => {
  document.body.setAttribute('data-filename', window.location.pathname.split('/').pop());
  document.querySelectorAll('#TOPTOC a').forEach(toplink => {
    const hrefWithoutHash = window.location.href.split('#')[0];;
    if (toplink.href === hrefWithoutHash || toplink.href.endsWith('index.html') && hrefWithoutHash.endsWith('/')) {
      toplink.classList.add('active');
    }
  })
  const links = document.querySelectorAll("#TOC a[href^='#']");
  const sections = Array.from(links)
    .map(link => document.querySelector(link.getAttribute("href")))
    .filter(Boolean);

  function onScroll() {
    const scroller = document.documentElement;
    const scrollTop = scroller.scrollTop;
    const scrollBot = scrollTop + scroller.clientHeight;
    let current = sections[0];
    for (const sec of sections) {
      if (sec.offsetTop >= scrollBot) {
        break;
      }
      current = sec;
      if (sec.offsetTop >= scrollTop) {
        break;
      }
    }

    links.forEach(link =>
      link.classList.toggle("active", link.getAttribute("href") === `#${current.id}`)
    );
  }

  document.addEventListener("scroll", onScroll, { passive: true });
  onScroll(); // run on load
});
