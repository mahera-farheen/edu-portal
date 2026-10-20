'use strict';
const $ = (s, r = document) => r.querySelector(s);
const esc = s => String(s ?? '').replace(/[&<>"']/g, c => ({ '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;' }[c]));
const safeUrl = u => /^(https?:\/\/|\/api\/files\/)/i.test(u || '') ? u : '#';
const TYPES = ['PDF', 'VIDEO', 'LINK', 'NOTES', 'SLIDES'];
const ASSESS_TYPES = ['QUIZ', 'ASSIGNMENT', 'EXAM', 'PROJECT'];

let auth = sessionStorage.getItem('auth');
let me = null;
let current = 'dashboard';
const ui = { perfCourse: null, resFilter: { courseId: '', type: '', q: '' } };

/* ---------- API ---------- */
async function api(path, { method = 'GET', body, form } = {}) {
  const headers = {};
  if (auth) headers.Authorization = 'Basic ' + auth;
  let payload;
  if (form) payload = form;
  else if (body) { headers['Content-Type'] = 'application/json'; payload = JSON.stringify(body); }
  const res = await fetch('/api' + path, { method, headers, body: payload });
  const text = await res.text();
  let data = null;
  try { data = text ? JSON.parse(text) : null; } catch { /* non-JSON */ }
  if (!res.ok) throw new Error((data && data.message) || `Request failed (${res.status})`);
  return data;
}
const basic = (email, pw) => btoa(unescape(encodeURIComponent(email.trim().toLowerCase() + ':' + pw)));

/* ---------- helpers ---------- */
let toastTimer;
function toast(msg, isErr) {
  const t = $('#toast');
  t.textContent = msg; t.className = 'show' + (isErr ? ' err' : '');
  clearTimeout(toastTimer);
  toastTimer = setTimeout(() => (t.className = ''), 3500);
}
const level = p => (p >= 75 ? 'high' : p >= 50 ? 'mid' : 'low');
const fillCls = p => (p >= 75 ? '' : p >= 50 ? 'mid' : 'low');
const options = (list, sel) => list.map(([v, l]) => `<option value="${esc(v)}"${String(v) === String(sel) ? ' selected' : ''}>${esc(l)}</option>`).join('');
const bars = rows => rows.length
  ? `<div class="bars">${rows.map(r => `<div><div class="bar-label"><span>${esc(r.label)}</span><span class="pct ${level(r.value)}">${r.value}%</span></div>
      <div class="track"><div class="fill ${fillCls(r.value)}" style="width:${Math.min(r.value, 100)}%"></div></div></div>`).join('')}</div>`
  : '<p class="empty">No grades recorded yet.</p>';
const isStaff = () => me.role === 'TEACHER' || me.role === 'ADMIN';

/* ---------- shell ---------- */
function renderAuth(show = true) {
  $('#auth').hidden = !show; $('#app').hidden = show;
}
function buildNav() {
  const items = [['dashboard', 'Dashboard'], ['courses', 'Courses'], ['resources', 'Resources'], ['performance', me.role === 'STUDENT' ? 'My grades' : 'Performance']];
  if (me.role === 'ADMIN') items.push(['users', 'Users']);
  $('#nav').innerHTML = items.map(([id, l]) => `<button data-action="nav" data-view="${id}">${l}</button>`).join('');
  $('#who-name').textContent = me.name;
  $('#who-role').textContent = me.role.charAt(0) + me.role.slice(1).toLowerCase();
}
async function show(view) {
  current = view;
  document.querySelectorAll('#nav button').forEach(b => b.classList.toggle('active', b.dataset.view === view));
  try { $('#view').innerHTML = await views[view](); }
  catch (e) { $('#view').innerHTML = `<p class="error">${esc(e.message)}</p>`; }
}

/* ---------- views ---------- */
const views = {
  async dashboard() {
    const d = await api('/dashboard');
    let tiles;
    if (me.role === 'STUDENT') tiles = [['Enrolled courses', d.enrolledCourses], ['Graded items', d.gradedItems], ['Overall average', d.average + '%']];
    else {
      tiles = [['Courses', d.courses], ['Enrollments', d.enrollments], ['Graded items', d.gradedItems], ['Average score', d.average + '%']];
      if (me.role === 'ADMIN') tiles.push(['Users', d.users], ['Resources', d.resources]);
    }
    let recent = '';
    if (me.role === 'STUDENT') {
      recent = `<div class="panel"><h3>Latest results</h3>${d.recent.length ? gradeTable(d.recent, false) : '<p class="empty">Nothing graded yet. Enroll in a course to get started.</p>'}</div>`;
    }
    return `<h2>Hello, ${esc(me.name.split(' ')[0])}</h2>
      <div class="stats">${tiles.map(([l, v]) => `<div class="stat"><b>${esc(v)}</b><span>${l}</span></div>`).join('')}</div>
      <div class="panel"><h3>${me.role === 'STUDENT' ? 'Your average by course' : 'Average score by course'}</h3>${bars(d.chart)}</div>${recent}`;
  },

  async courses() {
    const [all, mine] = await Promise.all([api('/courses'), api('/courses/mine')]);
    const mineIds = new Set(mine.map(c => c.id));
    const form = isStaff() ? `<div class="panel"><h3>New course</h3>
      <form data-form="course" class="row"><label>Title<input name="title" required></label>
      <label>Description<input name="description"></label><button class="btn primary">Create course</button></form></div>` : '';
    const cards = all.map(c => `<article class="item"><h3>${esc(c.title)}</h3><p>${esc(c.description)}</p>
      <span class="muted">Taught by ${esc(c.teacherName)}</span>
      <div class="actions">${me.role === 'STUDENT'
        ? (mineIds.has(c.id) ? '<span class="badge">Enrolled</span>' : `<button class="btn primary small" data-action="enroll" data-id="${c.id}">Enroll</button>`)
        : (mineIds.has(c.id) ? '<span class="badge amber">You manage this</span>' : '')}
        <button class="btn small" data-action="openRes" data-id="${c.id}">Resources</button></div></article>`).join('');
    return `<h2>Courses</h2>${form}<div class="grid">${cards || '<p class="empty">No courses yet.</p>'}</div>`;
  },

  async resources() {
    const f = ui.resFilter;
    const qs = new URLSearchParams(Object.entries(f).filter(([, v]) => v)).toString();
    const [list, all, mine] = await Promise.all([api('/resources' + (qs ? '?' + qs : '')), api('/courses'), isStaff() ? api('/courses/mine') : []]);
    const cname = Object.fromEntries(all.map(c => [c.id, c.title]));
    const filter = `<form data-form="resFilter" class="panel row">
      <label>Search<input name="q" value="${esc(f.q)}" placeholder="Title or description"></label>
      <label>Course<select name="courseId"><option value="">All courses</option>${options(all.map(c => [c.id, c.title]), f.courseId)}</select></label>
      <label>Type<select name="type"><option value="">All types</option>${options(TYPES.map(t => [t, t]), f.type)}</select></label>
      <button class="btn">Apply filters</button></form>`;
    const add = isStaff() ? (mine.length ? `<div class="panel"><h3>Add a resource</h3>
      <form data-form="resource"><div class="row">
        <label>Course<select name="courseId" required>${options(mine.map(c => [c.id, c.title]))}</select></label>
        <label>Title<input name="title" required></label>
        <label>Type<select name="type">${options(TYPES.map(t => [t, t]))}</select></label></div>
        <label>Description<input name="description"></label>
        <div class="row"><label>Link (https://…)<input name="url" type="url"></label>
        <label>…or upload a file<input name="file" type="file"></label>
        <button class="btn primary">Add resource</button></div></form></div>`
      : '<div class="panel"><p class="empty">Create a course first, then add resources to it.</p></div>') : '';
    const items = list.map(r => `<article class="item"><span><span class="badge">${esc(r.type)}</span> <span class="muted">${esc(cname[r.courseId] || '')}</span></span>
      <h3>${esc(r.title)}</h3><p>${esc(r.description)}</p>
      <div class="actions"><a class="btn small" href="${esc(safeUrl(r.url))}" target="_blank" rel="noopener">Open</a>
      ${isStaff() ? `<button class="btn danger small" data-action="delResource" data-id="${r.id}">Delete</button>` : ''}</div></article>`).join('');
    return `<h2>Resources</h2>${add}${filter}<div class="grid">${items || '<p class="empty">No resources match. Try clearing the filters.</p>'}</div>`;
  },

  async performance() {
    if (me.role === 'STUDENT') {
      const rows = await api('/grades/me');
      const byCourse = {};
      rows.forEach(r => (byCourse[r.courseTitle] ||= []).push(r.percent));
      const chart = Object.entries(byCourse).map(([label, v]) => ({ label, value: Math.round(v.reduce((a, b) => a + b, 0) / v.length * 10) / 10 }));
      return `<h2>My grades</h2><div class="panel"><h3>Average by course</h3>${bars(chart)}</div>
        <div class="panel"><h3>All results</h3>${rows.length ? gradeTable(rows, false) : '<p class="empty">No grades yet.</p>'}</div>`;
    }
    const mine = await api('/courses/mine');
    if (!mine.length) return '<h2>Performance</h2><p class="empty">Create a course to start recording results.</p>';
    if (!ui.perfCourse || !mine.some(c => c.id == ui.perfCourse)) ui.perfCourse = mine[0].id;
    const id = ui.perfCourse;
    const [assess, students, rows] = await Promise.all([api(`/courses/${id}/assessments`), api(`/courses/${id}/students`), api(`/courses/${id}/grades`)]);
    const byStudent = {};
    rows.forEach(r => (byStudent[r.studentName] ||= []).push(r.percent));
    const chart = Object.entries(byStudent).map(([label, v]) => ({ label, value: Math.round(v.reduce((a, b) => a + b, 0) / v.length * 10) / 10 }));
    return `<h2>Performance</h2>
      <div class="panel"><label>Course<select data-change="perfCourse">${options(mine.map(c => [c.id, c.title]), id)}</select></label></div>
      <div class="panel"><h3>Average by student</h3>${bars(chart)}</div>
      <div class="panel"><h3>New assessment</h3><form data-form="assessment" class="row">
        <input type="hidden" name="courseId" value="${id}">
        <label>Title<input name="title" required></label>
        <label>Type<select name="type">${options(ASSESS_TYPES.map(t => [t, t]))}</select></label>
        <label>Max score<input name="maxScore" type="number" min="1" step="any" value="100" required></label>
        <label>Date<input name="date" type="date"></label><button class="btn primary">Add assessment</button></form></div>
      <div class="panel"><h3>Record a grade</h3>${assess.length && students.length ? `<form data-form="grade" class="row">
        <label>Assessment<select name="assessmentId">${options(assess.map(a => [a.id, `${a.title} (/${a.maxScore})`]))}</select></label>
        <label>Student<select name="studentId">${options(students.map(s => [s.id, s.name]))}</select></label>
        <label>Score<input name="score" type="number" min="0" step="any" required></label>
        <label>Feedback<input name="feedback"></label><button class="btn primary">Save grade</button></form>`
        : '<p class="empty">Add an assessment and make sure students are enrolled before recording grades.</p>'}</div>
      <div class="panel"><h3>Results</h3>${rows.length ? gradeTable(rows, true) : '<p class="empty">No grades recorded for this course yet.</p>'}</div>`;
  },

  async users() {
    const list = await api('/admin/users');
    return `<h2>Users</h2><div class="panel"><h3>Create user</h3><form data-form="user" class="row">
      <label>Name<input name="name" required></label><label>Email<input name="email" type="email" required></label>
      <label>Password<input name="password" type="password" minlength="6" required></label>
      <label>Role<select name="role">${options(['TEACHER', 'STUDENT', 'ADMIN'].map(r => [r, r]))}</select></label>
      <button class="btn primary">Create user</button></form></div>
      <div class="panel table-wrap"><table><thead><tr><th>Name</th><th>Email</th><th>Role</th></tr></thead><tbody>
      ${list.map(u => `<tr><td>${esc(u.name)}</td><td>${esc(u.email)}</td><td><span class="badge">${esc(u.role)}</span></td></tr>`).join('')}</tbody></table></div>`;
  },
};

function gradeTable(rows, withStudent) {
  return `<div class="table-wrap"><table><thead><tr>${withStudent ? '<th>Student</th>' : '<th>Course</th>'}<th>Assessment</th><th>Score</th><th>Result</th><th>Feedback</th></tr></thead><tbody>
    ${rows.map(r => `<tr><td>${esc(withStudent ? r.studentName : r.courseTitle)}</td><td>${esc(r.assessmentTitle)} <span class="muted">${esc(r.type)}</span></td>
    <td>${r.score} / ${r.maxScore}</td><td><span class="pct ${level(r.percent)}">${r.percent}%</span></td><td>${esc(r.feedback)}</td></tr>`).join('')}</tbody></table></div>`;
}

/* ---------- actions ---------- */
const actions = {
  nav: el => show(el.dataset.view),
  authTab(el) {
    document.querySelectorAll('.tab').forEach(t => t.classList.toggle('active', t === el));
    $('#login-form').hidden = el.dataset.tab !== 'login';
    $('#register-form').hidden = el.dataset.tab !== 'register';
    $('#auth-error').textContent = '';
  },
  logout() { sessionStorage.removeItem('auth'); auth = null; me = null; renderAuth(true); },
  async enroll(el) { await api(`/courses/${el.dataset.id}/enroll`, { method: 'POST' }); toast('Enrolled'); show('courses'); },
  openRes(el) { ui.resFilter = { courseId: el.dataset.id, type: '', q: '' }; show('resources'); },
  async delResource(el) {
    if (!confirm('Delete this resource?')) return;
    await api(`/resources/${el.dataset.id}`, { method: 'DELETE' }); toast('Deleted'); show('resources');
  },
};

const changes = { perfCourse(el) { ui.perfCourse = el.value; show('performance'); } };

const forms = {
  async login(f) {
    const d = Object.fromEntries(new FormData(f));
    auth = basic(d.email, d.password);
    try { me = await api('/auth/me'); }
    catch (e) { auth = null; throw new Error('Invalid email or password'); }
    sessionStorage.setItem('auth', auth); start();
  },
  async register(f) {
    const d = Object.fromEntries(new FormData(f));
    await api('/auth/register', { method: 'POST', body: d });
    auth = basic(d.email, d.password); me = await api('/auth/me');
    sessionStorage.setItem('auth', auth); start();
  },
  async course(f) { await api('/courses', { method: 'POST', body: Object.fromEntries(new FormData(f)) }); toast('Course created'); show('courses'); },
  resFilter(f) { ui.resFilter = Object.fromEntries(new FormData(f)); show('resources'); },
  async resource(f) {
    const fd = new FormData(f);
    const file = fd.get('file');
    if (file && file.size) await api('/resources/upload', { method: 'POST', form: fd });
    else {
      if (!fd.get('url')) throw new Error('Add a link or choose a file');
      const body = Object.fromEntries(fd); delete body.file; body.courseId = Number(body.courseId);
      await api('/resources', { method: 'POST', body });
    }
    toast('Resource added'); show('resources');
  },
  async assessment(f) {
    const d = Object.fromEntries(new FormData(f));
    d.courseId = Number(d.courseId); d.maxScore = Number(d.maxScore); if (!d.date) delete d.date;
    await api('/assessments', { method: 'POST', body: d }); toast('Assessment added'); show('performance');
  },
  async grade(f) {
    const d = Object.fromEntries(new FormData(f));
    d.assessmentId = Number(d.assessmentId); d.studentId = Number(d.studentId); d.score = Number(d.score);
    await api('/grades', { method: 'POST', body: d }); toast('Grade saved'); show('performance');
  },
  async user(f) { await api('/admin/users', { method: 'POST', body: Object.fromEntries(new FormData(f)) }); toast('User created'); show('users'); },
};

document.addEventListener('click', async e => {
  const el = e.target.closest('[data-action]');
  if (!el) return;
  try { await actions[el.dataset.action](el); } catch (err) { toast(err.message, true); }
});
document.addEventListener('change', async e => {
  const el = e.target.closest('[data-change]');
  if (el) try { await changes[el.dataset.change](el); } catch (err) { toast(err.message, true); }
});
document.addEventListener('submit', async e => {
  const f = e.target.closest('form[data-form]');
  if (!f) return;
  e.preventDefault();
  const authForm = f.dataset.form === 'login' || f.dataset.form === 'register';
  try { await forms[f.dataset.form](f); }
  catch (err) { if (authForm) $('#auth-error').textContent = err.message; else toast(err.message, true); }
});

/* ---------- boot ---------- */
function start() { renderAuth(false); buildNav(); show('dashboard'); }
(async function init() {
  if (auth) {
    try { me = await api('/auth/me'); start(); return; } catch { auth = null; sessionStorage.removeItem('auth'); }
  }
  renderAuth(true);
})();
