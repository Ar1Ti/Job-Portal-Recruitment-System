/* Job Portal - single page frontend (vanilla JS) */
const $ = (s, r = document) => r.querySelector(s);
const app = $('#app');
let session = JSON.parse(localStorage.getItem('session') || 'null');

const esc = (v) => String(v ?? '').replace(/[&<>"']/g, c => ({ '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;' }[c]));
const fmtDate = (d) => d ? new Date(d).toLocaleDateString() : '';
const salary = (j) => (j.salaryMin || j.salaryMax) ? `₹${j.salaryMin ?? '?'} – ${j.salaryMax ?? '?'}` : 'Not disclosed';
const tags = (csv) => (csv || '').split(',').map(s => s.trim()).filter(Boolean).map(s => `<span class="tag">${esc(s)}</span>`).join('');
const nullIfEmpty = (v) => (v === '' || v === undefined) ? null : v;

function toast(msg, err = false) {
  const t = $('#toast'); t.textContent = msg; t.className = 'toast' + (err ? ' err' : '');
  clearTimeout(toast.t); toast.t = setTimeout(() => t.classList.add('hidden'), 3500);
}

async function api(path, { method = 'GET', body, form } = {}) {
  const headers = {};
  if (session) headers.Authorization = 'Bearer ' + session.token;
  if (body) headers['Content-Type'] = 'application/json';
  const res = await fetch('/api' + path, { method, headers, body: form || (body ? JSON.stringify(body) : undefined) });
  if (res.status === 401 && session) { logout(); throw new Error('Session expired, please log in again'); }
  const data = await res.json().catch(() => ({}));
  if (!res.ok) throw new Error(data.message || 'Request failed');
  return data;
}
const run = (fn) => fn().catch(e => toast(e.message, true));

function logout() { session = null; localStorage.removeItem('session'); go('jobs'); }

/* ---------- navigation ---------- */
const NAVS = {
  guest: [['jobs', 'Browse Jobs'], ['login', 'Login'], ['register', 'Register']],
  JOB_SEEKER: [['jobs', 'Browse Jobs'], ['recommended', 'Recommended'], ['myApps', 'My Applications'], ['profile', 'Profile']],
  RECRUITER: [['jobs', 'Browse Jobs'], ['myJobs', 'My Jobs'], ['postJob', 'Post Job']],
  ADMIN: [['stats', 'Dashboard'], ['adminUsers', 'Users'], ['adminJobs', 'Jobs'], ['adminApps', 'Applications']],
};
let current = 'jobs';

function renderNav() {
  const items = NAVS[session ? session.role : 'guest'];
  $('#nav').innerHTML = items.map(([k, label]) => `<a data-nav="${k}" class="${k === current ? 'active' : ''}">${label}</a>`).join('')
    + (session ? `<a id="logout">Logout (${esc(session.fullName.split(' ')[0])})</a>` : '');
  const lo = $('#logout'); if (lo) lo.onclick = logout;
}

function go(view, arg) {
  current = view; renderNav();
  const views = { jobs: viewJobs, login: viewLogin, register: viewRegister, recommended: viewRecommended,
    myApps: viewMyApps, profile: viewProfile, myJobs: viewMyJobs, postJob: viewPostJob, applicants: viewApplicants,
    stats: viewStats, adminUsers: viewAdminUsers, adminJobs: viewAdminJobs, adminApps: viewAdminApps };
  run(() => views[view](arg));
}
document.addEventListener('click', (e) => {
  const a = e.target.closest('[data-nav]');
  if (a) { e.preventDefault(); go(a.dataset.nav); }
});

function pager(p, onPage) {
  if (p.totalPages <= 1) return '';
  setTimeout(() => {
    const prev = $('#pgPrev'), next = $('#pgNext');
    if (prev) prev.onclick = () => onPage(p.page - 1);
    if (next) next.onclick = () => onPage(p.page + 1);
  });
  return `<div class="pager"><button class="secondary small" id="pgPrev" ${p.page === 0 ? 'disabled' : ''}>Prev</button>
    <span class="muted">Page ${p.page + 1} of ${p.totalPages} · ${p.totalElements} total</span>
    <button class="secondary small" id="pgNext" ${p.page + 1 >= p.totalPages ? 'disabled' : ''}>Next</button></div>`;
}

/* ---------- auth ---------- */
function viewLogin() {
  app.innerHTML = `<div class="card narrow"><h1>Login</h1>
    <form id="f"><div class="field"><label>Email</label><input name="email" type="email" required></div>
    <div class="field"><label>Password</label><input name="password" type="password" required></div>
    <button>Login</button></form>
</div>`;
  $('#f').onsubmit = (e) => { e.preventDefault(); run(async () => {
    const fd = Object.fromEntries(new FormData(e.target));
    startSession(await api('/auth/login', { method: 'POST', body: fd }));
  }); };
}

function viewRegister() {
  app.innerHTML = `<div class="card narrow"><h1>Create account</h1>
    <form id="f"><div class="field"><label>I am a</label>
      <select name="role" id="role"><option value="JOB_SEEKER">Job Seeker</option><option value="RECRUITER">Recruiter</option></select></div>
    <div class="field"><label>Full name</label><input name="fullName" required></div>
    <div class="field hidden" id="co"><label>Company name</label><input name="companyName"></div>
    <div class="field"><label>Email</label><input name="email" type="email" required></div>
    <div class="field"><label>Password (min 6)</label><input name="password" type="password" minlength="6" required></div>
    <button>Register</button></form></div>`;
  $('#role').onchange = (e) => $('#co').classList.toggle('hidden', e.target.value !== 'RECRUITER');
  $('#f').onsubmit = (e) => { e.preventDefault(); run(async () => {
    startSession(await api('/auth/register', { method: 'POST', body: Object.fromEntries(new FormData(e.target)) }));
  }); };
}

function startSession(s) {
  session = s; localStorage.setItem('session', JSON.stringify(s));
  toast('Welcome, ' + s.fullName);
  go({ ADMIN: 'stats', RECRUITER: 'myJobs', JOB_SEEKER: 'jobs' }[s.role]);
}

/* ---------- jobs (public) ---------- */
const jobFilters = { keyword: '', location: '', jobType: '', sortBy: 'postedAt', dir: 'desc', page: 0 };

async function viewJobs() {
  app.innerHTML = `<h1>Find your next job</h1>
    <form id="f" class="card"><div class="grid">
      <div><label>Keyword / skill</label><input name="keyword" value="${esc(jobFilters.keyword)}"></div>
      <div><label>Location</label><input name="location" value="${esc(jobFilters.location)}"></div>
      <div><label>Type</label><select name="jobType"><option value="">Any</option>
        ${['FULL_TIME', 'PART_TIME', 'CONTRACT', 'INTERNSHIP', 'REMOTE'].map(t => `<option ${jobFilters.jobType === t ? 'selected' : ''}>${t}</option>`).join('')}</select></div>
      <div><label>Sort by</label><select name="sortBy">
        <option value="postedAt" ${jobFilters.sortBy === 'postedAt' ? 'selected' : ''}>Newest</option>
        <option value="salaryMax" ${jobFilters.sortBy === 'salaryMax' ? 'selected' : ''}>Salary</option>
        <option value="title" ${jobFilters.sortBy === 'title' ? 'selected' : ''}>Title</option></select></div>
    </div><div class="row" style="margin-top:12px"><button>Search</button></div></form><div id="list"></div>`;
  $('#f').onsubmit = (e) => { e.preventDefault(); Object.assign(jobFilters, Object.fromEntries(new FormData(e.target)), { page: 0 }); loadJobs(); };
  await loadJobs();
}

async function loadJobs() {
  const q = new URLSearchParams({ size: 6, page: jobFilters.page, sortBy: jobFilters.sortBy, dir: jobFilters.sortBy === 'title' ? 'asc' : 'desc' });
  ['keyword', 'location', 'jobType'].forEach(k => jobFilters[k] && q.set(k, jobFilters[k]));
  const p = await api('/jobs?' + q);
  $('#list').innerHTML = (p.content.map(j => jobCard(j, { apply: session?.role === 'JOB_SEEKER' })).join('')
    || '<p class="muted">No jobs match your search.</p>') + pager(p, (n) => { jobFilters.page = n; run(loadJobs); });
  bindApply();
}

function jobCard(j, { apply = false, extra = '' } = {}) {
  return `<div class="card"><div class="row between"><div><h2>${esc(j.title)}</h2>
    <div class="muted">${esc(j.companyName)} · ${esc(j.location)} · ${esc(j.jobType.replace('_', ' '))}${j.experienceRequired != null ? ' · ' + j.experienceRequired + '+ yrs' : ''}</div></div>
    <div>${j.matchScore != null ? `<span class="tag">${j.matchScore}% match</span>` : ''}${j.active ? '' : '<span class="tag off">Closed</span>'}</div></div>
    <p>${esc(j.description.length > 220 ? j.description.slice(0, 220) + '…' : j.description)}</p>
    <div>${tags(j.skillsRequired)}</div>
    <div class="row between" style="margin-top:10px"><span class="muted">${salary(j)} · Posted ${fmtDate(j.postedAt)}</span>
    <div class="row">${extra}${apply ? `<button class="small" data-apply="${j.id}">Apply</button>` : ''}</div></div>
    ${apply ? `<div id="ap${j.id}" class="hidden" style="margin-top:10px"><textarea placeholder="Cover letter (optional)"></textarea>
      <button class="small" data-send="${j.id}" style="margin-top:6px">Submit application</button></div>` : ''}</div>`;
}

function bindApply() {
  document.querySelectorAll('[data-apply]').forEach(b => b.onclick = () => $('#ap' + b.dataset.apply).classList.toggle('hidden'));
  document.querySelectorAll('[data-send]').forEach(b => b.onclick = () => run(async () => {
    const id = b.dataset.send;
    await api('/applications/jobs/' + id, { method: 'POST', body: { coverLetter: $('#ap' + id + ' textarea').value } });
    $('#ap' + id).classList.add('hidden'); toast('Application submitted!');
  }));
}

/* ---------- job seeker ---------- */
async function viewRecommended() {
  const list = await api('/jobs/recommendations');
  app.innerHTML = `<h1>Recommended for you</h1><p class="muted">Matched by comparing your profile skills with each job's required skills.</p>`
    + (list.map(j => jobCard(j, { apply: true })).join('') || '<p class="card muted">No matches yet. Add skills to your profile (e.g. "java, spring boot, mysql").</p>');
  bindApply();
}

async function viewMyApps(page = 0) {
  const p = await api('/applications/my?page=' + page);
  app.innerHTML = `<h1>My applications</h1>` + (p.content.map(a => `<div class="card row between">
    <div><h2>${esc(a.jobTitle)}</h2><div class="muted">${esc(a.companyName)} · applied ${fmtDate(a.appliedAt)}</div></div>
    <span class="tag ${a.status}">${a.status}</span></div>`).join('') || '<p class="card muted">You have not applied to any jobs yet.</p>')
    + pager(p, (n) => run(() => viewMyApps(n)));
}

async function viewProfile() {
  const p = await api('/profile');
  app.innerHTML = `<h1>My profile</h1><div class="card"><form id="f"><p class="muted">${esc(p.fullName)} · ${esc(p.email)}</p><div class="grid two">
    <div class="field"><label>Headline</label><input name="headline" value="${esc(p.headline)}"></div>
    <div class="field"><label>Phone</label><input name="phone" value="${esc(p.phone)}"></div>
    <div class="field"><label>Location</label><input name="location" value="${esc(p.location)}"></div>
    <div class="field"><label>Education</label><input name="education" value="${esc(p.education)}"></div>
    <div class="field"><label>Experience (years)</label><input name="experienceYears" type="number" min="0" value="${esc(p.experienceYears)}"></div>
    <div class="field"><label>Skills (comma separated)</label><input name="skills" value="${esc(p.skills)}"></div></div>
    <div class="field"><label>Summary</label><textarea name="summary">${esc(p.summary)}</textarea></div><button>Save profile</button></form></div>
    <div class="card"><h3>Resume</h3><p class="muted">${p.resumeFileName ? 'Uploaded: ' + esc(p.resumeFileName) : 'No resume uploaded yet'} (PDF, DOC or DOCX, max 5MB)</p>
    <div class="row"><input type="file" id="file" accept=".pdf,.doc,.docx" style="max-width:320px"><button id="up" class="secondary">Upload</button></div></div>`;
  $('#f').onsubmit = (e) => { e.preventDefault(); run(async () => {
    const d = Object.fromEntries(new FormData(e.target));
    d.experienceYears = d.experienceYears === '' ? null : Number(d.experienceYears);
    await api('/profile', { method: 'PUT', body: d }); toast('Profile saved');
  }); };
  $('#up').onclick = () => run(async () => {
    const f = $('#file').files[0]; if (!f) throw new Error('Choose a file first');
    const fd = new FormData(); fd.append('file', f);
    await api('/profile/resume', { method: 'POST', form: fd }); toast('Resume uploaded'); go('profile');
  });
}

/* ---------- recruiter ---------- */
async function viewMyJobs(page = 0) {
  const p = await api('/jobs/my?page=' + page);
  app.innerHTML = `<h1>My job postings</h1>` + (p.content.map(j => jobCard(j, { extra:
    `<button class="small secondary" data-applicants="${j.id}">Applicants</button>
     <button class="small secondary" data-edit="${j.id}">Edit</button>
     <button class="small danger" data-del="${j.id}">Delete</button>` })).join('') || '<p class="card muted">No jobs posted yet.</p>')
    + pager(p, (n) => run(() => viewMyJobs(n)));
  const byId = Object.fromEntries(p.content.map(j => [j.id, j]));
  document.querySelectorAll('[data-applicants]').forEach(b => b.onclick = () => go('applicants', byId[b.dataset.applicants]));
  document.querySelectorAll('[data-edit]').forEach(b => b.onclick = () => go('postJob', byId[b.dataset.edit]));
  document.querySelectorAll('[data-del]').forEach(b => b.onclick = () => { if (confirm('Delete this job and its applications?')) run(async () => {
    await api('/jobs/' + b.dataset.del, { method: 'DELETE' }); toast('Job deleted'); go('myJobs'); }); });
}

function viewPostJob(job) {
  const j = job || {};
  app.innerHTML = `<h1>${j.id ? 'Edit job' : 'Post a new job'}</h1><div class="card"><form id="f">
    <div class="field"><label>Title</label><input name="title" value="${esc(j.title)}" required></div>
    <div class="field"><label>Description</label><textarea name="description" required>${esc(j.description)}</textarea></div>
    <div class="grid two"><div class="field"><label>Location</label><input name="location" value="${esc(j.location)}" required></div>
    <div class="field"><label>Type</label><select name="jobType">${['FULL_TIME', 'PART_TIME', 'CONTRACT', 'INTERNSHIP', 'REMOTE'].map(t => `<option ${j.jobType === t ? 'selected' : ''}>${t}</option>`).join('')}</select></div>
    <div class="field"><label>Required skills (comma separated)</label><input name="skillsRequired" value="${esc(j.skillsRequired)}"></div>
    <div class="field"><label>Min experience (years)</label><input name="experienceRequired" type="number" min="0" value="${esc(j.experienceRequired)}"></div>
    <div class="field"><label>Salary min</label><input name="salaryMin" type="number" min="0" value="${esc(j.salaryMin)}"></div>
    <div class="field"><label>Salary max</label><input name="salaryMax" type="number" min="0" value="${esc(j.salaryMax)}"></div></div>
    ${j.id ? `<div class="field"><label><input type="checkbox" name="active" ${j.active ? 'checked' : ''} style="width:auto"> Accepting applications</label></div>` : ''}
    <button>${j.id ? 'Save changes' : 'Publish job'}</button></form></div>`;
  $('#f').onsubmit = (e) => { e.preventDefault(); run(async () => {
    const d = Object.fromEntries(new FormData(e.target));
    ['experienceRequired', 'salaryMin', 'salaryMax'].forEach(k => d[k] = d[k] === '' ? null : Number(d[k]));
    if (j.id) d.active = e.target.active.checked;
    await api('/jobs' + (j.id ? '/' + j.id : ''), { method: j.id ? 'PUT' : 'POST', body: d });
    toast(j.id ? 'Job updated' : 'Job published'); go('myJobs');
  }); };
}

async function viewApplicants(job, page = 0) {
  const p = await api(`/jobs/${job.id}/applicants?page=${page}`);
  app.innerHTML = `<div class="row between"><h1>Applicants: ${esc(job.title)}</h1><button class="secondary" data-nav="myJobs">Back</button></div>`
    + (p.content.length ? `<div class="card"><table><tr><th>Candidate</th><th>Applied</th><th>Status</th><th>Actions</th></tr>${p.content.map(a => `<tr>
      <td><b>${esc(a.applicantName)}</b><br><span class="muted">${esc(a.applicantEmail)}</span>${a.coverLetter ? `<br><span class="muted">${esc(a.coverLetter)}</span>` : ''}</td>
      <td>${fmtDate(a.appliedAt)}</td><td><span class="tag ${a.status}">${a.status}</span></td>
      <td class="row"><button class="small ok" data-st="SHORTLISTED" data-id="${a.id}">Shortlist</button>
      <button class="small danger" data-st="REJECTED" data-id="${a.id}">Reject</button>
      <button class="small secondary" data-resume="${a.id}">Resume</button></td></tr>`).join('')}</table></div>`
      : '<p class="card muted">No applicants yet.</p>') + pager(p, (n) => run(() => viewApplicants(job, n)));
  document.querySelectorAll('[data-st]').forEach(b => b.onclick = () => run(async () => {
    await api(`/applications/${b.dataset.id}/status`, { method: 'PATCH', body: { status: b.dataset.st } });
    toast('Candidate ' + b.dataset.st.toLowerCase()); viewApplicants(job, page);
  }));
  document.querySelectorAll('[data-resume]').forEach(b => b.onclick = () => run(() => downloadResume(b.dataset.resume)));
}

async function downloadResume(appId) {
  const res = await fetch(`/api/applications/${appId}/resume`, { headers: { Authorization: 'Bearer ' + session.token } });
  if (!res.ok) throw new Error((await res.json().catch(() => ({}))).message || 'Could not download resume');
  const name = (res.headers.get('Content-Disposition') || '').match(/filename="(.+)"/)?.[1] || 'resume';
  const url = URL.createObjectURL(await res.blob());
  const a = document.createElement('a'); a.href = url; a.download = name; a.click(); URL.revokeObjectURL(url);
}

/* ---------- admin ---------- */
async function viewStats() {
  const s = await api('/admin/stats');
  const box = (n, l) => `<div class="card stat"><b>${n}</b>${l}</div>`;
  app.innerHTML = `<h1>Admin dashboard</h1><div class="grid">${box(s.jobSeekers, 'Job seekers')}${box(s.recruiters, 'Recruiters')}
    ${box(s.totalJobs, 'Total jobs')}${box(s.activeJobs, 'Active jobs')}${box(s.totalApplications, 'Applications')}
    ${box(s.applied, 'Pending review')}${box(s.shortlisted, 'Shortlisted')}${box(s.rejected, 'Rejected')}</div>`;
}

async function viewAdminUsers(page = 0) {
  const p = await api('/admin/users?page=' + page);
  app.innerHTML = `<h1>Users</h1><div class="card"><table><tr><th>Name</th><th>Role</th><th>Status</th><th></th></tr>${p.content.map(u => `<tr>
    <td><b>${esc(u.fullName)}</b><br><span class="muted">${esc(u.email)}${u.companyName ? ' · ' + esc(u.companyName) : ''}</span></td>
    <td>${u.role}</td><td><span class="tag ${u.enabled ? '' : 'off'}">${u.enabled ? 'Active' : 'Disabled'}</span></td>
    <td class="row">${u.role === 'ADMIN' ? '' : `<button class="small secondary" data-tg="${u.id}" data-v="${!u.enabled}">${u.enabled ? 'Disable' : 'Enable'}</button>
    <button class="small danger" data-rm="${u.id}">Delete</button>`}</td></tr>`).join('')}</table></div>` + pager(p, (n) => run(() => viewAdminUsers(n)));
  document.querySelectorAll('[data-tg]').forEach(b => b.onclick = () => run(async () => {
    await api(`/admin/users/${b.dataset.tg}/enabled?value=${b.dataset.v}`, { method: 'PATCH' }); viewAdminUsers(page); }));
  document.querySelectorAll('[data-rm]').forEach(b => b.onclick = () => { if (confirm('Delete this user and all their data?')) run(async () => {
    await api('/admin/users/' + b.dataset.rm, { method: 'DELETE' }); toast('User deleted'); viewAdminUsers(page); }); });
}

async function viewAdminJobs(page = 0) {
  const p = await api('/admin/jobs?page=' + page);
  app.innerHTML = `<h1>All job postings</h1>` + p.content.map(j => jobCard(j, { extra: `<button class="small danger" data-del="${j.id}">Remove</button>` })).join('')
    + pager(p, (n) => run(() => viewAdminJobs(n)));
  document.querySelectorAll('[data-del]').forEach(b => b.onclick = () => { if (confirm('Remove this job?')) run(async () => {
    await api('/admin/jobs/' + b.dataset.del, { method: 'DELETE' }); toast('Job removed'); viewAdminJobs(page); }); });
}

async function viewAdminApps(page = 0) {
  const p = await api('/admin/applications?page=' + page);
  app.innerHTML = `<h1>All applications</h1><div class="card"><table><tr><th>Candidate</th><th>Job</th><th>Applied</th><th>Status</th><th></th></tr>${p.content.map(a => `<tr>
    <td>${esc(a.applicantName)}<br><span class="muted">${esc(a.applicantEmail)}</span></td><td>${esc(a.jobTitle)}<br><span class="muted">${esc(a.companyName)}</span></td>
    <td>${fmtDate(a.appliedAt)}</td><td><span class="tag ${a.status}">${a.status}</span></td>
    <td><button class="small secondary" data-resume="${a.id}">Resume</button></td></tr>`).join('')}</table></div>` + pager(p, (n) => run(() => viewAdminApps(n)));
  document.querySelectorAll('[data-resume]').forEach(b => b.onclick = () => run(() => downloadResume(b.dataset.resume)));
}

/* ---------- start ---------- */
go(session?.role === 'ADMIN' ? 'stats' : session?.role === 'RECRUITER' ? 'myJobs' : 'jobs');
