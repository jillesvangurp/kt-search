const state = {
  query: '',
  animal: null,
  breed: null,
  sex: null,
  ageRange: null,
  priceRange: null
};

let typingTimeout = null;
const DEBOUNCE_MS = 200;

async function fetchPets() {
  const params = new URLSearchParams();
  if (state.query) params.set('q', state.query);
  if (state.animal) params.set('animal', state.animal);
  if (state.breed) params.set('breed', state.breed);
  if (state.sex) params.set('sex', state.sex);
  if (state.ageRange) params.set('ageRange', state.ageRange);
  if (state.priceRange) params.set('priceRange', state.priceRange);

  const response = await fetch(`/api/pets/search?${params.toString()}`);
  const data = await response.json();
  renderPets(data);
  renderFacets(data.facets);
}

function escapeHtml(text) {
  const div = document.createElement('div');
  div.textContent = text ?? '';
  return div.innerHTML;
}

function highlighted(field, highlights, fallback) {
  const list = highlights?.[field];
  if (list && list.length > 0) {
    return list[0];
  }
  return escapeHtml(fallback);
}

function renderPets(result) {
  const cards = document.getElementById('cards');
  const hitCount = document.getElementById('hit-count');
  hitCount.textContent = `${result.total} pets in ${result.tookMs} ms`;
  cards.innerHTML = '';

  result.results.forEach(({ pet, highlights }) => {
    const card = document.createElement('article');
    card.className = 'card';
    const imageUrl = pet.imageUrl || pet.image_url || '/placeholder.svg';
    const nameHtml = highlighted('name', highlights, pet.name);
    const descriptionHtml = highlighted('description', highlights, pet.description);

    card.innerHTML = `
      <div class="card-media" style="background-image: url('${imageUrl}')"></div>
      <div class="card-body">
        <div class="card-title">
          <h3>${nameHtml}</h3>
          <span class="price">$${pet.price.toFixed(2)}</span>
        </div>
        <p class="muted">${escapeHtml(pet.breed)} • ${escapeHtml(pet.sex.toUpperCase())} • ${escapeHtml(String(pet.age))} yrs</p>
        <p>${descriptionHtml}</p>
        <div class="tags">
          ${pet.traits.map((t) => `<span class="tag">${escapeHtml(t)}</span>`).join('')}
        </div>
        <div class="links">
          ${pet.wikipediaUrl ? `<a href="${pet.wikipediaUrl}" target="_blank">Wikipedia</a>` : ''}
        </div>
      </div>
    `;
    cards.appendChild(card);
  });
}

function renderFacets(facets) {
  renderPills('animal-filters', facets.animals, 'animal');
  renderPills('breed-filters', facets.breeds, 'breed');
  renderPills('sex-filters', facets.sexes, 'sex');
  renderPills('age-filters', facets.ageRanges, 'ageRange');
  renderPills('price-filters', facets.priceRanges, 'priceRange');
}

function renderPills(targetId, values, key) {
  const container = document.getElementById(targetId);
  container.innerHTML = '';
  Object.entries(values).forEach(([value, count]) => {
    const pill = document.createElement('button');
    pill.type = 'button';
    pill.className = `pill ${state[key] === value ? 'active' : ''}`;
    pill.textContent = `${value} (${count})`;
    pill.onclick = () => {
      state[key] = state[key] === value ? null : value;
      fetchPets();
    };
    container.appendChild(pill);
  });
}

async function createPet(form) {
  const payload = Object.fromEntries(new FormData(form).entries());
  payload.age = Number(payload.age);
  payload.price = Number(payload.price);
  payload.traits = payload.traits ? payload.traits.split(',').map((t) => t.trim()).filter(Boolean) : [];
  payload.id = '';

  await fetch('/api/pets', {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify(payload)
  });

  form.reset();
  fetchPets();
}

async function reindex() {
  const button = document.getElementById('reload');
  button.disabled = true;
  button.textContent = 'Reindexing…';
  await fetch('/api/pets/reindex', { method: 'POST' });
  button.textContent = 'Reindex search';
  button.disabled = false;
  fetchPets();
}

async function resetStore() {
  const button = document.getElementById('reset');
  button.disabled = true;
  const original = button.textContent;
  button.textContent = 'Resetting…';
  await fetch('/api/pets/reset', { method: 'POST' });
  button.textContent = original;
  button.disabled = false;
  state.query = '';
  document.getElementById('query').value = '';
  fetchPets();
}

function wireUi() {
  document.getElementById('search-form').addEventListener('submit', (e) => {
    e.preventDefault();
    state.query = document.getElementById('query').value;
    fetchPets();
  });

  document.getElementById('query').addEventListener('input', (e) => {
    state.query = e.target.value;
    if (typingTimeout) {
      clearTimeout(typingTimeout);
    }
    typingTimeout = setTimeout(fetchPets, DEBOUNCE_MS);
  });

  document.getElementById('pet-form').addEventListener('submit', (e) => {
    e.preventDefault();
    createPet(e.target);
  });

  document.getElementById('reload').addEventListener('click', () => reindex());
  document.getElementById('reset').addEventListener('click', () => resetStore());
}

wireUi();
fetchPets();
