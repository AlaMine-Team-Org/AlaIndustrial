(() => {
  const toggle = document.querySelector('.nav-toggle');
  const sidebar = document.getElementById('wiki-sidebar');
  if (toggle && sidebar) {
    const close = () => { document.body.classList.remove('nav-open'); toggle.setAttribute('aria-expanded', 'false'); };
    toggle.addEventListener('click', () => {
      const open = document.body.classList.toggle('nav-open');
      toggle.setAttribute('aria-expanded', String(open));
    });
    document.addEventListener('keydown', event => { if (event.key === 'Escape') close(); });
    sidebar.addEventListener('click', event => { if (event.target.closest('a')) close(); });
  }
  const search = document.getElementById('wiki-search');
  if (search) {
    const cards = [...document.querySelectorAll('[data-search]')];
    const empty = document.getElementById('empty-search');
    const filter = () => {
      const query = search.value.trim().toLocaleLowerCase(document.documentElement.lang || 'ru');
      let visible = 0;
      cards.forEach(card => {
        const match = !query || (card.dataset.search + ' ' + card.textContent).toLocaleLowerCase().includes(query);
        card.hidden = !match;
        if (match) visible++;
      });
      if (empty) empty.style.display = visible ? 'none' : 'block';
    };
    search.addEventListener('input', filter);
    document.addEventListener('keydown', event => {
      if ((event.ctrlKey || event.metaKey) && event.key.toLowerCase() === 'k') {
        event.preventDefault(); search.focus();
      }
    });
  }
})();
