const state = {
  query: '',
  animal: null,
  breed: null,
  sex: null,
  ageRange: null,
  priceRange: null
};

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

function renderPets(result) {
  const cards = document.getElementById('cards');
  const hitCount = document.getElementById('hit-count');
  hitCount.textContent = `${result.total} pets found`;
  cards.innerHTML = '';

  result.pets.forEach((pet) => {
    const card = document.createElement('article');
    card.className = 'card';
    const imageUrl = pet.imageUrl || pet.image_url || '/placeholder.svg';
    card.innerHTML = `
      <div class="card-media" style="background-image: url('${imageUrl}')"></div>
      <div class="card-body">
        <div class="card-title">
          <h3>${pet.name}</h3>
          <span class="price">$${pet.price.toFixed(2)}</span>
        </div>
        <p class="muted">${pet.breed} • ${pet.sex.toUpperCase()} • ${pet.age} yrs</p>
        <p>${pet.description}</p>
        <div class="tags">
          ${pet.traits.map((t) => `<span class="tag">${t}</span>`).join('')}
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

  document.getElementById('pet-form').addEventListener('submit', (e) => {
    e.preventDefault();
    createPet(e.target);
  });

  document.getElementById('reload').addEventListener('click', () => reindex());
  document.getElementById('reset').addEventListener('click', () => resetStore());
}

wireUi();
fetchPets();
