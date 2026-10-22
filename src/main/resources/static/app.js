'use strict';
const $ = id => document.getElementById(id);
const esc = value => String(value ?? '').replace(/[&<>"']/g, char => ({'&':'&amp;','<':'&lt;','>':'&gt;','"':'&quot;',"'":'&#39;'}[char]));

let state, draft, pending = false, toastTimer;

async function api(path, method = 'GET', body) {
  const controller = new AbortController(), timer = setTimeout(() => controller.abort(), 20000);
  let response;
  try { response = await fetch('/api' + path, {method, signal:controller.signal, headers: body === undefined ? {} : {'Content-Type':'application/json'}, body: body === undefined ? undefined : JSON.stringify(body)}); }
  catch (error) { throw new Error(error.name === 'AbortError' ? '请求超时，保存结果待确认，请刷新页面核对后重试' : '无法连接服务，请检查服务是否正在运行'); }
  finally { clearTimeout(timer); }
  if (!response.ok) {
    const error = await response.json().catch(() => ({}));
    throw new Error(error.message || `请求失败（${response.status}）`);
  }
  return response.json();
}

function toast(message) {
  clearTimeout(toastTimer); $('toast').textContent = message; $('toast').hidden = false;
  toastTimer = setTimeout(() => { $('toast').hidden = true; }, 4500);
}

// 一次只提交一个操作；点击计数到写入/重绘完成期间禁用按钮，防止双击重复记数。
async function run(action, message) {
  if (pending) return;
  pending = true;
  document.querySelectorAll('button').forEach(button => { button.dataset.wasDisabled = String(button.disabled); button.disabled = true; });
  try { await action(); if (message) toast(state?.renderError ? '数据已保存，但图片生成失败，请查看顶部提示。' : message); }
  catch (error) { toast(error.message); }
  finally {
    pending = false;
    document.querySelectorAll('button').forEach(button => { button.disabled = button.dataset.wasDisabled === 'true'; delete button.dataset.wasDisabled; });
    movementButtons();
  }
}

function time(value, options = {}) {
  return value ? new Intl.DateTimeFormat('zh-CN', {timeZone:state.settings.timezone, month:'2-digit', day:'2-digit', hour:'2-digit', minute:'2-digit', ...options}).format(new Date(value)) : '暂无';
}

async function load(initial = false) {
  const previousDate = state?.date;
  state = await api('/state');
  document.title = state.settings.title + ' · Kindle 看板';
  if (!initial && previousDate !== state.date) {
    if ($('todoDate').value === previousDate) $('todoDate').value = state.date;
    if ($('movementDate').value === previousDate) $('movementDate').value = state.date;
  }
  $('dateStamp').textContent = state.date + ' · ' + state.settings.timezone;
  $('gestation').textContent = state.pregnancy.gestation;
  $('babyCountdown').textContent = state.pregnancy.countdown === '待设置' ? '设置预产期，开始记录期待。' : state.pregnancy.countdown;
  $('imageMeta').textContent = `${state.settings.canvasWidth} × ${state.settings.canvasHeight} px · ${state.imageAt ? '生成于 ' + time(state.imageAt) : '尚未生成'}`;
  $('paper').style.aspectRatio = `${state.settings.canvasWidth}/${state.settings.canvasHeight}`;
  if (state.imageAt) $('boardImage').src = '/api/image?v=' + encodeURIComponent(state.imageAt);
  $('errorBanner').hidden = !state.renderError;
  $('errorBanner').textContent = state.renderError + (state.font !== '中文字体可用' ? ' ' + state.font : '');
  const weather = state.weather;
  $('weatherSummary').textContent = weather.lastSuccessAt ? `${weather.cityName} · ${weather.temperature}°C · ${state.weatherDescription}${state.weatherCached ? ' · 缓存' : ''}` : state.settings.weather.cityName + ' · 天气暂不可用';
  $('weatherTime').textContent = weather.lastSuccessAt ? `上次成功：${time(weather.lastSuccessAt)} · ${weather.message}` : weather.message;
  $('todayTaps').textContent = state.movements.count;
  $('sessionTaps').textContent = state.activeSession ? state.activeSession.taps.length : '—';
  $('sessionStatus').textContent = state.activeSession ? '正在计数' : '尚未开始';
  if (initial) {
    $('todoDate').value = state.date; $('movementDate').value = state.date;
    draft = await api('/content');
    fillSettings(); renderCustom();
  }
  await Promise.all([loadTodos(), loadHistory()]);
  movementButtons();
}

function movementButtons() {
  if (!state || pending) return;
  const active = state.activeSession;
  $('startSession').disabled = !!active;
  $('tapSession').disabled = !active;
  $('undoSession').disabled = !active || !active.taps.length;
  $('endSession').disabled = !active;
}

function showTab(tab) {

  document.querySelectorAll('[data-panel]').forEach(panel => { panel.hidden = panel.dataset.panel !== tab; });
  document.querySelectorAll('[data-tab]').forEach(button => { button.classList.toggle('active', button.dataset.tab === tab); button.setAttribute('aria-pressed', String(button.dataset.tab === tab)); });


  if (tab === 'custom') renderCustom();
}

async function loadTodos() {
  const date = $('todoDate').value || state.date;
  const todos = await api('/todos?date=' + date);
  $('todoList').replaceChildren();
  if (!todos.length) { $('todoList').innerHTML = '<p class="empty">留白也很好，今天还没有安排。</p>'; return; }
  todos.forEach(todo => {
    const row = document.createElement('div'); row.className = 'todo-row' + (todo.done ? ' done' : '');
    row.innerHTML = `<input type="checkbox" aria-label="${esc(todo.content)}：完成状态" ${todo.done ? 'checked' : ''}><span class="content">${esc(todo.content)}</span><button class="quiet edit">编辑</button><button class="quiet delete">删除</button>`;
    row.querySelector('input').addEventListener('change', event => run(async () => { await api('/todos/' + todo.id, 'PUT', {...todo, done:event.target.checked}); await load(); }, '安排已更新'));
    row.querySelector('.edit').onclick = () => {
      const content = prompt('修改安排内容（最多 500 字）', todo.content);
      if (content !== null) run(async () => { await api('/todos/' + todo.id, 'PUT', {...todo, content}); await load(); }, '安排已更新');
    };
    row.querySelector('.delete').onclick = () => {
      if (confirm('删除这条安排？')) run(async () => { await api('/todos/' + todo.id, 'DELETE'); await load(); }, '安排已删除');
    };
    $('todoList').append(row);
  });
}

async function loadHistory() {
  const result = await api('/movements?date=' + ($('movementDate').value || state.date));
  $('movementHistory').innerHTML = `<p class="muted">当日合计 ${result.count} 次 · ${result.sessions.length} 段会话</p>`;
  result.sessions.forEach(session => {
    const box = document.createElement('div'); box.className = 'history-card';
    box.innerHTML = `<strong>${esc(time(session.startedAt))} — ${session.endedAt ? esc(time(session.endedAt)) : '计数中'}</strong><p>整段会话 ${session.taps.length} 次</p><p class="taps">点击时间：${session.taps.length ? session.taps.map(tap => esc(time(tap.at, {second:'2-digit'}))).join('、') : '暂无点击'}</p><label>会话备注<textarea maxlength="500" aria-label="会话备注">${esc(session.note)}</textarea></label><button class="quiet">保存备注</button>`;
    box.querySelector('button').onclick = () => run(async () => { await api('/movements/' + session.id + '/note', 'PUT', {note:box.querySelector('textarea').value}); await load(); }, '备注已保存');
    $('movementHistory').append(box);
  });
}

function fillSettings() {
  const settings = state.settings;
  $('orientation').value = settings.canvasWidth > settings.canvasHeight ? 'landscape' : 'portrait';
  const values = {...settings, ...settings.weather, dueDate:settings.dueDate || ''};
  for (const [name, value] of Object.entries(values)) if ($('settingsForm').elements.namedItem(name)) $('settingsForm').elements.namedItem(name).value = value;
}

// 固定模板只编辑文字与日期，位置和尺寸由服务端保持与参考图一致。
function renderCustom() {
  if (!draft) return;
  $('customCards').replaceChildren();
  const labels = ['下次产检信息', '今日提醒 · 左', '今日提醒 · 中', '今日提醒 · 右', '底部寄语'];
  draft.forEach((card, index) => {
    if (index > 0 && index < 4) return; // 三条提醒只保留历史数据，不再显示编辑入口。
    const form = document.createElement('form'); form.className = 'custom-card';
    form.innerHTML = `<h3>${labels[index]}</h3>${index < 4 ? `<div class="field"><label>标题<input name="title" maxlength="80" value="${esc(card.title)}"></label></div>` : ''}<div class="field"><label>${index === 0 ? '地点 / 补充说明' : '正文'}<textarea name="text" maxlength="5000">${esc(card.text)}</textarea></label></div>${index > 0 && index < 4 ? `<div class="field"><label>可选倒计时日期<input name="targetDate" type="date" value="${esc(card.targetDate)}"></label><small>留空显示正文；填写后，此处显示目标日期倒计时。</small></div>` : ''}<button type="submit" class="primary">保存内容并生成</button>`;
    form.onsubmit = event => {
      event.preventDefault(); const data = new FormData(form);
      if (index < 4) card.title = data.get('title');
      card.text = data.get('text');
      if (index > 0 && index < 4) card.targetDate = data.get('targetDate') || null;
      run(async () => { await api('/content', 'PUT', draft); await load(); draft = await api('/content'); renderCustom(); }, '内容已保存，固定模板图片已更新');
    };
    $('customCards').append(form);
  });
}
document.querySelectorAll('[data-tab]').forEach(button => { button.onclick = () => showTab(button.dataset.tab); });
$('goDueDate').onclick = () => { showTab('settings'); $('dueDate').focus(); };
$('todoForm').onsubmit = event => {
  event.preventDefault();
  run(async () => { const todos = await api('/todos?date=' + $('todoDate').value); const sort = Math.max(0, ...todos.map(todo => todo.sort)) + 10; await api('/todos','POST',{date:$('todoDate').value,content:$('todoContent').value,done:false,sort}); $('todoContent').value = ''; await load(); }, '安排已保存，图片已更新');
};
$('todoDate').onchange = () => run(loadTodos);
$('movementDate').onchange = () => run(loadHistory);
$('startSession').onclick = () => run(async () => { await api('/movements/start','POST'); await load(); }, '开始计数');
for (const [id, action, message] of [['tapSession','tap','已记录一次'],['undoSession','undo','已撤销最近一次'],['endSession','end','本次计数已结束']]) {
  $(id).onclick = () => {
    if (!state.activeSession || action === 'undo' && !confirm('撤销当前会话最近一次点击？')) return;
    run(async () => { await api('/movements/' + state.activeSession.id + '/' + action,'POST'); await load(); }, message);
  };
}
// 方向沿用既有宽高保存，防止方向标识与输出分辨率互相矛盾。
function syncOrientation() {
  $('orientation').value = Number($('canvasWidth').value) > Number($('canvasHeight').value) ? 'landscape' : 'portrait';
}
$('orientation').onchange = () => {
  const width = $('canvasWidth'), height = $('canvasHeight');
  if (($('orientation').value === 'landscape') !== (Number(width.value) > Number(height.value))) {
    [width.value, height.value] = [height.value, width.value];
  }
  syncOrientation();
};
$('canvasWidth').oninput = syncOrientation;
$('canvasHeight').oninput = syncOrientation;
$('settingsForm').onsubmit = event => {
  event.preventDefault(); const data = new FormData(event.target);
  const settings = {title:data.get('title'),timezone:data.get('timezone'),canvasWidth:Number(data.get('canvasWidth')),canvasHeight:Number(data.get('canvasHeight')),dueDate:data.get('dueDate') || null,defaultRenderMinutes:Number(data.get('defaultRenderMinutes')),weather:{cityName:data.get('cityName'),latitude:Number(data.get('latitude')),longitude:Number(data.get('longitude')),refreshMinutes:Number(data.get('refreshMinutes'))}};
  run(async () => { await api('/settings','PUT',settings); await load(); }, '设置已保存；天气位置变更后可点击“刷新天气”');
};
$('renderNow').onclick = () => run(async () => { await api('/render','POST'); await load(); }, '已请求生成，请查看预览状态');
$('refreshWeather').onclick = () => run(async () => { const weather = await api('/weather/refresh','POST'); await load(); toast(weather.message); });
$('boardImage').onerror = () => { $('errorBanner').hidden = false; $('errorBanner').textContent = '图片尚不可用，请检查服务状态、中文字体或点击立即生成。'; };
$('boardImage').onload = () => { $('imageMeta').textContent = `${$('boardImage').naturalWidth} × ${$('boardImage').naturalHeight} px · 生成于 ${time(state.imageAt)}`; };
run(() => load(true));
setInterval(() => { if (state && !pending) run(() => load()); }, 60000);
