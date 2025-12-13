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
const charts = {};

function buildSearchParams() {
  const params = new URLSearchParams();
  if (state.query) params.set('q', state.query);
  if (state.animal) params.set('animal', state.animal);
  if (state.breed) params.set('breed', state.breed);
  if (state.sex) params.set('sex', state.sex);
  if (state.ageRange) params.set('ageRange', state.ageRange);
  if (state.priceRange) params.set('priceRange', state.priceRange);
  return params;
}

async function fetchPets(params) {
  const response = await fetch(`/api/pets/search?${params.toString()}`);
  const data = await response.json();
  renderPets(data);
  renderFacets(data.facets);
}

async function fetchDashboard(params) {
  const response = await fetch(`/api/pets/dashboard?${params.toString()}`);
  const data = await response.json();
  renderDashboard(data);
}

function refreshData() {
  const params = buildSearchParams();
  fetchPets(params);
  fetchDashboard(params);
}

function escapeHtml(text) {
  const div = document.createElement('div');
  div.textContent = text ?? '';
  return div.innerHTML;
}

function firstHighlight(highlights) {
  if (!highlights) return null;
  const preferredOrder = ['name', 'breed', 'animal', 'description', 'traits'];
  for (const field of preferredOrder) {
    const fragments = highlights[field];
    if (fragments && fragments.length > 0) {
      return fragments[0];
    }
  }
  return null;
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
    const nameHtml = escapeHtml(pet.name);
    const snippetHtml = firstHighlight(highlights);
    const descriptionHtml = escapeHtml(pet.description);

    card.innerHTML = `
      <div class="card-media" style="background-image: url('${imageUrl}')"></div>
      <div class="card-body">
        <div class="card-title">
          <h3>${nameHtml}</h3>
          <span class="price">$${pet.price.toFixed(2)}</span>
        </div>
        ${snippetHtml ? `<p class="highlight-snippet">${snippetHtml}</p>` : ''}
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
      refreshData();
    };
    container.appendChild(pill);
  });
}

const PALETTE = ['#7c3aed', '#22d3ee', '#fbbf24', '#34d399', '#f472b6', '#60a5fa', '#e879f9', '#a3e635'];

function getChart(id) {
  if (!charts[id]) {
    charts[id] = echarts.init(document.getElementById(id));
  }
  return charts[id];
}

function renderDashboard(data) {
  renderPie('chart-animals', data.animals);
  renderLine('chart-price', data.priceHistogram);
  renderBar('chart-age', data.ageHistogram, false);
  renderBar('chart-breeds', data.breeds.slice(0, 12), true);
  renderColumn('chart-avg-price', data.avgPriceByAnimal);
  renderRose('chart-traits', data.traits.slice(0, 12));
}

function renderPie(id, buckets) {
  const chart = getChart(id);
  chart.setOption({
    color: PALETTE,
    tooltip: { trigger: 'item' },
    legend: { bottom: 0, textStyle: { color: '#e2e8f0' } },
    series: [
      {
        name: 'Animals',
        type: 'pie',
        radius: ['35%', '70%'],
        roseType: 'radius',
        data: buckets.map((b) => ({ name: b.label, value: b.value })),
        label: { color: '#e2e8f0' }
      }
    ]
  });
}

function renderLine(id, buckets) {
  const chart = getChart(id);
  chart.setOption({
    color: ['#22d3ee'],
    tooltip: { trigger: 'axis' },
    grid: { left: 40, right: 16, bottom: 30, top: 16 },
    xAxis: {
      type: 'category',
      boundaryGap: false,
      data: buckets.map((b) => b.label),
      axisLabel: { color: '#94a3b8', rotate: 30 },
      axisLine: { lineStyle: { color: '#1f2937' } }
    },
    yAxis: {
      type: 'value',
      axisLabel: { color: '#94a3b8' },
      splitLine: { lineStyle: { color: '#1f2937' } }
    },
    series: [
      {
        name: 'Price bucket',
        type: 'line',
        smooth: true,
        areaStyle: {
          color: new echarts.graphic.LinearGradient(0, 0, 0, 1, [
            { offset: 0, color: 'rgba(34,211,238,0.45)' },
            { offset: 1, color: 'rgba(34,211,238,0.05)' }
          ])
        },
        data: buckets.map((b) => b.value)
      }
    ]
  });
}

function renderBar(id, buckets, horizontal = false) {
  const chart = getChart(id);
  chart.setOption({
    color: ['#7c3aed'],
    tooltip: { trigger: 'axis' },
    grid: { left: 50, right: 16, bottom: 40, top: 16 },
    xAxis: horizontal
      ? { type: 'value', axisLabel: { color: '#94a3b8' }, splitLine: { lineStyle: { color: '#1f2937' } } }
      : { type: 'category', data: buckets.map((b) => b.label), axisLabel: { color: '#94a3b8', rotate: 30 } },
    yAxis: horizontal
      ? { type: 'category', data: buckets.map((b) => b.label), axisLabel: { color: '#94a3b8' } }
      : { type: 'value', axisLabel: { color: '#94a3b8' }, splitLine: { lineStyle: { color: '#1f2937' } } },
    series: [
      {
        type: 'bar',
        barWidth: '55%',
        data: buckets.map((b) => b.value),
        showBackground: true,
        backgroundStyle: { color: 'rgba(255,255,255,0.04)' }
      }
    ]
  });
}

function renderColumn(id, buckets) {
  const chart = getChart(id);
  chart.setOption({
    color: ['#f59e0b'],
    tooltip: { trigger: 'axis', formatter: '{b}: ${c}' },
    grid: { left: 40, right: 16, bottom: 32, top: 16 },
    xAxis: {
      type: 'category',
      data: buckets.map((b) => b.label),
      axisLabel: { color: '#94a3b8', rotate: 25 }
    },
    yAxis: {
      type: 'value',
      axisLabel: { color: '#94a3b8', formatter: '${value}' },
      splitLine: { lineStyle: { color: '#1f2937' } }
    },
    series: [
      {
        type: 'bar',
        data: buckets.map((b) => b.value),
        barWidth: '50%',
        itemStyle: { borderRadius: [4, 4, 0, 0] }
      }
    ]
  });
}

function renderRose(id, buckets) {
  const chart = getChart(id);
  chart.setOption({
    color: PALETTE,
    tooltip: { trigger: 'item' },
    legend: { bottom: 0, textStyle: { color: '#e2e8f0' } },
    series: [
      {
        name: 'Traits',
        type: 'pie',
        radius: ['25%', '70%'],
        roseType: 'area',
        data: buckets.map((b) => ({ name: b.label, value: b.value })),
        label: { color: '#e2e8f0' }
      }
    ]
  });
}

window.addEventListener('resize', () => {
  Object.values(charts).forEach((chart) => chart.resize());
});

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
  refreshData();
}

async function reindex() {
  const button = document.getElementById('reload');
  button.disabled = true;
  button.textContent = 'Reindexing…';
  await fetch('/api/pets/reindex', { method: 'POST' });
  button.textContent = 'Reindex search';
  button.disabled = false;
  refreshData();
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
  refreshData();
}

function wireUi() {
  document.getElementById('search-form').addEventListener('submit', (e) => {
    e.preventDefault();
    state.query = document.getElementById('query').value;
    refreshData();
  });

  document.getElementById('query').addEventListener('input', (e) => {
    state.query = e.target.value;
    if (typingTimeout) {
      clearTimeout(typingTimeout);
    }
    typingTimeout = setTimeout(refreshData, DEBOUNCE_MS);
  });

  document.getElementById('pet-form').addEventListener('submit', (e) => {
    e.preventDefault();
    createPet(e.target);
  });

  document.getElementById('reload').addEventListener('click', () => reindex());
  document.getElementById('reset').addEventListener('click', () => resetStore());
}

wireUi();
refreshData();
